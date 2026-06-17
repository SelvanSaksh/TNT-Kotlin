package core.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parameters for `GET /companies/barcode/logs/lookup`.
 * Prefer `gtin`, `batch`, and `serial` query params when known; otherwise use legacy `key=`.
 */
data class BarcodeLookupQuery(
    val gtin: String = "",
    val batch: String = "",
    val serial: String = "",
    val legacyKey: String = "",
) {
    fun canOpenTrackTrace(): Boolean =
        gtin.isNotBlank() || batch.isNotBlank() || serial.isNotBlank() ||
            (legacyKey.isNotBlank() && legacyKey != "Unknown")

    fun hasStructured(): Boolean =
        gtin.isNotBlank() || batch.isNotBlank() || serial.isNotBlank()

    /** Label for the summary card (GTIN · batch · serial or legacy key). */
    fun displayKey(): String = when {
        hasStructured() ->
            listOfNotNull(
                gtin.takeIf { it.isNotBlank() },
                batch.takeIf { it.isNotBlank() },
                serial.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        else -> legacyKey
    }
}

@Serializable
data class BarcodeLookupResponse(
    val key: String = "",
    val total: Int = 0,
    val data: List<BarcodeTraceEvent> = emptyList(),
)

@Serializable
data class BarcodeTraceEvent(
    val id: Int? = null,
    val event_id: String? = null,
    val event_type: String? = null,
    val epc_id: String? = null,
    val event_time: String? = null,
    val biz_step: String? = null,
    val biz_location: String? = null,
    val geo_location: String? = null,
    val auth_result: String? = null,
    val scanner_id: String? = null,
    val barcode_type: String? = null,
    val barcode_data: String? = null,
    val serial: String? = null,
    val batch: String? = null,
    val device_type: String? = null,
    val lat: Double? = null,
    @SerialName("long")
    val lon: Double? = null,
    val user: TraceUser? = null,
    val product: TraceProduct? = null,
)

@Serializable
data class TraceUser(
    val id: Int? = null,
    val email: String? = null,
    val firstName: String? = null,
    @SerialName("first_name")
    val firstNameSnake: String? = null,
    val lastName: String? = null,
    @SerialName("last_name")
    val lastNameSnake: String? = null,
)

@Serializable
data class TraceProduct(
    val id: String? = null,
    val productName: String? = null,
    @SerialName("product_name")
    val productNameSnake: String? = null,
    val brandName: String? = null,
    @SerialName("brand_name")
    val brandNameSnake: String? = null,
    val sku: String? = null,
    @SerialName("SKU")
    val skuUpper: String? = null,
)
