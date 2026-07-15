package core.network.wms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ── Industry packing requests ─────────────────────────────────────────────────

@Serializable
data class WmsIndustryCreateBoxRequest(
    val packerId: Int,
    val boxType: String,
    val parentBoxId: Int? = null,
    val boxId: Int? = null,
)

@Serializable
data class WmsIndustrySetBoxParentRequest(
    val packerId: Int,
    val parentBoxId: Int,
)

@Serializable
data class WmsIndustrySealBoxRequest(
    val packerId: Int,
    val weightKg: Double = 0.0,
)

@Serializable
data class WmsIndustryCompleteListRequest(
    val packerId: Int,
)

@Serializable
data class WmsAddIndustryPackingBoxItemRequest(
    val quantity: Int,
    val packerId: Int,
    val verificationType: String? = null,
)

// ── Dispatch / receiving (shipped picklists) ──────────────────────────────────

@Serializable
data class WmsShippedPicklistTask(
    val id: Int? = null,
    @SerialName("picking_list_id") val pickingListId: Int? = null,
    @SerialName("source_order_ref") val sourceOrderRef: String? = null,
    val sku: String? = null,
    val gtin: String? = null,
    val batch: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val bin: String? = null,
    @SerialName("requested_qty") val requestedQty: Int? = null,
    @SerialName("picked_qty") val pickedQty: Int? = null,
    @SerialName("packed_qty") val packedQty: Int? = null,
    @SerialName("received_qty") val receivedQty: Int? = null,
    @SerialName("receiver_id") val receiverId: Int? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("receive_notes") val receiveNotes: String? = null,
    val uom: String? = null,
    val status: String? = null,
    val exception: String? = null,
    @SerialName("exception_notes") val exceptionNotes: String? = null,
    @SerialName("picked_at") val pickedAt: String? = null,
    @SerialName("packed_at") val packedAt: String? = null,
) {
    val resolvedId: Int get() = id ?: 0
}

@Serializable
data class WmsShippedPicklist(
    val id: Int? = null,
    val companyId: Int? = null,
    val zoneCode: String? = null,
    val status: String? = null,
    val packingStatus: String? = null,
    val dispatchStatus: String? = null,
    val receivingCompanyId: Int? = null,
    val receivingCompanyName: String? = null,
    val carrier: String? = null,
    val trackingNumber: String? = null,
    val invoiceNumber: String? = null,
    val invoiceDate: String? = null,
    val invoiceFileUrl: String? = null,
    val dispatchedAt: String? = null,
    val dispatchedBy: Int? = null,
    val createdAt: String? = null,
    val tasks: List<WmsShippedPicklistTask>? = null,
) {
    val resolvedId: Int get() = id ?: 0

    val displayTitle: String
        get() = invoiceNumber?.takeIf { it.isNotBlank() }
            ?: "Pick List #${id ?: 0}"

    val pickListTitle: String
        get() = "PICK-${String.format("%05d", id ?: 0)}"

    val dispatchStatusLabel: String
        get() = (dispatchStatus ?: "dispatched").replaceFirstChar { it.uppercase() }

    val totalPackedQty: Int
        get() = tasks?.sumOf { it.packedQty ?: 0 } ?: 0

    val lineCount: Int get() = tasks?.size ?: 0

    val isDelivered: Boolean
        get() {
            val allTasks = tasks.orEmpty()
            if (allTasks.isEmpty()) return false
            return allTasks.all { (it.receivedQty ?: 0) > 0 && it.receiverId != null }
        }

    val isPending: Boolean
        get() {
            val allTasks = tasks.orEmpty()
            if (allTasks.isEmpty()) return true
            return allTasks.none { (it.receivedQty ?: 0) > 0 && it.receiverId != null }
        }

    val isInProgress: Boolean
        get() {
            val allTasks = tasks.orEmpty()
            if (allTasks.isEmpty()) return false
            val verifiedCount = allTasks.count { (it.receivedQty ?: 0) > 0 && it.receiverId != null }
            return verifiedCount > 0 && verifiedCount < allTasks.size
        }
}

data class WmsShippedPicklistsPage(
    val items: List<WmsShippedPicklist>,
    val total: Int,
    val totalPages: Int,
    val page: Int,
)

@Serializable
data class WmsVerifyDispatchTaskRequest(
    val taskId: Int,
    val receivedQty: Int,
    val receiverId: Int,
    val receiveNotes: String = "",
)

@Serializable
data class WmsVerifyDispatchTaskResponse(
    val message: String? = null,
)

@Serializable
data class WmsDeliverDispatchRequest(
    val pickingListId: Int,
    val receivedBy: Int,
    val notes: String = "",
)

@Serializable
data class WmsDeliverDispatchResponse(
    val message: String? = null,
    val pickingListId: Int? = null,
    val dispatchStatus: String? = null,
    val totalItems: Int? = null,
    val fullyMatched: Int? = null,
    val withShortage: Int? = null,
    val receivedBy: Int? = null,
)

@Serializable
data class WmsDispatchManualInvoiceRequest(
    val pickingListId: Int,
    val companyId: Int,
    val invoiceNumber: String,
    val invoiceDate: String,
    val dispatchedBy: Int? = null,
)

@Serializable
data class WmsDispatchFullRequest(
    val pickingListId: Int,
    val companyId: Int,
    val receivingCompanyId: Int,
    val carrier: String? = null,
    val trackingNumber: String? = null,
    val invoiceNumber: String,
    val invoiceDate: String,
    val dispatchedBy: Int? = null,
    val notes: String? = null,
)

@Serializable
data class WmsDispatchInvoiceResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val pickingListId: Int? = null,
    val dispatchInvoiceId: Int? = null,
    val status: String? = null,
    val uploadType: String? = null,
)

@Serializable
data class WmsDispatchCompany(
    val id: Int = 0,
    val companyName: String = "",
    val companyEmail: String? = null,
    val companyContact: String? = null,
    val address1: String? = null,
    val city: String? = null,
) {
    val displayLocation: String?
        get() = listOfNotNull(
            address1?.trim()?.takeIf { it.isNotBlank() },
            city?.trim()?.takeIf { it.isNotBlank() },
        ).joinToString(", ").ifBlank { null }
}

data class WmsDispatchCompaniesPage(
    val companies: List<WmsDispatchCompany>,
    val total: Int,
    val page: Int,
    val totalPages: Int,
)

enum class DispatchInvoiceEntryMode(val label: String) {
    Upload("Upload Invoice"),
    Manual("Manual Entry"),
}

// ── Admin packing helpers ─────────────────────────────────────────────────────

enum class AdminPackingFilterCategory(val label: String) {
    All("All"),
    Pending("Pending"),
    InProgress("In Progress"),
    ReadyToDispatch("Ready to Dispatch"),
    Dispatched("Dispatched"),
    ;

    val filterColorHex: Long
        get() = when (this) {
            All -> 0xFF163C66
            Pending -> 0xFFD97706
            InProgress -> 0xFF2563EB
            ReadyToDispatch -> 0xFF059669
            Dispatched -> 0xFF6B7280
        }
}

fun WmsPickListItem.adminPackingFilterCategory(): AdminPackingFilterCategory {
    val ps = (packingStatus ?: "").uppercase()
    return when {
        ps == "DISPATCHED" || ps == "SHIPPED" -> AdminPackingFilterCategory.Dispatched
        ps == "PACKED" -> AdminPackingFilterCategory.ReadyToDispatch
        ps == "PACKING" || ps == "IN_PROGRESS" -> AdminPackingFilterCategory.InProgress
        (assignedPacker ?: assignedPackerId ?: 0) > 0 -> AdminPackingFilterCategory.InProgress
        else -> AdminPackingFilterCategory.Pending
    }
}

val WmsPickListItem.hasPackerAssigned: Boolean
    get() = (assignedPacker ?: assignedPackerId ?: 0) > 0

/** Primary status pill on admin packing cards — matches iOS `adminPackingStatusLabel`. */
val WmsPickListItem.adminPackingStatusLabel: String
    get() {
        val packing = packingStatus?.trim().orEmpty()
        if (packing.equals("shipped", ignoreCase = true)) return "DISPATCHED"
        if (hasPackerAssigned) return "Packer Assigned"
        if (packing.isNotBlank()) return packing.uppercase()
        val pickStatus = status?.trim().orEmpty()
        return pickStatus.ifBlank { "PICKED" }.uppercase()
    }

val WmsPickListItem.adminPackingCardTitle: String
    get() = formatPickListDisplayId(
        pickListId ?: numericId?.toString(),
        pickListNumber,
        pickListCode,
    ).ifBlank { displayTitle }

val WmsPickListItem.adminPackingResolvedLineCount: Int
    get() = lineCount?.takeIf { it > 0 } ?: resolvedLines.size

val WmsPickListItem.adminPackingRequestedQty: Int
    get() = totalRequestedQty?.takeIf { it > 0 }
        ?: resolvedLines.sumOf { it.resolvedRequestedQty() }.takeIf { it > 0 }
        ?: 0

val WmsPickListItem.adminPackingPackedQty: Int
    get() {
        val packing = packingStatus?.trim()?.uppercase().orEmpty()
        if (packing == "PACKED") return adminPackingRequestedQty
        return summary?.totalPackedQty?.takeIf { it > 0 }
            ?: resolvedLines.sumOf { it.resolvedPackedQty() }.takeIf { it > 0 }
            ?: 0
    }

val WmsPickListItem.adminPackingPickStatusLabel: String
    get() = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "PICKED"

val WmsPickListItem.adminPackingLocationLine: String
    get() = buildList {
        resolvedZoneCode?.takeIf { it.isNotBlank() }?.let { add(it) }
        val lines = adminPackingResolvedLineCount
        if (lines > 0) add("$lines lines")
    }.joinToString(" · ").ifBlank { "Ready for packaging" }

val WmsPickListItem.resolvedPickerName: String?
    get() = assignedPickerName?.takeIf { it.isNotBlank() }
        ?: packer?.displayName?.takeIf { it.isNotBlank() }

val WmsPickListItem.resolvedPackerName: String?
    get() = assignedPackerName?.takeIf { it.isNotBlank() }

fun WmsPickListItem.toPackingPickListItem(): WmsPackingPickListItem {
    val sourceLines = resolvedLines
    val packLines = sourceLines.map { line ->
        WmsPackingPickListLine(
            pickLineId = line.pickLineId ?: line.id,
            taskId = line.taskId?.trim()?.takeIf { it.isNotBlank() } ?: line.apiTaskOrLineId,
            productId = line.productId?.toIntOrNull(),
            productName = line.displayName,
            productSku = line.displaySku,
            gtin = line.scanGtin.trim().takeIf { it.isNotEmpty() },
            batchNumber = line.displayBatch.takeIf { it != "—" },
            expiryDate = line.expiryDate?.trim()?.takeIf { it.isNotBlank() },
            requestedQty = line.resolvedRequestedQty().takeIf { it > 0 },
            pickedQty = line.resolvedPickedQty().takeIf { it > 0 },
            packedQty = line.resolvedPackedQty().takeIf { it > 0 },
            status = line.status,
            exceptionTypes = line.exceptionTypes,
            exceptionNotes = line.exceptionNotes?.trim()?.takeIf { it.isNotBlank() },
        )
    }
    val totalRequested = sourceLines.sumOf { it.resolvedRequestedQty() }
        .takeIf { it > 0 } ?: totalRequestedQty ?: packLines.sumOf { it.requiredCount }
    val totalPicked = sourceLines.sumOf { it.resolvedPickedQty() }
        .takeIf { it > 0 } ?: totalPickedQty ?: packLines.sumOf { it.pickedCount }
    val totalPackedFromTasks = sourceLines.sumOf { it.resolvedPackedQty() }
    val totalPacked = summary?.totalPackedQty?.takeIf { it > 0 }
        ?: totalPackedFromTasks.takeIf { it > 0 }
        ?: packLines.sumOf { it.packedCount }
    val totalRemaining = summary?.remainingToPack?.takeIf { it >= 0 }
        ?: maxOf(0, totalRequested - totalPacked)
    return WmsPackingPickListItem(
        packingOrderId = packingOrderId,
        packingNumber = packingNumber,
        packingStatus = packingStatus,
        displayPackingStatus = displayPackingStatus ?: packingStatus,
        status = status,
        pickListId = resolvedAPIListId,
        pickListNumber = formatPickListDisplayId(resolvedAPIListId, pickListNumber, pickListCode),
        pickListCode = pickListCode,
        orderId = orderId,
        orderNumber = orderNumber,
        zoneCode = resolvedZoneCode,
        zone = zone,
        toteNumber = toteNumber,
        stagingLocationName = stagingLocationName,
        packingStartedAt = packingStartedAt,
        packedAt = packedAt,
        lineCount = lineCount ?: sourceLines.size,
        totalRequestedQty = totalRequested,
        totalPickedQty = totalPicked,
        totalPackedQty = totalPacked,
        totalRemainingToPackQty = totalRemaining,
        packingProgress = summary?.progress,
        boxCount = boxCount?.takeIf { it > 0 } ?: summary?.boxCount?.takeIf { it > 0 } ?: boxes?.size,
        inProgressBoxCount = openBoxCount?.takeIf { it > 0 }
            ?: (boxCount?.minus(completedBoxCount ?: 0))?.takeIf { it > 0 }
            ?: (summary?.boxCount?.minus(summary.sealedBoxCount ?: 0))?.takeIf { it > 0 },
        completedBoxCount = completedBoxCount?.takeIf { it > 0 } ?: summary?.sealedBoxCount?.takeIf { it > 0 },
        openBoxCount = openBoxCount?.takeIf { it > 0 }
            ?: (boxCount?.minus(completedBoxCount ?: 0))?.takeIf { it > 0 },
        canComplete = canComplete,
        lines = packLines,
        boxes = boxes.orEmpty(),
    )
}

/** Industry packing status normalization — matches iOS `WMSIndustryPackingStatus`. */
object WmsIndustryPackingStatus {
    fun normalizedListLabel(raw: String?, display: String?): String {
        val normalizedDisplay = display?.trim()?.uppercase().orEmpty()
        if (normalizedDisplay.isNotEmpty()) return normalizedDisplay
        return when (raw?.trim()?.lowercase().orEmpty()) {
            "pending" -> "PENDING"
            "packing", "in_progress", "in progress" -> "IN_PROGRESS"
            "packed" -> "PACKED"
            "shipped", "dispatched" -> "SHIPPED"
            else -> raw?.trim()?.uppercase().orEmpty().ifBlank { "PENDING" }
        }
    }

    fun normalizedBoxLabel(raw: String?, display: String?): String {
        val normalizedDisplay = display?.trim()?.uppercase().orEmpty()
        if (normalizedDisplay.isNotEmpty()) return normalizedDisplay
        return when (raw?.trim()?.lowercase().orEmpty()) {
            "open" -> "OPEN"
            "sealed" -> "SEALED"
            else -> raw?.trim()?.uppercase().orEmpty()
        }
    }

    fun isListPacked(label: String): Boolean =
        label == "PACKED" || label == "SHIPPED"

    fun isBoxOpen(label: String): Boolean =
        label == "OPEN" || label == "IN_PROGRESS" || label == "PACKING"

    fun isBoxSealed(label: String): Boolean =
        label == "SEALED" || label == "COMPLETED" || label == "PACKED" || label == "CLOSED"
}

fun industryNumericBoxId(raw: String?): Int? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return value.toIntOrNull()
}

fun industryNormalizedBoxId(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return industryNumericBoxId(trimmed)?.toString() ?: trimmed
}

fun industryBoxIdsMatch(left: String?, right: String?): Boolean {
    val a = industryNormalizedBoxId(left)
    val b = industryNormalizedBoxId(right)
    return a != null && a == b
}

fun industryAllSealedCartonsForList(boxes: List<WmsPackingBoxSummary>): List<WmsPackingBoxSummary> =
    boxes.filter { carton ->
        carton.isSecondaryPackage &&
            !carton.isPackedListSummary &&
            carton.isCompleted &&
            carton.hasIndustryBoxId
    }

fun industrySealedCartonsLinkedToPallet(
    boxes: List<WmsPackingBoxSummary>,
    palletId: String,
): List<WmsPackingBoxSummary> {
    val pallet = industryNormalizedBoxId(palletId) ?: return emptyList()
    return industryAllSealedCartonsForList(boxes).filter { carton ->
        industryBoxIdsMatch(carton.resolvedParentPackId, pallet)
    }
}

fun industrySealedCartonsAvailableForPallet(boxes: List<WmsPackingBoxSummary>): List<WmsPackingBoxSummary> =
    industryAllSealedCartonsForList(boxes).filter { carton ->
        carton.resolvedParentPackId.isNullOrBlank()
    }

fun industryOrphanCartonsOnPallet(
    boxes: List<WmsPackingBoxSummary>,
    palletId: String,
): List<WmsPackingBoxSummary> {
    val pallet = industryNormalizedBoxId(palletId) ?: return emptyList()
    val sealedIds = industryAllSealedCartonsForList(boxes).map { industryNormalizedBoxId(it.id) }.toSet()
    return boxes.filter {
        it.isSecondaryPackage &&
            !it.isPackedListSummary &&
            industryBoxIdsMatch(it.resolvedParentPackId, pallet)
    }.filter { carton ->
        industryNormalizedBoxId(carton.id) !in sealedIds && !carton.isCompleted
    }
}

private val dispatchJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

internal fun decodeShippedPicklistsPage(raw: String, page: Int): WmsShippedPicklistsPage {
    val element = dispatchJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        val items = element.mapNotNull {
            runCatching { dispatchJson.decodeFromJsonElement(WmsShippedPicklist.serializer(), it) }.getOrNull()
        }
        return WmsShippedPicklistsPage(items, items.size, 1, page)
    }
    val obj = element.jsonObject
    val array = obj["data"]?.jsonArray ?: JsonArray(emptyList())
    val items = array.mapNotNull {
        runCatching { dispatchJson.decodeFromJsonElement(WmsShippedPicklist.serializer(), it) }.getOrNull()
    }
    return WmsShippedPicklistsPage(
        items = items,
        total = obj["total"]?.jsonPrimitive?.intOrNull ?: items.size,
        totalPages = obj["totalPages"]?.jsonPrimitive?.intOrNull ?: 1,
        page = obj["page"]?.jsonPrimitive?.intOrNull ?: page,
    )
}

internal fun decodeDispatchCompaniesPage(raw: String, page: Int): WmsDispatchCompaniesPage {
    val element = dispatchJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        val companies = element.mapNotNull {
            runCatching { dispatchJson.decodeFromJsonElement(WmsDispatchCompany.serializer(), it) }.getOrNull()
        }
        return WmsDispatchCompaniesPage(companies, companies.size, page, 1)
    }
    val obj = element.jsonObject
    val array = obj["data"]?.jsonArray ?: JsonArray(emptyList())
    val companies = array.mapNotNull {
        runCatching { dispatchJson.decodeFromJsonElement(WmsDispatchCompany.serializer(), it) }.getOrNull()
    }
    return WmsDispatchCompaniesPage(
        companies = companies,
        total = obj["total"]?.jsonPrimitive?.intOrNull ?: companies.size,
        page = obj["page"]?.jsonPrimitive?.intOrNull ?: page,
        totalPages = obj["totalPages"]?.jsonPrimitive?.intOrNull ?: 1,
    )
}

@Serializable
data class IndustryPickingProductItem(
    val taskId: Int? = null,
    val sku: String? = null,
    val productName: String? = null,
    val gtin: String? = null,
    val batch: String? = null,
    val bin: String? = null,
    val uom: String? = null,
    val requestedQty: Int? = null,
    val pickedQty: Int? = null,
    val status: String? = null,
    val sequence: Int? = null,
)

@Serializable
data class IndustryPickingProductsResponse(
    val id: Int? = null,
    val pickListCode: String? = null,
    val companyId: Int? = null,
    val waveId: Int? = null,
    val zoneCode: String? = null,
    val zoneLocation: String? = null,
    val temperatureClass: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val products: List<IndustryPickingProductItem>? = null,
)

