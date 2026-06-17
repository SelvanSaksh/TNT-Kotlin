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
    val display_name: String = "",
    val address: Address = Address(),
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

@Serializable
data class ScanLogCreateRequest(
    val event_type: String,
    val epc_id: String,
    val event_time: String,
    val biz_step: String = "inspecting",
    val biz_location: String = "urn:epc:id:sgln:0001234.00000.0",
    val geo_location: String,
    val auth_result: String,
    val scanner_id: String,
    val signature: String = "0xandroid",
    val company_id: Int,
    val user_id: Int = 0,
    val lat: Double = 0.0,
    val long: Double = 0.0,
    val barcode_type: String = "QR",
    val barcode_data: String = "",
    val device_type: String = "android",
    val serial: String = "",
    val batch: String = "",
    val gtin: String = "",
)

@Serializable
data class GenerationLogRequest(
    val barcode_type: String,
    val barcode_data: String,
    val company_id: Int,
    val user_id: Int = 0,
    val event_id: String,
    val event_time: String,
    val epc_id: String = "",
    val biz_step: String = "encoding",
    val biz_location: String = "urn:epc:id:sgln:0001234.00000.0",
    val geo_location: String = "Unknown",
    val auth_result: String = "AUTHENTIC",
    val scanner_id: String = "",
    val signature: String = "0xandroid",
    val lat: Double = 0.0,
    val long: Double = 0.0,
    val device_type: String = "android",
    val serial: String = "",
    val batch: String = "",
)