//package resolver
//
//import kotlinx.datetime.plus
//import kotlinx.datetime.toLocalDateTime
//import resolverModels.ApplicationIdentifier
//import resolverModels.AuthResult
//import resolverModels.CurrentLocation
//import resolverModels.LocationData
//import resolverModels.ParsedData
//import kotlin.time.Clock
//import kotlin.time.ExperimentalTime
//
//data class ResolverState(
//    // Core loading
//    val isLoading: Boolean = true,
//    val error: String? = null,
//
//    // Parsed data
//    val parsedData: ParsedData? = null,
//    val cleanUrl: String = "",
//    val gtin: String? = null,
//
//    // Auth
//    val ai97: String? = null,
//    val ai98: String? = null,
//    val authResult: AuthResult? = null,
//    val isAuthLoading: Boolean = false,
//
//    // Product
//    val productData: Map<String, Any>? = null,
//    val isProductLoading: Boolean = false,
//
//    // Location
//    val currentLocation: CurrentLocation? = null,
//    val currentAddress: LocationData? = null,
//    val locationError: String? = null,
//    val isLocationLoading: Boolean = false,
//
//    // UI
//    val isCopied: Boolean = false,
//
//    // Config
//    val config: Map<String, Any>? = null
//) {
//    // Derived from productData
//    val basic: Map<String, Any> get() =
//        (productData?.get("Basic Details") as? Map<String, Any>) ?: emptyMap()
//
//    val batchDetails: Map<String, Any> get() =
//        (productData?.get("Batch Details") as? Map<String, Any>) ?: emptyMap()
//
//    val invoiceDetails: Map<String, Any> get() =
//        (productData?.get("Invoice Details") as? Map<String, Any>) ?: emptyMap()
//
//    val productName: String get() =
//        (basic["Product Name"] as? String) ?: "Premium Product"
//
//    val brandName: String? get() =
//        basic["Brand Name"] as? String
//
//    val gtinValue: String get() =
//        (basic["GTIN"] as? String)
//            ?: parsedData?.identifiers?.firstOrNull {
//                it.code == "01" || it.code == "02"
//            }?.value
//            ?: "—"
//
//    val allergen: String get() =
//        ((productData?.get("Allergen Info") as? Map<*, *>)
//            ?.get("Allergen") as? String) ?: "Not Available"
//
//    val sideEffects: String get() =
//        ((productData?.get("Side Effects") as? Map<*, *>)
//            ?.get("Side Effects") as? String)
//            ?: (basic["Side Effects"] as? String)
//            ?: "Not Available"
//
//    val authQuality: String? get() = authResult?.quality
//
//    val isReal: Boolean? get() = authQuality?.let {
//        listOf("real", "original", "authentic").contains(it.trim().lowercase())
//    }
//
//    val expiryAI: ApplicationIdentifier? get() =
//        parsedData?.identifiers?.firstOrNull { it.code == "17" }
//
//    val isExpiredProduct: Boolean get() =
//        expiryAI?.let { isExpired(it.value) } ?: false
//
//    val invoiceCity: String? get() =
//        realValue(
//            (invoiceDetails["City"] as? String)
//                ?: (invoiceDetails["Customer City"] as? String)
//        )
//
//    val invoiceState: String? get() =
//        realValue(
//            (invoiceDetails["State"] as? String)
//                ?: (invoiceDetails["Customer State"] as? String)
//        )
//
//    val invoiceCountry: String? get() =
//        realValue(invoiceDetails["Customer Country"] as? String)
//
//    val invoiceNumber: String? get() =
//        realValue(invoiceDetails["Invoice Number"] as? String)
//
//    val invoiceDate: String? get() =
//        realValue(invoiceDetails["Invoice Date"] as? String)
//
//    val customerName: String get() =
//        realValue(invoiceDetails["Customer Name"] as? String) ?: "Customer"
//
//    val customerAddress: String? get() =
//        realValue(
//            (invoiceDetails["Address"] as? String)
//                ?: (invoiceDetails["Customer Address"] as? String)
//        )
//
//    val isLocationMatched: Boolean? get() {
//        val addr = currentAddress ?: return null
//        val city = invoiceCity ?: return null
//        val cityMatch = normalizeCity(addr.city) == normalizeCity(city)
//        val stateMatch = invoiceState
//            ?.let { addr.state.lowercase() == it.lowercase() } ?: true
//        val countryMatch = invoiceCountry
//            ?.let { addr.country.lowercase() == it.lowercase() } ?: true
//        return cityMatch && stateMatch && countryMatch
//    }
//}
//
//// ── Helpers ───────────────────────────────────────────────────────────────────
//
//fun realValue(value: String?): String? =
//    if (value.isNullOrBlank() || value.trim().lowercase() == "n/a") null
//    else value
//
//fun normalizeCity(city: String): String {
//    val map = mapOf(
//        "new delhi" to "delhi", "delhi" to "delhi",
//        "mumbai" to "mumbai", "bombay" to "mumbai",
//        "chennai" to "chennai", "madras" to "chennai",
//        "bengaluru" to "bangalore", "bangalore" to "bangalore",
//        "kolkata" to "kolkata", "calcutta" to "kolkata"
//    )
//    return map[city.lowercase().trim()] ?: city.lowercase().trim()
//}
//
//@OptIn(ExperimentalTime::class)
//fun isExpired(value: String): Boolean {
//    if (value.length != 6) return false
//    return try {
//        val year = 2000 + value.substring(0, 2).toInt()
//        val month = value.substring(2, 4).toInt()
//        val day = value.substring(4, 6).toInt()
//        // Use kotlinx-datetime for multiplatform
//        val now = Clock.System.now()
//            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
//        val expiry = kotlinx.datetime.LocalDate(year, month, day)
//        expiry < now
//    } catch (e: Exception) { false }
//}
//
//@OptIn(ExperimentalTime::class)
//fun isExpiringSoon(value: String): Boolean {
//    if (value.length != 6) return false
//    return try {
//        val year = 2000 + value.substring(0, 2).toInt()
//        val month = value.substring(2, 4).toInt()
//        val day = value.substring(4, 6).toInt()
//        val now = Clock.System.now()
//            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
//        val expiry = kotlinx.datetime.LocalDate(year, month, day)
//        val sixMonthsLater = now.plus(6, kotlinx.datetime.DateTimeUnit.MONTH)
//        expiry <= sixMonthsLater
//    } catch (e: Exception) { false }
//}