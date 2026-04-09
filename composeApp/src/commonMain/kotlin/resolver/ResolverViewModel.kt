//package resolver
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import core.network.repository.AppRepository
//import core.network.models.AuditLogRequest
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.flow.update
//import kotlinx.coroutines.launch
//import kotlinx.serialization.json.Json
//import kotlinx.serialization.json.jsonObject
//import resolverModels.CurrentLocation
//import resolverModels.DistStep
//import resolverModels.LocationData
//import resolverModels.ParsedData
//
//class ResolverViewModel : ViewModel() {
//
//    private val _state = MutableStateFlow(ResolverState())
//    val state: StateFlow<ResolverState> = _state.asStateFlow()
//
//    // ── Entry point: called from Screen with the scanned URL ─────────────────
//    fun init(fullUrl: String) {
//        val (cleanUrl, ai97, ai98) = removeAuthFromFullUrl(fullUrl)
//        _state.update { it.copy(cleanUrl = cleanUrl, ai97 = ai97, ai98 = ai98) }
//        loadConfig()
//        resolveLink(fullUrl)
//        if (ai97 != null && ai98 != null) authenticateBarcode(cleanUrl, ai97, ai98)
//    }
//
//    // ── Config loading ────────────────────────────────────────────────────────
//    private fun loadConfig() {
//        viewModelScope.launch {
//            try {
//                // Read from composeResources/files/config.json
//                val bytes = org.jetbrains.compose.resources.ExperimentalResourceApi::class
//                // Use generated Res accessor:
//                val jsonText = readConfigFile() // see platform impl below
//                val config = Json.parseToJsonElement(jsonText).jsonObject
//                    .entries.associate { (k, v) -> k to v.toString() }
//                _state.update { it.copy(config = config) }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
//
//    // ── Parse & resolve the digital link ─────────────────────────────────────
//    private fun resolveLink(fullUrl: String) {
//        viewModelScope.launch {
//            _state.update { it.copy(isLoading = true, error = null) }
//            try {
//                val parsed = GS1Parser.parse(fullUrl)
//
//                val gtin = parsed.identifiers.firstOrNull {
//                    it.code == "01"
//                }?.value
//
//                _state.update { it.copy(
//                    parsedData = parsed,
//                    gtin = gtin,
//                    isLoading = false
//                )}
//
//                gtin?.let { loadProduct(it, parsed) }
//
//            } catch (e: Exception) {
//                _state.update { it.copy(
//                    isLoading = false,
//                    error = e.message ?: "Failed to resolve digital link"
//                )}
//            }
//        }
//    }
//
//    // ── Product data ──────────────────────────────────────────────────────────
//    private fun loadProduct(gtin: String, parsed: ParsedData) {
//        val config = _state.value.config ?: return
//        viewModelScope.launch {
//            _state.update { it.copy(isProductLoading = true) }
//            try {
//                val batchNumber = parsed.identifiers
//                    .firstOrNull { it.code == "10" }?.value
//
//                val payload = buildMap<String, Any> {
//                    putAll(config)
//                    put("gtin", gtin)
//                    batchNumber?.let { put("batchNumber", it) }
//                }
//
//                // Call your existing repository method
//                // Replace with actual sendConfigPayload equivalent:
//                val result = AppRepository.sendConfigPayload(payload)
//                result.onSuccess { data ->
//                    _state.update { it.copy(productData = data, isProductLoading = false) }
//                }.onFailure {
//                    _state.update { it.copy(isProductLoading = false) }
//                }
//
//            } catch (e: Exception) {
//                _state.update { it.copy(isProductLoading = false) }
//            }
//        }
//    }
//
//    // ── Barcode authentication ────────────────────────────────────────────────
//    private fun authenticateBarcode(cleanUrl: String, ai97: String, ai98: String) {
//        viewModelScope.launch {
//            _state.update { it.copy(isAuthLoading = true) }
//            try {
//                val result = AppRepository.authenticateBarcode(
//                    barcodeData = cleanUrl,
//                    encryptedText = ai98,
//                    companyId = ai97
//                )
//                result.onSuccess { authResult ->
//                    _state.update { it.copy(
//                        authResult = authResult,
//                        isAuthLoading = false
//                    )}
//                }.onFailure {
//                    _state.update { it.copy(isAuthLoading = false) }
//                }
//            } catch (e: Exception) {
//                _state.update { it.copy(isAuthLoading = false) }
//            }
//        }
//    }
//
//    // ── Location ──────────────────────────────────────────────────────────────
//    fun onLocationReceived(lat: Double, lng: Double) {
//        _state.update { it.copy(
//            currentLocation = CurrentLocation(lat, lng),
//            isLocationLoading = true,
//            locationError = null
//        )}
//        viewModelScope.launch {
//            AppRepository.getLocationDetails(lat, lng)
//                .onSuccess { loc ->
//                    _state.update { it.copy(
//                        currentAddress = LocationData(
//                            city = loc.city ?: "",
//                            state = loc.state ?: "",
//                            country = loc.country ?: ""
//                        ),
//                        isLocationLoading = false
//                    )}
//                }
//                .onFailure { err ->
//                    _state.update { it.copy(
//                        locationError = err.message,
//                        isLocationLoading = false
//                    )}
//                }
//        }
//    }
//
//    fun onLocationError(message: String) {
//        _state.update { it.copy(locationError = message, isLocationLoading = false) }
//    }
//
//    fun onLocationLoading() {
//        _state.update { it.copy(isLocationLoading = true, locationError = null) }
//    }
//
//    // ── Copy to clipboard ─────────────────────────────────────────────────────
//    fun onCopyGtin() {
//        _state.update { it.copy(isCopied = true) }
//        viewModelScope.launch {
//            kotlinx.coroutines.delay(1500)
//            _state.update { it.copy(isCopied = false) }
//        }
//    }
//
//    // ── Distribution steps builder ────────────────────────────────────────────
//    fun buildDistSteps(state: ResolverState): List<DistStep> {
//        val apiPath = state.productData?.get("Distribution Path") as? List<Map<String, Any>>
//        if (!apiPath.isNullOrEmpty()) {
//            return apiPath.map { item ->
//                DistStep(
//                    label = (item["label"] ?: item["name"] ?: "Unknown").toString(),
//                    sub = listOf(
//                        item["city"]?.toString() ?: "",
//                        item["state"]?.toString() ?: "",
//                        item["address"]?.let { "— $it" } ?: ""
//                    ).filter { it.isNotBlank() }.joinToString(" "),
//                    status = when (item["status"]?.toString()) {
//                        "done"     -> DistStepStatus.DONE
//                        "transit"  -> DistStepStatus.TRANSIT
//                        "diverted" -> DistStepStatus.DIVERTED
//                        "invoiced" -> DistStepStatus.INVOICED
//                        else       -> DistStepStatus.TARGET
//                    }
//                )
//            }
//        }
//
//        val steps = mutableListOf<DistStep>()
//
//        if (state.invoiceCity != null || state.invoiceState != null) {
//            val locationParts = listOfNotNull(state.invoiceCity, state.invoiceState)
//                .joinToString(", ")
//            var sub = locationParts
//            state.customerAddress?.let { sub += " — $it" }
//            state.invoiceDate?.let { sub += " | ${formatDate(it)}" }
//
//            steps.add(DistStep(
//                label = "Invoice - ${state.customerName}",
//                sub = sub,
//                status = DistStepStatus.INVOICED,
//                badge = state.invoiceNumber
//            ))
//        }
//
//        state.currentLocation?.let { loc ->
//            val label = state.currentAddress?.let {
//                listOf(it.city, it.state).filter { s -> s.isNotBlank() }.joinToString(", ")
//            } ?: "${"%.4f".format(loc.lat)}, ${"%.4f".format(loc.lng)}"
//
//            steps.add(DistStep(
//                label = "Current Location",
//                sub = label,
//                status = DistStepStatus.TARGET
//            ))
//        }
//
//        if (steps.isEmpty()) {
//            steps.add(DistStep(
//                label = "No Distribution Data",
//                sub = "No tracking or invoice details available",
//                status = DistStepStatus.TARGET
//            ))
//        }
//
//        return steps
//    }
//
//    // ── Helpers ───────────────────────────────────────────────────────────────
//    private data class AuthExtract(
//        val cleanUrl: String,
//        val ai97: String?,
//        val ai98: String?
//    )
//
//    private fun removeAuthFromFullUrl(url: String): AuthExtract {
//        // Port your JS logic to Kotlin string processing
//        var ai97: String? = null
//        var ai98: String? = null
//
//        val urlObj = java.net.URL(url)
//        val params = urlObj.query?.split("&")
//            ?.map { it.split("=", limit = 2) }
//            ?.filter { it.size == 2 }
//            ?.associate { it[0] to it[1] } ?: emptyMap()
//
//        val cleanParams = mutableMapOf<String, String>()
//
//        params.forEach { (key, value) ->
//            when (key) {
//                "97" -> { ai97 = value }
//                "98" -> {
//                    val parts = value.split("/")
//                    ai98 = parts[0]
//                    parts.getOrNull(1)?.split("=")?.let { kv ->
//                        if (kv.getOrNull(0) == "97") ai97 = kv.getOrNull(1)
//                    }
//                }
//                else -> cleanParams[key] = value
//            }
//        }
//
//        val cleanQuery = cleanParams.entries.joinToString("&") { "${it.key}=${it.value}" }
//        val cleanUrl = "${urlObj.protocol}://${urlObj.host}${urlObj.path}" +
//                if (cleanQuery.isNotEmpty()) "?$cleanQuery" else ""
//
//        return AuthExtract(cleanUrl, ai97, ai98)
//    }
//}
//
//fun formatDate(dateString: String): String {
//    return try {
//        // Use kotlinx-datetime
//        val date = kotlinx.datetime.LocalDate.parse(dateString.substring(0, 10))
//        "${date.dayOfMonth} ${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
//    } catch (e: Exception) { dateString }
//}
//
//fun formatGS1Date(value: String): String {
//    if (value.length != 6) return value
//    val year = "20${value.substring(0, 2)}"
//    val month = value.substring(2, 4)
//    val day = value.substring(4, 6)
//    return "$year-$month-$day"
//}