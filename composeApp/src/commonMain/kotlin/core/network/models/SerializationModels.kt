package core.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class VrsVerifyRequest(
    val epcUri: String,
    val companyId: Int,
    val gtin: String? = null,
    val serial: String? = null,
    val batch: String? = null,
)

@Serializable
data class VrsVerifyResponse(
    val status: String? = null,
    val verified: Boolean? = null,
    val message: String? = null,
    val epcUri: String? = null,
    val gtin: String? = null,
    val serial: String? = null,
    val batch: String? = null,
    val expiryDate: String? = null,
    val recallStatus: String? = null,
) {
    val displayStatus: String
        get() = when {
            !status.isNullOrBlank() -> status.uppercase()
            verified == true -> "VERIFIED"
            verified == false -> "UNKNOWN"
            else -> "UNKNOWN"
        }
}

@Serializable
data class SerialMasterResponse(
    val epcUri: String? = null,
    val gtin: String? = null,
    val serial: String? = null,
    val batch: String? = null,
    val status: String? = null,
    val companyId: Int? = null,
)

@Serializable
data class EventLineageResponse(
    val epcUri: String? = null,
    val events: List<EpcisEventDto> = emptyList(),
)

@Serializable
data class EpcisEventDto(
    val eventTime: String? = null,
    val bizStep: String? = null,
    val disposition: String? = null,
    val readPoint: String? = null,
    val bizLocation: String? = null,
    val action: String? = null,
    val extra: JsonElement? = null,
)

@Serializable
data class GenerateBarcodeRequest(
    val companyId: Int,
    val gtin: String,
    val serialised: Boolean = true,
    val bcNos: Int,
    val framework: String = "CDSCO",
    val marketCode: String = "IN",
    val batch: String? = null,
    val expiryDate: String? = null,
)

@Serializable
data class GenerateBarcodeResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val barcodes: List<String> = emptyList(),
)

@Serializable
data class SerialPoolDto(
    val id: String? = null,
    val gtin: String? = null,
    val poolName: String? = null,
    val availableCount: Int? = null,
    val companyId: Int? = null,
)

// ── EPCIS write payloads (TrackandTrace-backend serialization module) ─────────

@Serializable
data class CommissionSerialRequest(
    val companyId: Int,
    val gtin: String,
    val serialNumber: String,
    val batchNumber: String? = null,
    val framework: String = "GENERIC",
    val marketCode: String = "IN",
    val productionOrder: String? = null,
)

@Serializable
data class CommissionSerialResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val epcUri: String? = null,
)

@Serializable
data class AggregateSerialRequest(
    val companyId: Int,
    val parentEpcUri: String,
    val childEpcUris: List<String>,
    val levelOrigin: String,
    val framework: String = "GENERIC",
    val containerSealed: Boolean = true,
)

@Serializable
data class AggregateSerialResponse(
    val success: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class EpcisEventEpc(
    val epcUri: String,
    val epcRole: String = "epc",
)

@Serializable
data class EpcisFlowEventRequest(
    val companyId: Int,
    val flow: String,
    val framework: String = "GENERIC",
    val epcs: List<EpcisEventEpc>,
    val sourceGln: String? = null,
    val destinationGln: String? = null,
    val bizTransactionId: String? = null,
    val bizTransactionType: String? = null,
    val receivingConfirmed: Boolean? = null,
)

@Serializable
data class EpcisFlowEventResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val eventId: String? = null,
)

@Serializable
data class DispenseSerialRequest(
    val companyId: Int,
    val epcUri: String,
    val destinationGln: String,
    val framework: String = "CDSCO",
    val marketCode: String = "IN",
)

@Serializable
data class DispenseSerialResponse(
    val success: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class RecallSerialRequest(
    val companyId: Int,
    val gtin: String,
    val batchNumber: String,
    val recallReference: String,
    val affectedQuantity: Int,
    val framework: String = "CDSCO",
    val marketCode: String = "IN",
)

@Serializable
data class RecallSerialResponse(
    val success: Boolean? = null,
    val message: String? = null,
)

// ── GTIN verification (productmaster lookup) ──────────────────────────────────

@Serializable
data class GtinLookupRequest(
    val companyId: Int,
    val gtin: String,
)

@Serializable
data class GtinLookupResponse(
    val exists: Boolean? = null,
    val found: Boolean? = null,
    val gtin: String? = null,
    val productName: String? = null,
    val productId: String? = null,
    val message: String? = null,
) {
    val isRegistered: Boolean get() = exists == true || found == true
}

// ── L2 Pack aggregate (GTIN + serials) ───────────────────────────────────────

@Serializable
data class EpcisL2PackRequest(
    val companyId: Int,
    val parentEpcUri: String,
    val gtin: String,
    val serials: List<String>,
    val containerSealed: Boolean = false,
    val bizLocationGln: String? = null,
)

@Serializable
data class EpcisL2PackResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val eventId: String? = null,
)

// ── L3 Aggregate (cartons onto pallet, items with GTIN+serial+batch) ──────────

@Serializable
data class EpcisL3AggregateItem(
    val gtin: String,
    val serial: String,
    val batch: String? = null,
)

@Serializable
data class EpcisL3AggregateRequest(
    val companyId: Int,
    val parentEpcUri: String,
    val items: List<EpcisL3AggregateItem>,
    val levelOrigin: String = "L3",
    val containerSealed: Boolean = true,
    val framework: String = "GENERIC",
    val bizLocationGln: String? = null,
    val rejectUnknownProduct: Boolean = false,
)

@Serializable
data class EpcisL3AggregateResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val eventId: String? = null,
)

// ── L3 Ship (dispatch shipment) ──────────────────────────────────────────────

@Serializable
data class L3ShipmentProduct(
    val gtin: String,
    val serials: List<String>,
    val batch: String? = null,
)

@Serializable
data class L3ShipmentRequest(
    val companyId: Int,
    val sourceGln: String,
    val destinationGln: String,
    val bizTransactionId: String,
    val products: List<L3ShipmentProduct>,
    val bizLocationGln: String? = null,
    val framework: String? = null,
)

@Serializable
data class L3ShipmentResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val eventId: String? = null,
)

// ── L4 Verify (receipt verification) ─────────────────────────────────────────

@Serializable
data class L4VerifyRequest(
    val companyId: Int,
    val bizTransactionId: String,
    val gtin: String,
    val serial: String,
)

@Serializable
data class L4VerifyItem(
    val gtin: String? = null,
    val serial: String? = null,
    @SerialName("epcUri") val epcUri: String? = null,
    @SerialName("epcType") val epcType: String? = null,
)

@Serializable
data class L4L3Shipment(
    @SerialName("ship_event_id") val shipEventId: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("shipped_at") val shippedAt: String? = null,
    @SerialName("source_gln") val sourceGln: String? = null,
    @SerialName("destination_gln") val destinationGln: String? = null,
    @SerialName("level_origin") val levelOrigin: String? = null,
)

@Serializable
data class L4ReceiptProgress(
    @SerialName("shippedCount") val shippedCount: Int? = null,
    @SerialName("receivedCount") val receivedCount: Int? = null,
    @SerialName("pendingCount") val pendingCount: Int? = null,
    @SerialName("complete") val complete: Boolean? = null,
)

@Serializable
data class L4VerifyResponse(
    val companyId: Int? = null,
    @SerialName("bizTransactionId") val bizTransactionId: String? = null,
    val level: String? = null,
    @SerialName("l3Shipped") val l3Shipped: Boolean? = null,
    @SerialName("productFound") val productFound: Boolean? = null,
    @SerialName("alreadyReceived") val alreadyReceived: Boolean? = null,
    @SerialName("readyForL4Receive") val readyForL4Receive: Boolean? = null,
    val message: String? = null,
    val item: L4VerifyItem? = null,
    @SerialName("l3Shipment") val l3Shipment: L4L3Shipment? = null,
    val progress: L4ReceiptProgress? = null,
)

// ── L4 Receive (item receipt) ────────────────────────────────────────────────

@Serializable
data class L4ReceiveRequest(
    val companyId: Int,
    val gtin: String,
    @SerialName("userId") val userId: Int,
    @SerialName("bizTransactionId") val bizTransactionId: String? = null,
    @SerialName("sourceGln") val sourceGln: String? = null,
    @SerialName("destinationGln") val destinationGln: String? = null,
    val serial: String? = null,
    val batch: String? = null,
    @SerialName("scannerId") val scannerId: String? = null,
    @SerialName("receivingConfirmed") val receivingConfirmed: Boolean? = null,
    @SerialName("framework") val framework: String? = null,
    @SerialName("bizLocationGln") val bizLocationGln: String? = null,
    @SerialName("verifyAgainstShipment") val verifyAgainstShipment: Boolean? = null,
)

@Serializable
data class L4ReceiveResponse(
    val level: String? = null,
    val flow: String? = null,
    val verified: Boolean? = null,
    val progress: L4ReceiptProgress? = null,
)

// ── L3 By GTIN (fetch L3 shipment details before L4 receive) ────────────────

@Serializable
data class L3ByGtinResponse(
    val companyId: Int? = null,
    val gtin: String? = null,
    val count: Int? = null,
    val shipments: List<L3ByGtinShipment>? = null,
)

@Serializable
data class L3ByGtinShipment(
    val id: Int? = null,
    @SerialName("eventId") val eventId: String? = null,
    @SerialName("bizTransactionId") val bizTransactionId: String? = null,
    @SerialName("sourceGln") val sourceGln: String? = null,
    @SerialName("destinationGln") val destinationGln: String? = null,
    @SerialName("epcId") val epcId: String? = null,
    val serial: String? = null,
    val batch: String? = null,
    @SerialName("levelOrigin") val levelOrigin: String? = null,
    @SerialName("shippedAt") val shippedAt: String? = null,
)
