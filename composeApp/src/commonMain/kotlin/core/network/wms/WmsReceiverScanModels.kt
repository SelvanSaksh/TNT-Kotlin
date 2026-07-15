package core.network.wms

import kotlinx.serialization.Serializable

@Serializable
data class WmsPackingReceiverScanPackRequest(
    val companyId: Int,
    val receiverId: Int,
    val sscc: String? = null,
    val packId: String? = null,
    val epcUri: String? = null,
    val barcodeData: String? = null,
    val deviceType: String = "android",
)

@Serializable
data class WmsPackingReceiverScanPackResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val orderId: String? = null,
    val packId: String? = null,
    val sscc: String? = null,
    val matchedLines: Int? = null,
    val receivedQty: Int? = null,
)
