package resolver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import core.network.repository.AppRepository
import core.network.repository.ResolverRepository
import core.storage.SessionManager
import core.storage.getLocalStorage
import core.util.AuditLogHelper
import features.app.resolver.CmsScanContext
import features.app.resolver.DefaultResolverTemplateData
import features.app.resolver.bindProductDetails
import features.app.resolver.defaultTemplateFromDetails
import features.app.resolver.scanEventsFromDetails
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import resolver.cms.ResolverCmsPage
import resolver.cms.cmsArray
import resolver.cms.cmsBuildProductFallbackPage
import resolver.cms.cmsDict
import resolver.cms.cmsDouble
import resolver.cms.cmsIsAssignedPage
import resolver.cms.cmsPageMatchesGtin
import resolver.cms.cmsProductFromConfigData
import resolver.cms.cmsProductMatchesGtin
import resolver.cms.cmsRootHasProduct
import resolver.cms.cmsSimilarProductsFromConfigData
import resolver.cms.cmsString
import resolver.cms.enrichCmsRootWithProduct
import resolver.cms.enrichCmsRootWithSimilar
import resolver.cms.isAuthenticQuality
import resolver.cms.parseCmsResponse
import resolver.distanceKm
import resolver.isExpired
import resolver.locationsMatch
import resolver.realValue
import resolverModels.ApplicationIdentifier
import resolverModels.AuthResult
import resolverModels.CurrentLocation
import resolverModels.DistStep
import resolverModels.DistStepStatus
import resolverModels.LocationData
import resolverModels.ParsedData
import utils.DeviceLocationProvider

private val EMPTY_JSON = JsonObject(emptyMap())

/**
 * Drives one Digital Link resolution: parse the link, authenticate it, and look
 * up the brand's CMS page. Authentication and the CMS lookup run in parallel,
 * matching the web resolver's independent effects.
 */
@Stable
class ResolverScreenState(private val url: String, private val sessionManager: SessionManager) {

    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var scan by mutableStateOf<DigitalLinkScan?>(null)
        private set

    var authResult by mutableStateOf<AuthResult?>(null)
        private set
    var isAuthLoading by mutableStateOf(false)
        private set

    var cmsChecked by mutableStateOf(false)
        private set
    var hasCmsPage by mutableStateOf(false)
        private set
    var cmsPage by mutableStateOf<ResolverCmsPage?>(null)
        private set
    var cmsRoot by mutableStateOf(EMPTY_JSON)
        private set

    var productDetails by mutableStateOf<JsonObject?>(null)
        private set
    var isProductDetailsLoading by mutableStateOf(false)
        private set

    var productData by mutableStateOf<JsonObject?>(null)
        private set
    var isProductLoading by mutableStateOf(false)
        private set

    var currentLocation by mutableStateOf<CurrentLocation?>(null)
        private set
    var currentAddress by mutableStateOf<LocationData?>(null)
        private set
    var locationError by mutableStateOf<String?>(null)
        private set
    var isLocationLoading by mutableStateOf(false)
        private set
    var locationSettled by mutableStateOf(false)
        private set
    var locationDenied by mutableStateOf(false)
        private set
    var locationPromptHidden by mutableStateOf(false)
        private set

    /** Mirrors the web: the prompt appears once locating has finished but failed. */
    val locationPromptVisible: Boolean
        get() = locationSettled && currentLocation == null && !locationPromptHidden

    var scanLogged by mutableStateOf(false)
        private set

    val data: ParsedData? get() = scan?.data
    val gtin: String? get() = scan?.gtin

    suspend fun load() {
        isLoading = true
        error = null
        val parsed = try {
            parseDigitalLink(url)
        } catch (e: Exception) {
            error = e.message ?: "Failed to resolve digital link"
            isLoading = false
            return
        }
        scan = parsed
        isLoading = false

        coroutineScope {
            launch { authenticate(parsed) }
            launch { loadCmsPage(parsed) }
            launch { requestLocation() }
            launch {
                // Log the scan once authentication and locating have settled,
                // mirroring the web's independent effect.
                val deadline = withTimeoutOrNull(12000) {
                    while ((isAuthLoading && authResult == null) || !locationSettled) {
                        delay(100)
                    }
                }
                logScan()
            }
        }
    }

    private suspend fun authenticate(parsed: DigitalLinkScan) {
        if (parsed.cleanUrl.isBlank()) return
        isAuthLoading = true
        ResolverRepository.authenticateBarcode(
            barcodeData = parsed.cleanUrl,
            encryptedText = parsed.ai98.orEmpty(),
            companyId = parsed.ai97.orEmpty(),
        ).onSuccess { record -> authResult = record?.toAuthResult() }
        isAuthLoading = false
    }

    /**
     * CMS page first, then product enrichment. The QA API sometimes answers
     * with another GTIN's page while `data.product` is correct, so the page is
     * only trusted when one of those two identifiers matches the scan.
     */
    private suspend fun loadCmsPage(parsed: DigitalLinkScan) {
        val gtin = parsed.gtin
        if (gtin.isNullOrBlank()) {
            cmsChecked = true
            return
        }

        cmsChecked = false
        hasCmsPage = false
        cmsPage = null
        cmsRoot = EMPTY_JSON

        val companyId = parsed.ai97?.trim()
        var pageOk = false
        var root = EMPTY_JSON

        if (!companyId.isNullOrEmpty()) {
            ResolverRepository.fetchCmsPage(gtin, companyId)
                .onSuccess { raw ->
                    val response = parseCmsResponse(raw)
                    root = response.root
                    val pageMatches = cmsPageMatchesGtin(response.page, gtin)
                    val assignedPage = cmsIsAssignedPage(response.page)

                    if (response.hasPage && !pageMatches && !assignedPage) {
                        if (cmsProductMatchesGtin(response.root, gtin)) {
                            val product = cmsDict(response.root["product"]) ?: EMPTY_JSON
                            pageOk = true
                            cmsRoot = response.root
                            // Same item with a differently padded GTIN keeps the full layout.
                            if (response.page != null &&
                                cmsPageMatchesGtin(response.page, cmsString(product["identifier"]))
                            ) {
                                cmsPage = response.page
                            } else {
                                cmsPage = cmsBuildProductFallbackPage(
                                    requestedGtin = gtin,
                                    product = product,
                                    theme = response.page?.content?.theme,
                                )
                            }
                            hasCmsPage = true
                        } else {
                            cmsRoot = response.root
                        }
                    } else {
                        pageOk = response.hasPage
                        cmsRoot = response.root
                        cmsPage = response.page
                        hasCmsPage = response.hasPage
                    }
                }
        }

        cmsChecked = true

        val config = ResolverConfig.payload
        val batchNumber = parsed.data.identifiers.firstOrNull { it.code == "10" }?.value
        val serial = parsed.data.identifiers.firstOrNull { it.code == "21" }?.value

        if (pageOk) {
            if (!cmsRootHasProduct(root) && config != null) {
                val response = loadProductConfig(config, gtin, batchNumber)
                val product = cmsProductFromConfigData(response)
                val similar = cmsSimilarProductsFromConfigData(response)
                if (product.isNotEmpty()) {
                    cmsRoot = enrichCmsRootWithProduct(cmsRoot, product)
                }
                if (similar.isNotEmpty()) {
                    cmsRoot = enrichCmsRootWithSimilar(cmsRoot, similar)
                }
            }
            return
        }

        loadProductDetails(gtin, companyId, batchNumber, serial)
        if (config != null) loadProductConfig(config, gtin, batchNumber)
    }

    private suspend fun loadProductDetails(
        gtin: String,
        companyId: String?,
        batch: String?,
        serial: String?,
    ) {
        val cid = companyId?.trim()?.ifEmpty { null }
            ?: AuditLogHelper.companyIdFromBarcode(url)?.toString()
            ?: return
        isProductDetailsLoading = true
        fetchDetailsWithGtinFallback(gtin, cid, batch, serial)
            ?.let { productDetails = bindProductDetails(it) }
        isProductDetailsLoading = false
    }

    /** True when a product-details payload actually carries product data. */
    private fun hasProductData(result: JsonObject?): Boolean {
        val dict = result ?: return false
        if (cmsDict(dict["product"]) != null ||
            cmsDict(dict["batchDetails"]) != null ||
            cmsDict(dict["companyDetails"]) != null ||
            cmsDict(dict["locationDetails"]) != null
        ) {
            return true
        }
        val data = cmsDict(dict["data"]) ?: return false
        return cmsDict(data["product"]) != null ||
            cmsDict(data["batchDetails"]) != null ||
            cmsDict(data["companyDetails"]) != null ||
            cmsDict(data["locationDetails"]) != null
    }

    /**
     * Retries the product-details lookup with the requested GTIN first, then a
     * 14-character zero-padded (or trailing-14) candidate, mirroring the web.
     */
    private suspend fun fetchDetailsWithGtinFallback(
        gtin: String,
        companyId: String?,
        batch: String?,
        serial: String?,
    ): JsonObject? {
        val cid = companyId?.trim()?.ifEmpty { null } ?: return null
        val digits = gtin.filter { it.isDigit() }
        val candidates = mutableListOf(gtin)
        when {
            digits.length in 8..13 -> candidates.add(digits.padStart(14, '0'))
            digits.length > 14 -> candidates.add(digits.takeLast(14))
        }
        for (candidate in candidates) {
            val result = ResolverRepository
                .fetchProductDetails(candidate, cid, batch, serial)
                .getOrNull() ?: continue
            if (hasProductData(result)) return result
        }
        return null
    }

    private suspend fun loadProductConfig(
        config: JsonObject,
        gtin: String,
        batchNumber: String?,
    ): JsonObject? {
        isProductLoading = true
        var response: JsonObject? = null
        val payload = buildJsonObject {
            config.forEach { (key, value) -> put(key, value) }
            put("gtin", gtin)
            if (batchNumber != null) put("batchNumber", batchNumber)
        }
        ResolverRepository.sendConfigPayload(payload).onSuccess { raw ->
            response = cmsDict(raw)
            val body = cmsDict(response?.get("data"))
            if (response?.let { cmsString(it["success"]) } != "false" && body != null) {
                productData = body
            }
        }
        isProductLoading = false
        return response
    }

    /** Location powers the diversion check against the invoice address. */
    suspend fun requestLocation() {
        isLocationLoading = true
        locationDenied = false
        locationError = null
        val coordinates = try {
            DeviceLocationProvider().getCurrentLocation()
        } catch (e: Exception) {
            null
        }
        if (coordinates == null) {
            locationDenied = locationError == null
            locationError = locationError
                ?: "Location access is blocked. Enable it in your settings."
            locationSettled = true
            isLocationLoading = false
            return
        }
        currentLocation = CurrentLocation(coordinates.first, coordinates.second)
        AppRepository.getLocationDetails(coordinates.first, coordinates.second)
            .onSuccess { details ->
                currentAddress = LocationData(
                    city = details.city.orEmpty(),
                    state = details.state.orEmpty(),
                    country = details.country.orEmpty(),
                    address = details.address,
                    postcode = details.postcode.orEmpty(),
                )
            }
            .onFailure { locationError = "Could not resolve your current address." }
        locationSettled = true
        locationDenied = false
        isLocationLoading = false
    }

    /** Re-runs the permission request from the location prompt, mirroring the web retry. */
    suspend fun retryLocation() {
        locationPromptHidden = false
        requestLocation()
    }

    fun dismissLocationPrompt() {
        locationPromptHidden = true
    }

    private val loggedKeys = mutableSetOf<String>()

    /** Mirrors the web `logDigitalLinkScan`: posts a SCAN audit row once per scan. */
    suspend fun logScan() {
        val parsed = scan ?: return
        val gtin = parsed.gtin ?: return
        if (gtin.isBlank()) return
        if (scanLogged) return
        val serial = parsed.data.identifiers.firstOrNull { it.code == "21" }?.value.orEmpty()
        val batch = parsed.data.identifiers.firstOrNull { it.code == "10" }?.value.orEmpty()
        val key = "$gtin|$serial|${parsed.cleanUrl}"
        if (key in loggedKeys) return
        loggedKeys.add(key)

        try {
            val geo = currentAddress?.address?.trim()?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(currentAddress?.city, currentAddress?.state)
                    .filter { it.isNotBlank() }.joinToString(", ")
                    .takeIf { it.isNotBlank() }
                ?: currentLocation?.let { formatCoordinates(it) }
                ?: "Unknown"
            val request = AuditLogHelper.buildScanLogRequest(
                sessionManager = sessionManager,
                epcId = "",
                barcodeType = "QR",
                barcodeData = parsed.cleanUrl,
                gtin = gtin,
                serial = serial,
                batch = batch,
                isAuthFlow = true,
                scannedValue = authResult?.quality.orEmpty(),
                authResultOverride = when (authResult?.quality?.trim()?.lowercase()) {
                    "real", "original", "authentic" -> "AUTHENTIC"
                    "fake", "counterfeit" -> "DIVERTED"
                    else -> "AUTHENTIC"
                },
                geoLocationOverride = geo,
            )
            val stored = AppRepository.sendScanCreateLog(request).isSuccess
            if (stored && !parsed.ai97.isNullOrBlank()) {
                // Mirror the web: after logging, go back to the QA API with the
                // fully-padded GTIN candidate to surface enriched details.
                fetchDetailsWithGtinFallback(
                    gtin = gtin,
                    companyId = parsed.ai97,
                    batch = batch,
                    serial = serial,
                )?.let { productDetails = bindProductDetails(it) }
            }
        } finally {
            scanLogged = true
        }
    }

    /** Live scan context for CMS pack/banner/section widgets, mirroring the web. */
    val scanContext: CmsScanContext
        get() = CmsScanContext(
            expectedLocation = expectedLocationLabel,
            locationAddress = currentAddress?.address,
            locationMatched = isLocationMatched,
            timesScanned = scanTotal,
            scanEvents = scanEvents,
            brandName = brandName,
            gtin = gtin,
            batchNumber = scanBatch,
            serialNumber = scanSerial,
            mfgDate = scanMfg,
            expiryDate = scanExpiry,
            locationLabel = locationLabel,
            companyLabel = brandName,
        )

    // MARK: - Derived product fields

    private val basic: JsonObject? get() = cmsDict(productData?.get("Basic Details"))
    private val batchDetails: JsonObject? get() = cmsDict(productData?.get("Batch Details"))
    private val invoiceDetails: JsonObject? get() = cmsDict(productData?.get("Invoice Details"))

    private val primaryId: ApplicationIdentifier?
        get() = data?.identifiers?.firstOrNull { it.code == "01" || it.code == "02" }

    val defaultTemplate: DefaultResolverTemplateData
        get() = defaultTemplateFromDetails(
            root = productDetails,
            scanBatch = scanBatch,
            scanMfg = scanMfg,
            scanExpiry = scanExpiry,
            deviceCity = currentAddress?.city,
            deviceAddress = currentAddress?.address,
            locationMatched = isLocationMatched,
            expectedLocation = expectedLocationLabel,
            gtin = gtin,
            serial = scanSerial,
        )

    private val detailsInvoice: JsonObject? get() = cmsDict(productDetails?.get("invoiceDetails"))
    private val detailsSite: JsonObject? get() = cmsDict(productDetails?.get("locationDetails"))

    val productName: String
        get() = cmsString(basic?.get("Product Name"))?.takeIf { it.isNotBlank() }
            ?: defaultTemplate.productName

    val brandName: String? get() = cmsString(basic?.get("Brand Name"))?.takeIf { it.isNotBlank() }

    val gtinValue: String
        get() = cmsString(basic?.get("GTIN"))?.takeIf { it.isNotBlank() }
            ?: primaryId?.value
            ?: "—"

    val allergen: String
        get() = cmsString(cmsDict(productData?.get("Allergen Info"))?.get("Allergen"))
            ?: "Not Available"

    val sideEffects: String
        get() {
            val block = cmsDict(productData?.get("Side Effects"))
            return cmsString(block?.get("Side Effects"))
                ?: cmsString(block?.get("Country of Birth"))
                ?: cmsString(basic?.get("Side Effects"))
                ?: "Not Available"
        }

    val authQuality: String? get() = authResult?.quality

    val isReal: Boolean? get() = authQuality?.let { isAuthenticQuality(it) }

    val isExpiredProduct: Boolean
        get() = data?.identifiers?.firstOrNull { it.code == "17" }?.let { isExpired(it.value) } ?: false

    val invoiceCity: String?
        get() = realValue(cmsString(detailsInvoice?.get("customerCity")))
            ?: realValue(
                cmsString(invoiceDetails?.get("City")) ?: cmsString(invoiceDetails?.get("Customer City")),
            )
            ?: realValue(cmsString(detailsSite?.get("city")))

    val invoiceState: String?
        get() = realValue(cmsString(detailsInvoice?.get("customerState")))
            ?: realValue(
                cmsString(invoiceDetails?.get("State")) ?: cmsString(invoiceDetails?.get("Customer State")),
            )
            ?: realValue(cmsString(detailsSite?.get("state")))

    val invoiceCountry: String?
        get() = realValue(cmsString(detailsInvoice?.get("customerCountry")))
            ?: realValue(cmsString(invoiceDetails?.get("Customer Country")))
            ?: realValue(cmsString(detailsSite?.get("country")))

    val expectedLocationLabel: String?
        get() = listOfNotNull(invoiceCity, invoiceState)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotBlank() }

    /** Null until both a device address and an expected (invoice / site) city are known. */
    val isLocationMatched: Boolean?
        get() {
            val address = currentAddress ?: return null
            val byName = locationsMatch(
                userCity = address.city,
                userState = address.state,
                userCountry = address.country,
                expectedCity = invoiceCity,
                expectedState = invoiceState,
                expectedCountry = invoiceCountry,
            )
            val siteLat = cmsDouble(detailsSite?.get("lat"))
            val siteLng = cmsDouble(detailsSite?.get("long")) ?: cmsDouble(detailsSite?.get("lng"))
            val here = currentLocation
            val byGps = if (
                here != null &&
                siteLat != null && siteLng != null &&
                kotlin.math.abs(siteLat) > 0.01 && kotlin.math.abs(siteLng) > 0.01
            ) {
                distanceKm(here.lat, here.lng, siteLat, siteLng) <= 50.0
            } else {
                null
            }
            return byName ?: byGps
        }

    val invoiceNumber: String?
        get() = realValue(cmsString(detailsInvoice?.get("invoiceNumber")))
            ?: realValue(cmsString(invoiceDetails?.get("Invoice Number")))

    val invoiceDate: String?
        get() = realValue(cmsString(detailsInvoice?.get("invoiceDate")))
            ?: realValue(cmsString(invoiceDetails?.get("Invoice Date")))

    val customerName: String
        get() = realValue(cmsString(detailsInvoice?.get("customerName")))
            ?: realValue(cmsString(invoiceDetails?.get("Customer Name")))
            ?: "Customer"

    val customerAddress: String?
        get() = realValue(cmsString(detailsInvoice?.get("customerAddress")))
            ?: realValue(
                cmsString(invoiceDetails?.get("Address")) ?: cmsString(invoiceDetails?.get("Customer Address")),
            )

    val locationLabel: String?
        get() = currentAddress?.let {
            listOf(it.city, it.state).filter { part -> part.isNotBlank() }.joinToString(", ")
        }?.takeIf { it.isNotBlank() }

    val scanBatch: String?
        get() = data?.identifiers?.firstOrNull { it.code == "10" }?.value
            ?: realValue(cmsString(batchDetails?.get("Batch Number")))

    val scanSerial: String? get() = data?.identifiers?.firstOrNull { it.code == "21" }?.value

    val scanMfg: String?
        get() = formatScanDate(
            data?.identifiers?.firstOrNull { it.code == "11" }?.value
                ?: data?.identifiers?.firstOrNull { it.code == "13" }?.value
                ?: cmsString(batchDetails?.get("Manufacturing Date")),
        )

    val scanExpiry: String?
        get() = formatScanDate(
            data?.identifiers?.firstOrNull { it.code == "17" }?.value
                ?: data?.identifiers?.firstOrNull { it.code == "15" }?.value
                ?: cmsString(batchDetails?.get("Expiry Date")),
        )

    val distributionSteps: List<DistStep>
        get() {
            val apiPath = cmsArray(productData?.get("Distribution Path"))
            if (apiPath != null && apiPath.isNotEmpty()) {
                return apiPath.mapNotNull { cmsDict(it) }.map { item ->
                    DistStep(
                        label = cmsString(item["label"]) ?: cmsString(item["name"]) ?: "Unknown",
                        sub = listOf(
                            cmsString(item["city"]).orEmpty(),
                            cmsString(item["state"]).orEmpty(),
                            cmsString(item["address"])?.let { "— $it" }.orEmpty(),
                        ).filter { it.isNotBlank() }.joinToString(" "),
                        status = when (cmsString(item["status"])) {
                            "transit" -> DistStepStatus.TRANSIT
                            "target" -> DistStepStatus.TARGET
                            "diverted" -> DistStepStatus.DIVERTED
                            "invoiced" -> DistStepStatus.INVOICED
                            else -> DistStepStatus.DONE
                        },
                    )
                }
            }

            val steps = mutableListOf<DistStep>()
            if (invoiceCity != null || invoiceState != null) {
                var sub = listOfNotNull(invoiceCity, invoiceState).joinToString(", ")
                customerAddress?.let { sub += " — $it" }
                invoiceDate?.let { sub += " | ${formatDate(it)}" }
                steps.add(
                    DistStep(
                        label = "Invoice - $customerName",
                        sub = sub,
                        status = DistStepStatus.INVOICED,
                        badge = invoiceNumber,
                    ),
                )
            }

            currentLocation?.let { location ->
                steps.add(
                    DistStep(
                        label = "Current Location",
                        sub = locationLabel ?: formatCoordinates(location),
                        status = DistStepStatus.TARGET,
                    ),
                )
            }

            if (steps.isEmpty()) {
                steps.add(
                    DistStep(
                        label = "No Distribution Data",
                        sub = "No tracking or invoice details available",
                        status = DistStepStatus.TARGET,
                    ),
                )
            }
            return steps
        }

    val scanEvents: List<features.app.resolver.ScanEvent>
        get() = scanEventsFromDetails(productDetails)

    val scanTotal: Double?
        get() = cmsDouble(cmsDict(productDetails?.get("scannedDetails"))?.get("total"))
}

@Composable
fun rememberResolverScreenState(url: String): ResolverScreenState {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val state = remember(url) { ResolverScreenState(url, sessionManager) }
    LaunchedEffect(url) { state.load() }
    return state
}

private fun formatCoordinates(location: CurrentLocation): String {
    fun round4(value: Double): String {
        val scaled = (value * 10000).toLong()
        val whole = scaled / 10000
        val fraction = kotlin.math.abs(scaled % 10000).toString().padStart(4, '0')
        return "$whole.$fraction"
    }
    return "${round4(location.lat)}, ${round4(location.lng)}"
}

private fun JsonObject.toAuthResult(): AuthResult = AuthResult(
    barcodeData = cmsString(this["barcode_data"]),
    gs1Data = cmsDict(this["gs1_data"])?.mapNotNull { (code, value) ->
        val entry = cmsDict(value) ?: return@mapNotNull null
        code to mapOf(
            "name" to cmsString(entry["name"]).orEmpty(),
            "value" to cmsString(entry["value"]).orEmpty(),
        )
    }?.toMap(),
    encryptedText = cmsString(this["encrypted_text"]),
    quality = cmsString(this["quality"]),
)
