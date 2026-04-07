package core.network.models

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@Serializable
data class FetchAi(
    val id: Int,
    val ai: String,
    val data_content: String,
    val format: String,
    val fnc1_required: String,
    val data_title: String? = null
)

@Serializable
data class BarcodeResponse(
    val filename: String
)


@Serializable
data class LocationDetails(
    val lat: Double,
    val long: Double,
    val currentCity: String,
    val state: String
)@Serializable




data class NominatimResponse(
    val display_name: String,
    val address: Address
)

@kotlinx.serialization.Serializable
data class AuditLogResponse(
    val success: Boolean? = null,
    val message: String? = null
)

@kotlinx.serialization.Serializable
data class Address(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val state: String? = null,
    val county: String? = null,
    val country: String? = null
) {
}

@kotlinx.serialization.Serializable
data class LocationDatas(
    val latitude: Double,
    val longitude: Double,
    val city: String?,
    val displayName: String,
    val state: String?,
    val country: String?

)

@Serializable
data class AuditLogRequest(
    val type: Int,
    val company_id: String,
    val user_id: String,
    val location_details: LocationDetailsPayload,
    val details: AuditDetails
)

@Serializable
data class LocationDetailsPayload(
    val lat: Double,
    val long: Double,
    val currentCity: String?,
    val state: String?
)

@Serializable
data class AuditDetails @OptIn(ExperimentalTime::class) constructor(
    val barcode: String,
    val status: String,
    val barcodeType: String,
    val device: String,
    val timestamp: String
)