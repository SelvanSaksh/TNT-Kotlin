package resolverModels

import kotlinx.serialization.json.JsonObject

/** Category buckets carried over from the web resolver's AI catalogue. */
object AiCategory {
    const val PRIMARY = "primary"
    const val ATTRIBUTE = "attribute"
    const val LOGISTICS = "logistics"
    const val OTHER = "other"
}

/** Where an identifier was found in the Digital Link. */
object AiSource {
    const val PATH = "path"
    const val QUERY = "query"
}

data class ApplicationIdentifier(
    val code: String,
    val name: String,
    val value: String,
    val category: String,
    val source: String,
)

/**
 * AIs 96 through 99 are company-internal and are kept out of the displayed
 * identifier grid: 97 and 98 carry the authentication company id and token.
 */
data class SpecialIdentifier(
    val code: String,
    val name: String,
    val value: String,
    val source: String,
)

data class ParsedData(
    val domain: String,
    val path: String,
    val queryString: String,
    val identifiers: List<ApplicationIdentifier>,
    val specialIdentifiers: List<SpecialIdentifier>,
    val raw: JsonObject,
    val timestamp: String,
)

data class AuthResult(
    val barcodeData: String? = null,
    val gs1Data: Map<String, Map<String, String>>? = null,
    val encryptedText: String? = null,
    val quality: String? = null,
)

data class DistStep(
    val label: String,
    val sub: String,
    val status: DistStepStatus,
    val badge: String? = null,
)

enum class DistStepStatus { DONE, TRANSIT, TARGET, DIVERTED, INVOICED }

data class LocationData(
    val city: String,
    val state: String,
    val country: String,
)

data class CurrentLocation(
    val lat: Double,
    val lng: Double,
)
