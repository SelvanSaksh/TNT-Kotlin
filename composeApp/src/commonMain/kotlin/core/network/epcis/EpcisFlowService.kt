package core.network.epcis

import core.network.models.AggregateSerialRequest
import core.network.models.CommissionSerialRequest
import core.network.models.DispenseSerialRequest
import core.network.models.EpcisEventEpc
import core.network.models.EpcisFlowEventRequest
import core.network.models.EpcisL2PackRequest
import core.network.models.EpcisL3AggregateItem
import core.network.models.EpcisL3AggregateRequest
import core.network.models.GtinLookupResponse
import core.network.models.L3ShipmentProduct
import core.network.models.L3ShipmentRequest
import core.network.models.L3ByGtinResponse
import core.network.models.L4ReceiveRequest
import core.network.models.L4VerifyRequest
import core.network.models.L4VerifyResponse
import core.network.models.RecallSerialRequest
import core.network.repository.SerializationException
import core.network.repository.SerializationRepository
import core.network.wms.WmsCreatedPackingBox
import core.network.wms.WmsPackingBoxItem
import core.network.wms.WmsPackingBoxSummary
import core.network.wms.WmsPackingReceiverNode
import core.storage.SessionManager
import core.util.AuditLogHelper
import core.util.extractGs1AiValue
import core.util.extractGs1Batch
import core.util.extractGs1Gtin
import core.util.extractGs1Serial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import utils.EpcUriBuilder
import utils.Gs1Parser

/**
 * Orchestrates EPCIS write flows aligned with supply-chain levels:
 *
 * | Trigger              | Level | API                    |
 * |----------------------|-------|------------------------|
 * | Unit commissioned    | L1    | commission             |
 * | Units → case         | L2    | aggregate (L2)         |
 * | Cases → pallet       | L3    | aggregate (L3)         |
 * | Case/pallet broken   | L3    | epcis UNPACK           |
 * | Product repackaged   | L2/L4 | epcis REPACK           |
 * | Goods shipped        | L3/L4 | epcis SHIP             |
 * | Goods received       | L3/L4 | epcis RECEIVE          |
 * | Batch recalled       | L4    | recall                 |
 * | Unit dispensed       | L5    | dispense               |
 */
object EpcisFlowService {

    private val repo = SerializationRepository()
    private val json = Json { ignoreUnknownKeys = true }

    data class Context(
        val companyId: Int,
        val framework: String = "GENERIC",
        val marketCode: String = "IN",
        val sourceGln: String? = null,
    )

    fun contextFromSession(session: SessionManager, framework: String = "GENERIC"): Context {
        val companyId = AuditLogHelper.resolveCompanyId(session)
        return Context(
            companyId = companyId,
            framework = framework,
            marketCode = "IN",
            sourceGln = resolveSourceGln(session),
        )
    }

    /**
     * L1 — commission whenever a GS1 barcode is generated with GTIN.
     * Serial AI (21) is used when present; otherwise batch, expiry, or GTIN is used
     * as the commission key so GTIN-only generation is still stored in EPCIS.
     * Skips when GTIN is absent (non-GS1 barcodes).
     */
    suspend fun commissionFromGs1Payload(
        session: SessionManager,
        gs1Payload: String,
        framework: String = "GENERIC",
        productionOrder: String? = null,
        explicitSerial: String? = null,
    ): Result<Unit> {
        val gtin = extractGs1Gtin(gs1Payload).filter { it.isDigit() }
        if (gtin.isBlank()) return Result.success(Unit)

        val serialNumber = resolveCommissionSerial(gtin, gs1Payload, explicitSerial)
        return commissionUnit(
            context = contextFromSession(session, framework),
            gtin = gtin,
            serialNumber = serialNumber,
            batchNumber = extractGs1Batch(gs1Payload).takeIf { it.isNotBlank() },
            productionOrder = productionOrder,
        )
    }

    /**
     * Derives the commission serial key for [serials/commission].
     * Priority: explicit / AI 21 → batch (AI 10) → expiry (AI 17) → GTIN digits.
     */
    internal fun resolveCommissionSerial(
        gtin: String,
        gs1Payload: String,
        explicitSerial: String? = null,
    ): String {
        explicitSerial?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        extractGs1Serial(gs1Payload).trim().takeIf { it.isNotBlank() }?.let { return it }
        extractGs1Batch(gs1Payload).trim().takeIf { it.isNotBlank() }?.let { return it }
        extractGs1AiValue(gs1Payload, "17").trim().takeIf { it.isNotBlank() }?.let { return "EXP$it" }
        return gtin.filter { it.isDigit() }.ifBlank { gtin.trim() }
    }

    /** L1 — commission a range of serials (serialized barcode batch generation). */
    suspend fun commissionSerialRange(
        session: SessionManager,
        gtin: String,
        batchNumber: String?,
        serialPrefix: String,
        serialStart: Int,
        count: Int,
        framework: String = "GENERIC",
        productionOrder: String? = null,
    ): Result<Unit> {
        val normalizedGtin = gtin.filter { it.isDigit() }
        if (normalizedGtin.isBlank() || count <= 0) return Result.success(Unit)

        val ctx = contextFromSession(session, framework)
        return runCatching {
            repeat(count) { index ->
                val serial = buildSerialNumber(serialPrefix, serialStart + index)
                commissionUnit(
                    context = ctx,
                    gtin = normalizedGtin,
                    serialNumber = serial,
                    batchNumber = batchNumber,
                    productionOrder = productionOrder,
                ).getOrThrow()
            }
        }
    }

    suspend fun commissionUnit(
        context: Context,
        gtin: String,
        serialNumber: String,
        batchNumber: String? = null,
        productionOrder: String? = null,
    ): Result<Unit> {
        val normalizedGtin = gtin.filter { it.isDigit() }
        val serial = serialNumber.trim()
        println("=== L1 COMMISSION REQUEST ===")
        println("URL: POST /serialization/serials/commission")
        println("companyId: ${context.companyId}")
        println("gtin: $normalizedGtin")
        println("serialNumber: $serial")
        println("batchNumber: $batchNumber")
        println("framework: ${context.framework}")
        println("marketCode: ${context.marketCode}")
        println("productionOrder: $productionOrder")
        println("==============================")
        return runCatching {
            require(context.companyId > 0) { "Company is not configured." }
            repo.commissionSerial(
                CommissionSerialRequest(
                    companyId = context.companyId,
                    gtin = normalizedGtin,
                    serialNumber = serial,
                    batchNumber = batchNumber?.trim()?.takeIf { it.isNotBlank() },
                    framework = context.framework,
                    marketCode = context.marketCode,
                    productionOrder = productionOrder,
                ),
            )
            println("=== L1 COMMISSION RESPONSE === success: true")
            println("===============================")
            Unit
        }.onFailure {
            println("=== L1 COMMISSION RESPONSE === error: ${it.message}")
            println("===============================")
            logFailure("commission", it)
        }
    }

    /**
     * GTIN verification — check if product GTIN is registered in product master.
     * Call this when packer selects/scans a line item during packing.
     * GET /productmaster/lookup/gtin?companyId={companyId}&gtin={gtin}
     */
    suspend fun verifyGtin(
        session: SessionManager,
        gtin: String,
    ): Result<GtinLookupResponse> {
        val companyId = AuditLogHelper.resolveCompanyId(session)
        val normalizedGtin = gtin.filter { it.isDigit() }
        if (companyId <= 0 || normalizedGtin.isBlank()) {
            return Result.success(
                GtinLookupResponse(
                    exists = false,
                    gtin = normalizedGtin,
                    message = "GTIN or company is missing.",
                )
            )
        }
        println("=== GTIN VERIFY REQUEST ===")
        println("URL: GET /productmaster/lookup/gtin?companyId=$companyId&gtin=$normalizedGtin")
        println("===========================")
        return runCatching {
            val result = repo.lookupGtin(companyId, normalizedGtin)
            println("=== GTIN VERIFY RESPONSE ===")
            println("exists: ${result.isRegistered}, gtin: ${result.gtin}, productName: ${result.productName}")
            println("============================")
            result
        }.onFailure {
            println("=== GTIN VERIFY RESPONSE === error: ${it.message}")
            println("============================")
            logFailure("gtin-verify", it)
        }
    }

    /**
     * L2 — Pack L1 items into carton (AggregationEvent ADD packing with GTIN + serials).
     * Called when user taps Add to Box during packing.
     * POST /serialization/serials/aggregate
     * Body: { companyId, parentEpcUri, gtin, serials, containerSealed, bizLocationGln }
     */
    suspend fun aggregateL2Pack(
        session: SessionManager,
        parentSscc: String,
        gtin: String,
        serials: List<String>,
        containerSealed: Boolean = false,
        framework: String = "GENERIC",
    ): Result<Unit> {
        val ctx = contextFromSession(session, framework)
        val normalizedGtin = gtin.filter { it.isDigit() }
        val cleanSerials = serials.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedGtin.isBlank()) {
            println("=== L2 AGGREGATE SKIPPED — GTIN is blank after normalization (raw: '$gtin') ===")
            return Result.success(Unit)
        }
        return runCatching {
            require(ctx.companyId > 0) { "Company is not configured." }
            val parentUri = EpcUriBuilder.buildSsccEpcUri(parentSscc)
            val bizGln = ctx.sourceGln ?: glnFromCompanyId(ctx.companyId)
            val request = EpcisL2PackRequest(
                companyId = ctx.companyId,
                parentEpcUri = parentUri,
                gtin = normalizedGtin,
                serials = cleanSerials,
                containerSealed = containerSealed,
                bizLocationGln = bizGln,
            )
            println("=== L2 AGGREGATE REQUEST ===")
            println("URL: POST /serialization/serials/aggregate")
            println("companyId: ${ctx.companyId}")
            println("parentEpcUri: $parentUri")
            println("gtin: $normalizedGtin")
            println("serials: $cleanSerials")
            println("containerSealed: $containerSealed")
            println("bizLocationGln: $bizGln")
            println("============================")
            repo.aggregateL2Pack(request)
            println("=== L2 AGGREGATE RESPONSE === success: true")
            println("=============================")
            Unit
        }.onFailure {
            println("=== L2 AGGREGATE RESPONSE === error: ${it.message}")
            println("=============================")
            logFailure("aggregate-L2-pack", it)
        }
    }

    /** L2 — units packed into case (AggregationEvent ADD packing). */
    suspend fun aggregateUnitsIntoCase(
        session: SessionManager,
        parentSscc: String,
        childEpcUris: List<String>,
        containerSealed: Boolean = true,
        framework: String = "GENERIC",
    ): Result<Unit> = aggregatePack(
        context = contextFromSession(session, framework),
        parentEpcUri = EpcUriBuilder.buildSsccEpcUri(parentSscc),
        childEpcUris = childEpcUris,
        levelOrigin = "L2",
        containerSealed = containerSealed,
    )

    /** L3 — cases packed onto pallet (AggregationEvent ADD packing). */
    suspend fun aggregateCasesOntoPallet(
        session: SessionManager,
        parentSscc: String,
        childSsccs: List<String>,
        containerSealed: Boolean = true,
        framework: String = "GENERIC",
    ): Result<Unit> = aggregatePack(
        context = contextFromSession(session, framework),
        parentEpcUri = EpcUriBuilder.buildSsccEpcUri(parentSscc),
        childEpcUris = childSsccs.map { EpcUriBuilder.buildSsccEpcUri(it) },
        levelOrigin = "L3",
        containerSealed = containerSealed,
    )

    suspend fun aggregatePack(
        context: Context,
        parentEpcUri: String,
        childEpcUris: List<String>,
        levelOrigin: String,
        containerSealed: Boolean = true,
    ): Result<Unit> {
        val children = childEpcUris.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (children.isEmpty()) return Result.success(Unit)
        return runCatching {
            require(context.companyId > 0) { "Company is not configured." }
            repo.aggregateSerial(
                AggregateSerialRequest(
                    companyId = context.companyId,
                    parentEpcUri = parentEpcUri,
                    childEpcUris = children,
                    levelOrigin = levelOrigin,
                    framework = context.framework,
                    containerSealed = containerSealed,
                ),
            )
            Unit
        }.onFailure { logFailure("aggregate-$levelOrigin", it) }
    }

    /**
     * L3 — Aggregate cartons onto pallet with item-level detail (GTIN+serial+batch).
     * POST /serialization/serials/aggregate
     * Body: { companyId, parentEpcUri, items, levelOrigin, containerSealed, framework, bizLocationGln, rejectUnknownProduct }
     */
    suspend fun aggregateL3WithItems(
        session: SessionManager,
        parentSscc: String,
        items: List<EpcisL3AggregateItem>,
        containerSealed: Boolean = true,
        framework: String = "GENERIC",
    ): Result<Unit> {
        val ctx = contextFromSession(session, framework)
        val cleanItems = items.filter { it.gtin.isNotBlank() && it.serial.isNotBlank() }
        if (cleanItems.isEmpty()) {
            println("[EPCIS] L3 AGGREGATE skipped — no items with GTIN+serial")
            return Result.success(Unit)
        }
        return runCatching {
            require(ctx.companyId > 0) { "Company is not configured." }
            val parentUri = EpcUriBuilder.buildSsccEpcUri(parentSscc)
            val bizGln = ctx.sourceGln ?: glnFromCompanyId(ctx.companyId)
            val request = EpcisL3AggregateRequest(
                companyId = ctx.companyId,
                parentEpcUri = parentUri,
                items = cleanItems,
                levelOrigin = "L3",
                containerSealed = containerSealed,
                framework = framework,
                bizLocationGln = bizGln,
                rejectUnknownProduct = false,
            )
            println("=== L3 AGGREGATE REQUEST ===")
            println("URL: POST /serialization/serials/aggregate")
            println("companyId: ${ctx.companyId}")
            println("parentEpcUri: $parentUri")
            println("levelOrigin: L3")
            println("containerSealed: $containerSealed")
            println("framework: $framework")
            println("bizLocationGln: $bizGln")
            println("items: ${cleanItems.size}")
            cleanItems.forEachIndexed { index, item ->
                println("  item[$index]: gtin=${item.gtin}, serial=${item.serial}, batch=${item.batch}")
            }
            println("============================")
            repo.aggregateL3(request)
            println("=== L3 AGGREGATE RESPONSE === success: true")
            println("=============================")
            Unit
        }.onFailure {
            println("=== L3 AGGREGATE RESPONSE === error: ${it.message}")
            println("=============================")
            logFailure("aggregate-L3-items", it)
        }
    }

    /**
     * L3 — Ship dispatched pick list (dispatch shipment).
     * POST /serialization/shipments/l3
     * Body: { companyId, sourceGln, destinationGln, bizTransactionId, products, bizLocationGln }
     */
    suspend fun shipL3(
        session: SessionManager,
        sourceGln: String,
        destinationGln: String,
        bizTransactionId: String,
        products: List<L3ShipmentProduct>,
        framework: String = "GENERIC",
        destinationGlnOverride: String? = null,
    ): Result<Unit> {
        val ctx = contextFromSession(session, framework)
        val cleanProducts = products.filter { it.gtin.isNotBlank() }
        val finalDestGln = destinationGlnOverride?.trim()?.takeIf { it.isNotBlank() } ?: destinationGln
        return runCatching {
            require(ctx.companyId > 0) { "Company is not configured." }
            val bizGln = ctx.sourceGln ?: glnFromCompanyId(ctx.companyId)
            val request = L3ShipmentRequest(
                companyId = ctx.companyId,
                sourceGln = sourceGln,
                destinationGln = finalDestGln,
                bizTransactionId = bizTransactionId,
                products = cleanProducts,
                bizLocationGln = bizGln,
                framework = ctx.framework.ifBlank { null },
            )
            println("=== L3 SHIPMENT REQUEST ===")
            println("URL: POST /serialization/shipments/l3")
            println("companyId: ${ctx.companyId}")
            println("sourceGln: $sourceGln")
            println("destinationGln: $finalDestGln (override: $destinationGlnOverride)")
            println("bizTransactionId: $bizTransactionId")
            println("bizLocationGln: $bizGln")
            println("products: ${cleanProducts.size}")
            cleanProducts.forEachIndexed { index, product ->
                println("  product[$index]: gtin=${product.gtin}, serials=${product.serials}, batch=${product.batch}")
            }
            println("===========================")
            repo.shipL3(request)
            println("=== L3 SHIPMENT RESPONSE === success: true")
            println("============================")
            Unit
        }.onFailure {
            println("=== L3 SHIPMENT RESPONSE === error: ${it.message}")
            println("============================")
            logFailure("ship-L3", it)
        }
    }

    /**
     * L4 — Verify receipt before receiving.
     * POST /serialization/receipts/l4/verify
     * Body: { companyId, bizTransactionId, gtin, serial }
     */
    suspend fun verifyL4Receipt(
        session: SessionManager,
        bizTransactionId: String,
        gtin: String,
        serial: String,
    ): Result<L4VerifyResponse> {
        val ctx = contextFromSession(session)
        val normalizedGtin = gtin.filter { it.isDigit() }
        if (normalizedGtin.isBlank() || serial.isBlank()) {
            return Result.success(L4VerifyResponse(message = "GTIN or serial is missing."))
        }
        println("=== L4 VERIFY REQUEST ===")
        println("URL: POST /serialization/receipts/l4/verify")
        println("companyId: ${ctx.companyId}")
        println("bizTransactionId: $bizTransactionId")
        println("gtin: $normalizedGtin")
        println("serial: ${serial.trim()}")
        println("=========================")
        return runCatching {
            require(ctx.companyId > 0) { "Company is not configured." }
            val result = repo.verifyL4(
                L4VerifyRequest(
                    companyId = ctx.companyId,
                    bizTransactionId = bizTransactionId,
                    gtin = normalizedGtin,
                    serial = serial.trim(),
                ),
            )
            println("=== L4 VERIFY RESPONSE ===")
            println("level: ${result.level}, readyForL4Receive: ${result.readyForL4Receive}, message: ${result.message}")
            println("==========================")
            result
        }.onFailure {
            println("=== L4 VERIFY RESPONSE === error: ${it.message}")
            println("==========================")
            logFailure("verify-L4", it)
        }
    }

    /**
     * L4 — Receive item after verification.
     * POST /serialization/receipts/l4
     * Body: { companyId, gtin, userId, bizTransactionId, sourceGln, destinationGln, serial, batch, scannerId, receivingConfirmed }
     */
    suspend fun receiveL4(
        session: SessionManager,
        userId: Int,
        gtin: String,
        bizTransactionId: String? = null,
        sourceGln: String? = null,
        destinationGln: String? = null,
        serial: String? = null,
        batch: String? = null,
        scannerId: String? = null,
        receivingConfirmed: Boolean = true,
        framework: String = "GENERIC",
        bizLocationGln: String? = null,
        verifyAgainstShipment: Boolean? = null,
    ): Result<Unit> {
        val ctx = contextFromSession(session, framework)
        val normalizedGtin = gtin.filter { it.isDigit() }
        if (normalizedGtin.isBlank()) return Result.success(Unit)
        val bizGln = bizLocationGln ?: ctx.sourceGln ?: glnFromCompanyId(ctx.companyId)
        println("=== L4 RECEIVE REQUEST ===")
        println("URL: POST /serialization/receipts/l4")
        println("companyId: ${ctx.companyId}")
        println("gtin: $normalizedGtin")
        println("userId: $userId")
        println("bizTransactionId: $bizTransactionId")
        println("sourceGln: $sourceGln")
        println("destinationGln: $destinationGln")
        println("serial: $serial")
        println("batch: $batch")
        println("scannerId: $scannerId")
        println("receivingConfirmed: $receivingConfirmed")
        println("framework: ${ctx.framework}")
        println("bizLocationGln: $bizGln")
        println("==========================")
        return runCatching {
            require(ctx.companyId > 0) { "Company is not configured." }
            repo.receiveL4(
                L4ReceiveRequest(
                    companyId = ctx.companyId,
                    gtin = normalizedGtin,
                    userId = userId,
                    bizTransactionId = bizTransactionId,
                    sourceGln = sourceGln,
                    destinationGln = destinationGln,
                    serial = serial,
                    batch = batch,
                    scannerId = scannerId,
                    receivingConfirmed = receivingConfirmed,
                    framework = ctx.framework.ifBlank { null },
                    bizLocationGln = bizGln,
                    verifyAgainstShipment = verifyAgainstShipment,
                ),
            )
            println("=== L4 RECEIVE RESPONSE === success: true")
            println("===========================")
            Unit
        }.onFailure {
            println("=== L4 RECEIVE RESPONSE === error: ${it.message}")
            println("===========================")
            logFailure("receive-L4", it)
        }
    }

    /** Fetch L3 shipment details by GTIN to get correct sourceGln/destinationGln/bizTransactionId. */
    suspend fun fetchL3ByGtin(
        session: SessionManager,
        gtin: String,
    ): Result<L3ByGtinResponse> {
        val ctx = contextFromSession(session)
        val normalizedGtin = gtin.filter { it.isDigit() }
        if (normalizedGtin.isBlank()) return Result.success(L3ByGtinResponse())
        println("=== L3 BY GTIN REQUEST === gtin=$normalizedGtin companyId=${ctx.companyId}")
        return runCatching {
            require(ctx.companyId > 0) { "Company is not configured." }
            val result = repo.fetchL3ByGtin(ctx.companyId, normalizedGtin)
            println("=== L3 BY GTIN RESPONSE === count=${result.count} shipments=${result.shipments?.size ?: 0}")
            result
        }.onFailure {
            println("=== L3 BY GTIN RESPONSE === error: ${it.message}")
            logFailure("fetch-l3-by-gtin", it)
        }
    }

    /** L3 — case/pallet broken down (AggregationEvent DELETE unpacking). */
    suspend fun unpackContainer(
        session: SessionManager,
        parentEpcUri: String,
        childEpcUris: List<String>,
        framework: String = "GENERIC",
    ): Result<Unit> = postFlowEvent(
        session = session,
        flow = "UNPACK",
        epcUris = listOf(parentEpcUri) + childEpcUris,
        framework = framework,
    )

    /** L2/L4 — product repackaged (TransformationEvent repackaging). */
    suspend fun repackProduct(
        session: SessionManager,
        epcUris: List<String>,
        framework: String = "GENERIC",
    ): Result<Unit> = postFlowEvent(
        session = session,
        flow = "REPACK",
        epcUris = epcUris,
        framework = framework,
    )

    /** L3/L4 — goods shipped (TransactionEvent ADD shipping). */
    suspend fun shipGoods(
        session: SessionManager,
        epcUris: List<String>,
        destinationCompanyId: Int? = null,
        bizTransactionId: String? = null,
        bizTransactionType: String = "po",
        framework: String = "GENERIC",
    ): Result<Unit> {
        val ctx = contextFromSession(session, framework)
        val destinationGln = destinationCompanyId?.let { glnFromCompanyId(it) }
        return postFlowEvent(
            context = ctx,
            flow = "SHIP",
            epcUris = epcUris,
            destinationGln = destinationGln,
            bizTransactionId = bizTransactionId,
            bizTransactionType = bizTransactionType,
        )
    }

    /** L3/L4 — goods received (TransactionEvent ADD receiving). */
    suspend fun receiveGoods(
        session: SessionManager,
        epcUris: List<String>,
        sourceCompanyId: Int? = null,
        bizTransactionId: String? = null,
        receivingConfirmed: Boolean = true,
        framework: String = "GENERIC",
    ): Result<Unit> {
        val ctx = contextFromSession(session, framework)
        val sourceGln = sourceCompanyId?.let { glnFromCompanyId(it) } ?: ctx.sourceGln
        return postFlowEvent(
            context = ctx,
            flow = "RECEIVE",
            epcUris = epcUris,
            sourceGln = sourceGln,
            destinationGln = ctx.sourceGln,
            bizTransactionId = bizTransactionId,
            receivingConfirmed = receivingConfirmed,
        )
    }

    /** L5 — unit dispensed (ObjectEvent OBSERVE dispensing). */
    suspend fun dispenseUnit(
        session: SessionManager,
        epcUri: String,
        destinationGln: String? = null,
        framework: String = "CDSCO",
    ): Result<Unit> {
        val ctx = contextFromSession(session, framework)
        return runCatching {
            require(ctx.companyId > 0) { "Company is not configured." }
            repo.dispenseSerial(
                DispenseSerialRequest(
                    companyId = ctx.companyId,
                    epcUri = epcUri,
                    destinationGln = destinationGln ?: ctx.sourceGln ?: glnFromCompanyId(ctx.companyId),
                    framework = framework,
                    marketCode = ctx.marketCode,
                ),
            )
            Unit
        }.onFailure { logFailure("dispense", it) }
    }

    /** L4 — batch recalled (ObjectEvent OBSERVE holding recalled). */
    suspend fun recallBatch(
        session: SessionManager,
        gtin: String,
        batchNumber: String,
        recallReference: String,
        affectedQuantity: Int,
        framework: String = "CDSCO",
    ): Result<Unit> {
        val ctx = contextFromSession(session, framework)
        return runCatching {
            require(ctx.companyId > 0) { "Company is not configured." }
            repo.recallSerial(
                RecallSerialRequest(
                    companyId = ctx.companyId,
                    gtin = gtin.filter { it.isDigit() },
                    batchNumber = batchNumber.trim(),
                    recallReference = recallReference.trim(),
                    affectedQuantity = affectedQuantity,
                    framework = framework,
                    marketCode = ctx.marketCode,
                ),
            )
            Unit
        }.onFailure { logFailure("recall", it) }
    }

    /** Fire-and-forget helper — logs failures without blocking UI. */
    suspend fun runSafely(label: String, block: suspend () -> Result<Unit>) {
        block().onFailure { logFailure(label, it) }
    }

    // ── WMS box → EPC URI helpers ─────────────────────────────────────────────

    fun ssccFromBox(box: WmsCreatedPackingBox): String? =
        box.sscc?.trim()?.takeIf { it.isNotBlank() }
            ?: box.resolvedBarcodeData.filter { it.isDigit() }.takeIf { it.length >= 17 }
            ?: Gs1Parser.parse(box.resolvedBarcodeData).sscc

    fun ssccFromSummary(summary: WmsPackingBoxSummary): String? =
        summary.resolvedSscc.takeIf { it.isNotBlank() }
            ?: summary.barcodeData?.let { Gs1Parser.parse(it).sscc }

    fun childEpcUrisFromItems(items: List<WmsPackingBoxItem>, levelOrigin: String): List<String> =
        when (levelOrigin) {
            "L3" -> items.mapNotNull { item ->
                item.childPackSscc?.let { EpcUriBuilder.buildSsccEpcUri(it) }
                    ?: item.childBox?.sscc?.let { EpcUriBuilder.buildSsccEpcUri(it) }
            }.distinct()
            "L2" -> items.mapNotNull { item ->
                val childSscc = item.childPackSscc ?: item.childBox?.sscc
                childSscc?.let { EpcUriBuilder.buildSsccEpcUri(it) }
            }.distinct()
            else -> emptyList()
        }

    fun topLevelShipEpcUris(boxes: List<WmsPackingBoxSummary>): List<String> {
        val pallets = boxes.filter { it.isTertiaryPackage && it.isCompleted }
        val sources = if (pallets.isNotEmpty()) pallets else boxes.filter { it.isSecondaryPackage && it.isCompleted }
        return sources.mapNotNull { summary ->
            ssccFromSummary(summary)?.let { EpcUriBuilder.buildSsccEpcUri(it) }
        }.distinct()
    }

    fun epcUrisFromReceiverNode(root: WmsPackingReceiverNode): List<String> {
        val ssccs = mutableListOf<String>()
        fun walk(node: WmsPackingReceiverNode) {
            if (node.normalizedNodeType == "BOX" || node.isPalletBox || node.isCartonBox) {
                node.sscc?.trim()?.takeIf { it.isNotBlank() }?.let { ssccs += it }
            }
            node.childBoxes.forEach(::walk)
            node.children.orEmpty().forEach(::walk)
        }
        walk(root)
        val pallets = root.childBoxes.filter { it.isPalletBox }.mapNotNull { it.sscc?.trim()?.takeIf { s -> s.isNotBlank() } }
        val useSsccs = if (pallets.isNotEmpty()) pallets else ssccs.distinct()
        return useSsccs.map { EpcUriBuilder.buildSsccEpcUri(it) }.distinct()
    }

    suspend fun onCartonSealed(session: SessionManager, sealed: WmsCreatedPackingBox) {
        val sscc = ssccFromBox(sealed) ?: return
        val childUris = childEpcUrisFromItems(sealed.resolvedItems, "L2")
        if (childUris.isEmpty()) return
        aggregateUnitsIntoCase(session, sscc, childUris, containerSealed = true)
    }

    suspend fun onCartonLinkedToPallet(
        session: SessionManager,
        palletSscc: String,
        cartonSscc: String,
    ) {
        aggregateCasesOntoPallet(
            session = session,
            parentSscc = palletSscc,
            childSsccs = listOf(cartonSscc),
            containerSealed = false,
        )
    }

    suspend fun onPalletSealed(
        session: SessionManager,
        pallet: WmsCreatedPackingBox,
        linkedCartonSsccs: List<String>,
    ) {
        val palletSscc = ssccFromBox(pallet) ?: return
        if (linkedCartonSsccs.isEmpty()) return
        aggregateCasesOntoPallet(
            session = session,
            parentSscc = palletSscc,
            childSsccs = linkedCartonSsccs,
            containerSealed = true,
        )
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private suspend fun postFlowEvent(
        session: SessionManager,
        flow: String,
        epcUris: List<String>,
        framework: String = "GENERIC",
        sourceGln: String? = null,
        destinationGln: String? = null,
        bizTransactionId: String? = null,
        bizTransactionType: String? = null,
        receivingConfirmed: Boolean? = null,
    ): Result<Unit> = postFlowEvent(
        context = contextFromSession(session, framework),
        flow = flow,
        epcUris = epcUris,
        sourceGln = sourceGln,
        destinationGln = destinationGln,
        bizTransactionId = bizTransactionId,
        bizTransactionType = bizTransactionType,
        receivingConfirmed = receivingConfirmed,
    )

    private suspend fun postFlowEvent(
        context: Context,
        flow: String,
        epcUris: List<String>,
        sourceGln: String? = null,
        destinationGln: String? = null,
        bizTransactionId: String? = null,
        bizTransactionType: String? = null,
        receivingConfirmed: Boolean? = null,
    ): Result<Unit> {
        val epcs = epcUris.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (epcs.isEmpty()) return Result.success(Unit)
        return runCatching {
            require(context.companyId > 0) { "Company is not configured." }
            repo.postEpcisEvent(
                EpcisFlowEventRequest(
                    companyId = context.companyId,
                    flow = flow,
                    framework = context.framework,
                    epcs = epcs.map { EpcisEventEpc(epcUri = it) },
                    sourceGln = sourceGln ?: context.sourceGln,
                    destinationGln = destinationGln,
                    bizTransactionId = bizTransactionId,
                    bizTransactionType = bizTransactionType,
                    receivingConfirmed = receivingConfirmed,
                ),
            )
            Unit
        }.onFailure { logFailure(flow.lowercase(), it) }
    }

    private fun buildSerialNumber(prefix: String, number: Int): String =
        "${prefix.trim()}${number.toString().padStart(6, '0')}"

    private fun glnFromCompanyId(companyId: Int): String =
        companyId.toString().padStart(13, '0')

    private fun resolveSourceGln(session: SessionManager): String? {
        val raw = session.getLocationDetails()?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
                ?.get("location")?.jsonObject
                ?.get("locn_gln")?.jsonPrimitive?.contentOrNull
                ?.filter { it.isDigit() }
                ?.takeIf { it.length >= 13 }
                ?: json.parseToJsonElement(raw).jsonArray.firstOrNull()?.jsonObject
                    ?.get("location")?.jsonObject
                    ?.get("locn_sgln")?.jsonPrimitive?.contentOrNull
                    ?.substringAfterLast(':')
                    ?.filter { it.isDigit() }
                    ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun logFailure(flow: String, error: Throwable) {
        val message = when (error) {
            is SerializationException -> error.message
            else -> error.message
        }
        println("⚠️ EPCIS $flow failed: ${message ?: error}")
    }
}
