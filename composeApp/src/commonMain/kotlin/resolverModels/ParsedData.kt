package resolverModels

import kotlinx.serialization.Serializable

data class ApplicationIdentifier(
    val code: String,
    val name: String,
    val value: String,
    val category: String, // "primary" | "attribute" | "logistics" | "other"
    val source: String    // "path" | "query"
)

data class SpecialIdentifier(
    val code: String,
    val name: String,
    val value: String,
    val source: String
)

data class ParsedData(
    val domain: String,
    val path: String,
    val queryString: String,
    val identifiers: List<ApplicationIdentifier>,
    val specialIdentifiers: List<SpecialIdentifier>,
    val productData: Map<String, Any>?,
    val authResult: AuthResult?,
    val raw: Map<String, Any?>,
    val timestamp: String
)

data class AuthResult(
    val barcodeData: String? = null,
    val gs1Data: Map<String, Map<String, String>>? = null,
    val encryptedText: String? = null,
    val quality: String? = null
)

data class DistStep(
    val label: String,
    val sub: String,
    val status: DistStepStatus,
    val badge: String? = null
)

enum class DistStepStatus { DONE, TRANSIT, TARGET, DIVERTED, INVOICED }

data class LocationData(
    val city: String,
    val state: String,
    val country: String
)

data class CurrentLocation(
    val lat: Double,
    val lng: Double
)