package core.network.wms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

private val wmsJson = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

// ── Picking ───────────────────────────────────────────────────────────────────

@Serializable
data class WmsPickListStatusCounts(
    val assigned: Int? = null,
    @SerialName("in_progress") val inProgress: Int? = null,
    val picked: Int? = null,
    val staged: Int? = null,
    val completed: Int? = null,
)

@Serializable
data class WmsProductBatchRow(
    val gtin: String? = null,
    val batchNumber: String? = null,
    @SerialName("batch_number") val batchNumberSnake: String? = null,
) {
    val resolvedBatchNumber: String
        get() = batchNumber?.takeIf { it.isNotBlank() } ?: batchNumberSnake.orEmpty()
}

@Serializable
data class WmsOrderProduct(
    val productName: String? = null,
    @SerialName("product_name") val productNameSnake: String? = null,
    val productShortName: String? = null,
    @SerialName("product_short_name") val productShortNameSnake: String? = null,
    val productSku: String? = null,
    @SerialName("product_sku") val productSkuSnake: String? = null,
    val sku: String? = null,
    val gtin: String? = null,
    val identifier: String? = null,
    val batchNumber: String? = null,
    @SerialName("batch_number") val batchNumberSnake: String? = null,
    val batches: List<WmsProductBatchRow>? = null,
) {
    val resolvedName: String
        get() = listOfNotNull(productName, productNameSnake, productShortName, productShortNameSnake)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    val scanGtin: String
        get() = listOfNotNull(gtin, identifier, batches?.firstNotNullOfOrNull { it.gtin?.takeIf(String::isNotBlank) })
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    val scanBatchNumber: String
        get() = listOfNotNull(
            batchNumber,
            batchNumberSnake,
            batches?.firstNotNullOfOrNull { it.resolvedBatchNumber.takeIf(String::isNotBlank) },
        ).firstOrNull { it.isNotBlank() }.orEmpty()

    val resolvedSku: String
        get() = productSku?.takeIf { it.isNotBlank() }
            ?: productSkuSnake?.takeIf { it.isNotBlank() }
            ?: sku.orEmpty()
}

@Serializable
data class WmsPickListLine(
    val pickLineId: String? = null,
    val id: String? = null,
    val orderNumber: String? = null,
    val lineNumber: Int? = null,
    val productId: String? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val requestedQty: String? = null,
    val pickedQty: String? = null,
    val remainingQty: Int? = null,
    val status: String? = null,
    val locationCode: String? = null,
    val locationName: String? = null,
    val zone: String? = null,
    val bin: String? = null,
    val pickInstruction: String? = null,
    val sourceLocationId: String? = null,
    val product: WmsOrderProduct? = null,
) {
    val resolvedId: String
        get() = pickLineId?.takeIf { it.isNotBlank() } ?: id.orEmpty()

    fun resolvedRequestedQty(): Int {
        parseQty(requestedQty)?.takeIf { it > 0 }?.let { return it }
        remainingQty?.takeIf { it > 0 }?.let { return it }
        return 0
    }

    fun resolvedPickedQty(): Int = parseQty(pickedQty) ?: 0
    fun resolvedRemainingQty(): Int =
        remainingQty ?: maxOf(0, resolvedRequestedQty() - resolvedPickedQty())

    val displayName: String
        get() = listOfNotNull(
            product?.resolvedName?.takeIf { it.isNotBlank() },
            productName,
            product?.resolvedSku?.takeIf { it.isNotBlank() },
            productSku,
        ).firstOrNull { it.isNotBlank() } ?: "Line item"

    val locationLabel: String
        get() = displayLocation

    val displayLocation: String
        get() {
            locationCode?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val parts = listOfNotNull(zone, bin).filter { it.isNotBlank() }
            if (parts.isNotEmpty()) return parts.joinToString("-")
            lineNumber?.let { return "LINE-$it" }
            return "—"
        }

    val displaySku: String
        get() = (product?.resolvedSku ?: productSku)?.trim()?.takeIf { it.isNotEmpty() } ?: "—"

    val scanGtin: String
        get() = product?.scanGtin.orEmpty()

    val scanBatchNumber: String
        get() = product?.scanBatchNumber.orEmpty()
}

data class WmsPickListLinesPayload(
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val pickType: String? = null,
    val status: String? = null,
    val zone: String? = null,
    val toteNumber: String? = null,
    val assignedPicker: Int? = null,
    val orderId: String? = null,
    val stagingLocationId: String? = null,
    val defaultSourceLocationId: String? = null,
    val lineCount: Int? = null,
    val totalRequestedQty: Int? = null,
    val totalPickedQty: Int? = null,
    val pctComplete: Int? = null,
    val pickLines: List<WmsPickListLine>,
) {
    val displayTitle: String
        get() = pickListNumber?.takeIf { it.isNotBlank() } ?: pickListId.orEmpty()
}

@Serializable
data class WmsPickListItem(
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val pickType: String? = null,
    val status: String? = null,
    val zone: String? = null,
    val assignedPicker: Int? = null,
    val priority: Int? = null,
    val lineCount: Int? = null,
    val totalRequestedQty: Int? = null,
    val totalPickedQty: Int? = null,
    val waveId: String? = null,
    val waveNumber: String? = null,
    val assignedPickerName: String? = null,
    val toteNumber: String? = null,
    val stagingLocationId: String? = null,
    val assignedPacker: Int? = null,
    val assignedPackerName: String? = null,
    val packingOrderId: String? = null,
    val packingNumber: String? = null,
    val packingStatus: String? = null,
    val lines: List<WmsPickListLine>? = null,
    val pickLines: List<WmsPickListLine>? = null,
) {
    val id: String
        get() = pickListId?.takeIf { it.isNotBlank() }
            ?: pickListNumber?.takeIf { it.isNotBlank() }
            ?: orderId.orEmpty()

    val resolvedLines: List<WmsPickListLine>
        get() = lines?.takeIf { it.isNotEmpty() } ?: pickLines.orEmpty()

    val displayTitle: String
        get() = pickListNumber?.takeIf { it.isNotBlank() }
            ?: orderNumber?.takeIf { it.isNotBlank() }
            ?: "Pick list"

    val waveDisplayLabel: String?
        get() = waveNumber?.takeIf { it.isNotBlank() }?.let { "Wave $it" }

    val zoneCode: String? get() = zone
}

data class WmsPickListsPayload(
    val pickLists: List<WmsPickListItem>,
    val count: Int,
    val statusCounts: WmsPickListStatusCounts? = null,
)

data class PickerMyListTask(
    val id: String,
    val pickListId: String,
    val title: String,
    val lineCount: Int,
    val itemCount: Int,
    val pickedCount: Int,
    val status: String,
    val pickType: String? = null,
    val waveLabel: String? = null,
    val zone: String? = null,
    val toteNumber: String? = null,
    val locationLine: String,
    val isPriority: Boolean = false,
    val isLocked: Boolean = false,
    val lockReason: String? = null,
    val priority: Int = 5,
) {
    val progress: Float
        get() = if (itemCount <= 0) 0f else (pickedCount.toFloat() / itemCount).coerceIn(0f, 1f)

    val isPickedStatus: Boolean
        get() = status == "PICKED" || status == "STAGED"
}

// ── Packing ───────────────────────────────────────────────────────────────────

@Serializable
data class WmsPackingPickListLine(
    val pickLineId: String? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val requestedQty: Int? = null,
    val pickedQty: Int? = null,
    val packedQty: Int? = null,
    val remainingToPackQty: Int? = null,
    val packingStatus: String? = null,
    val pickLineStatus: String? = null,
    val status: String? = null,
) {
    val resolvedId: String
        get() = pickLineId?.takeIf { it.isNotBlank() }
            ?: "${productId ?: 0}-${productSku.orEmpty()}"

    /** Matches iOS `apiPickLineId` — used for add-to-box API calls. */
    val apiPickLineId: String
        get() = pickLineId?.trim()?.takeIf { it.isNotBlank() } ?: resolvedId

    val requiredCount: Int
        get() = maxOf(requestedQty ?: pickedQty ?: 0, 0)

    val packedCount: Int get() = maxOf(packedQty ?: 0, 0)

    val remainingToPackCount: Int
        get() = remainingToPackQty?.let { maxOf(it, 0) }
            ?: maxOf(requiredCount - packedCount, 0)

    fun linePackingStatusLabel(packedTotal: Int): String {
        packingStatus?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { return it }
        pickLineStatus?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { return it }
        when {
            packedTotal >= requiredCount && requiredCount > 0 -> return "PACKED"
            packedTotal > 0 -> return "PARTIAL"
        }
        return status?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "PENDING"
    }

    val isLineFullyPacked: Boolean
        get() = linePackingStatusLabel(packedCount) == "PACKED" ||
            (remainingToPackCount <= 0 && packedCount >= requiredCount && requiredCount > 0)
}

@Serializable
data class WmsPackingBoxSummary(
    val packId: String? = null,
    val packLabel: String? = null,
    val packType: String? = null,
    val hierarchyLevel: Int? = null,
    val sscc: String? = null,
    val status: String? = null,
    val itemCount: Int? = null,
    val totalPackedQty: Int? = null,
) {
    val resolvedPackId: String get() = packId.orEmpty()
    val isSecondaryPackage: Boolean get() = (hierarchyLevel ?: 2) == 2
    val isTertiaryPackage: Boolean get() = (hierarchyLevel ?: 0) == 3
    val isCompleted: Boolean
        get() = status?.uppercase() in setOf("COMPLETED", "SEALED", "PACKED")
    val isInProgress: Boolean
        get() = !isCompleted && resolvedPackId.isNotBlank()

    val displayTitle: String
        get() = packLabel?.trim()?.takeIf { it.isNotBlank() }
            ?: sscc?.trim()?.takeIf { it.isNotBlank() }
            ?: "Package"

    val resolvedSscc: String get() = sscc?.trim().orEmpty()

    fun packTypeUiLabel(): String =
        when {
            isTertiaryPackage || packType?.trim()?.uppercase() == "PALLET" -> "Tertiary"
            else -> "Secondary"
        }
}

@Serializable
data class WmsPackingPickListItem(
    val packingOrderId: String? = null,
    val packingNumber: String? = null,
    val packingStatus: String? = null,
    val status: String? = null,
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val toteNumber: String? = null,
    val stagingLocationName: String? = null,
    val lineCount: Int? = null,
    val totalRequestedQty: Int? = null,
    val totalPickedQty: Int? = null,
    val totalPackedQty: Int? = null,
    val totalRemainingToPackQty: Int? = null,
    val packingProgress: String? = null,
    val boxCount: Int? = null,
    val lines: List<WmsPackingPickListLine>? = null,
    val boxes: List<WmsPackingBoxSummary>? = null,
) {
    val id: String
        get() = packingOrderId?.takeIf { it.isNotBlank() }
            ?: pickListId?.takeIf { it.isNotBlank() }
            ?: packingNumber.orEmpty()

    val displayTitle: String
        get() = packingNumber?.takeIf { it.isNotBlank() }
            ?: pickListNumber?.takeIf { it.isNotBlank() }
            ?: "Packing list"

    val displayPackingStatusLabel: String
        get() = packingStatus?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "PENDING"

    val packLines: List<WmsPackingPickListLine> get() = lines.orEmpty()

    val resolvedTotalPackedQty: Int
        get() = totalPackedQty?.takeIf { it > 0 }
            ?: packLines.sumOf { it.packedCount }.takeIf { it > 0 }
            ?: 0

    val resolvedTotalRequestedQty: Int
        get() = totalRequestedQty?.takeIf { it > 0 }
            ?: totalPickedQty?.takeIf { it > 0 }
            ?: packLines.sumOf { it.requiredCount }.takeIf { it > 0 }
            ?: 0

    val isPackingQtyComplete: Boolean
        get() = packingProgress?.uppercase() == "FULLY_PACKED"
            || (totalRemainingToPackQty != null && totalRemainingToPackQty <= 0 && resolvedTotalPackedQty > 0)
            || (packLines.isNotEmpty() && packLines.all { it.isLineFullyPacked })
            || (resolvedTotalRequestedQty > 0 && resolvedTotalPackedQty >= resolvedTotalRequestedQty)

    val isPackingStatusPacked: Boolean get() = displayPackingStatusLabel == "PACKED"
    val isReadyToMarkPacked: Boolean get() = isPackingQtyComplete && !isPackingStatusPacked

    val isPackingInProgress: Boolean
        get() = displayPackingStatusLabel.replace(' ', '_') == "IN_PROGRESS"

    val resolvedInProgressBoxes: List<WmsPackingBoxSummary>
        get() = boxes.orEmpty().filter { it.isInProgress }
}

data class WmsPackingPickListsByPackerPayload(
    val packerId: Int?,
    val count: Int,
    val lists: List<WmsPackingPickListItem>,
)

@Serializable
data class WmsPackingBoxItem(
    val packItemId: String? = null,
    val pickLineId: String? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val quantity: Int? = null,
)

@Serializable
data class WmsPackingBoxNested(
    val packId: String? = null,
    val packLabel: String? = null,
    val packType: String? = null,
    val hierarchyLevel: Int? = null,
    val sscc: String? = null,
    val barcodeType: String? = null,
    val barcodeData: String? = null,
    val barcodeImageUrl: String? = null,
    val itemCount: Int? = null,
    val totalPackedQty: Int? = null,
    val status: String? = null,
    val items: List<WmsPackingBoxItem>? = null,
)

@Serializable
data class WmsCreatedPackingBox(
    val message: String? = null,
    val packId: String? = null,
    val packingOrderId: String? = null,
    val packageId: String? = null,
    val packLabel: String? = null,
    val packType: String? = null,
    val hierarchyLevel: Int? = null,
    val sscc: String? = null,
    val barcodeType: String? = null,
    val barcodeData: String? = null,
    val barcodeImageUrl: String? = null,
    val itemCount: Int? = null,
    val totalPackedQty: Int? = null,
    val status: String? = null,
    val items: List<WmsPackingBoxItem>? = null,
    val box: WmsPackingBoxNested? = null,
) {
    val resolvedPackId: String
        get() = packId?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.packId?.trim()?.takeIf { it.isNotBlank() }
            ?: ""

    val resolvedPackLabel: String
        get() = packLabel?.takeIf { it.isNotBlank() }
            ?: box?.packLabel?.takeIf { it.isNotBlank() }
            ?: "Box"

    val resolvedItems: List<WmsPackingBoxItem>
        get() = items?.takeIf { it.isNotEmpty() } ?: box?.items.orEmpty()

    val resolvedTotalPackedQty: Int
        get() = totalPackedQty
            ?: box?.totalPackedQty
            ?: resolvedItems.sumOf { it.quantity ?: 0 }

    val resolvedItemCount: Int
        get() = itemCount ?: box?.itemCount ?: resolvedItems.size

    val resolvedBarcodeType: String
        get() = barcodeType?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.barcodeType?.trim()?.takeIf { it.isNotBlank() }
            ?: "CODE128"

    val resolvedBarcodeData: String
        get() = barcodeData?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.barcodeData?.trim()?.takeIf { it.isNotBlank() }
            ?: sscc?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.sscc?.trim().orEmpty()

    val resolvedBarcodeImageUrl: String?
        get() = barcodeImageUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.barcodeImageUrl?.trim()?.takeIf { it.isNotBlank() }

    val resolvedPackageStatus: String
        get() = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: box?.status?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: if (resolvedPackId.isNotBlank()) "IN_PROGRESS" else ""

    val isPackageCompleted: Boolean
        get() = resolvedPackageStatus in setOf("COMPLETED", "SEALED", "PACKED")

    val isPackageInProgress: Boolean
        get() = resolvedPackId.isNotBlank() && !isPackageCompleted

    val hasPackageContents: Boolean
        get() = resolvedTotalPackedQty > 0 || resolvedItems.isNotEmpty()

    fun packedQtyForLine(pickLineId: String): Int {
        val key = pickLineId.trim()
        return resolvedItems
            .filter { (it.pickLineId?.trim() ?: "") == key }
            .sumOf { it.quantity ?: 0 }
    }
}

// ── Receiving ─────────────────────────────────────────────────────────────────

@Serializable
data class WmsPackingReceiverLine(
    val pickLineId: String? = null,
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val packedQty: Int? = null,
    val receivedQty: Int? = null,
    val remainingToReceiveQty: Int? = null,
    val receivingStatus: String? = null,
) {
    val id: String
        get() = pickLineId?.takeIf { it.isNotBlank() }
            ?: "${productId ?: 0}-${productSku.orEmpty()}"

    val displayName: String
        get() = productName?.takeIf { it.isNotBlank() } ?: productSku ?: "Line item"

    val resolvedPackedQty: Int get() = maxOf(packedQty ?: 0, 0)
    val resolvedReceivedQty: Int get() = maxOf(receivedQty ?: 0, 0)
    val resolvedRemainingQty: Int
        get() = remainingToReceiveQty ?: maxOf(0, resolvedPackedQty - resolvedReceivedQty)

    val resolvedPickLineId: String
        get() = pickLineId?.trim()?.takeIf { it.isNotBlank() } ?: id

    val displaySku: String
        get() = productSku?.trim()?.takeIf { it.isNotBlank() } ?: "—"

    val normalizedReceivingStatus: String
        get() = receivingStatus?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "PENDING"

    fun matchesPickList(pickList: WmsPackingReceiverNode): Boolean {
        val listId = pickList.pickListId?.trim().orEmpty()
        val listNumber = pickList.pickListNumber?.trim().orEmpty()
        if (listId.isNotEmpty() && pickListId?.trim() == listId) return true
        if (listNumber.isNotEmpty() && pickListNumber?.trim() == listNumber) return true
        return false
    }

    fun matchesTreeNode(node: WmsPackingReceiverNode): Boolean {
        val nodeLineId = node.pickLineId?.trim().orEmpty()
        if (nodeLineId.isNotEmpty() && nodeLineId == resolvedPickLineId) return true
        if (node.productId != null && productId != null && node.productId == productId) return true
        val nodeSku = node.productSku?.trim()?.lowercase().orEmpty()
        val lineSku = displaySku.lowercase()
        if (nodeSku.isNotEmpty() && nodeSku == lineSku && lineSku != "—") return true
        val nodeName = node.productName?.trim()?.lowercase().orEmpty()
        val lineName = displayName.lowercase()
        return nodeName.isNotEmpty() && nodeName == lineName
    }

    val isFullyReceived: Boolean
        get() = normalizedReceivingStatus == "RECEIVED"
            || (resolvedPackedQty > 0 && resolvedReceivedQty >= resolvedPackedQty)
}

@Serializable
data class WmsPackingReceiverReceivingStatus(
    val orderId: String? = null,
    val orderNumber: String? = null,
    val sessionStatus: String? = null,
    val totalLines: Int? = null,
    val receivedLines: Int? = null,
    val pendingLines: Int? = null,
    val partialLines: Int? = null,
    val totalPackedQty: Int? = null,
    val totalReceivedQty: Int? = null,
    val isComplete: Boolean? = null,
)

@Serializable
data class WmsPackingReceiverNode(
    val nodeType: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val customerName: String? = null,
    val orderStatus: String? = null,
    val receivingStatus: String? = null,
    val receivingSessionStatus: String? = null,
    val isReceivingComplete: Boolean? = null,
    val totalPackedQty: Int? = null,
    val totalReceivedQty: Int? = null,
    val totalPickedQty: Int? = null,
    val receivedQty: Int? = null,
    val packedLineCount: Int? = null,
    val receivedLineCount: Int? = null,
    val pendingLineCount: Int? = null,
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val pickLineId: String? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val requestedQty: Int? = null,
    val pickedQty: Int? = null,
    val packedQty: Int? = null,
    val packId: String? = null,
    val packLabel: String? = null,
    val packType: String? = null,
    val sscc: String? = null,
    val quantity: Int? = null,
    val packingStatus: String? = null,
    val packingNumber: String? = null,
    val toteNumber: String? = null,
    val stagingLocationName: String? = null,
    val packerName: String? = null,
    val assignedPacker: Int? = null,
    val status: String? = null,
    val hierarchyLevel: Int? = null,
    val boxCount: Int? = null,
    val packedPickListCount: Int? = null,
    val completedBoxCount: Int? = null,
    val totalBoxCount: Int? = null,
    val shippingMethod: String? = null,
    val children: List<WmsPackingReceiverNode>? = null,
) {
    val normalizedNodeType: String get() = nodeType?.trim()?.uppercase().orEmpty()

    val id: String
        get() = when (normalizedNodeType) {
            "ORDER" -> "order-${orderId ?: orderNumber.orEmpty()}"
            "PICK_LIST" -> "pick-${pickListId ?: pickListNumber.orEmpty()}"
            "LINE" -> "line-${pickListId.orEmpty()}-${productSku.orEmpty()}"
            "BOX" -> "box-${packId ?: sscc.orEmpty()}"
            else -> "node-${orderId.orEmpty()}-${pickListId.orEmpty()}"
        }

    val displayTitle: String
        get() = when (normalizedNodeType) {
            "ORDER" -> orderNumber ?: orderId ?: "Order"
            "PICK_LIST" -> pickListNumber ?: pickListId ?: "Pick list"
            "LINE" -> productName ?: productSku ?: "Line item"
            "PACK_ITEM" -> productName ?: productSku ?: "Pack item"
            "BOX" -> packLabel ?: sscc ?: "Box"
            else -> nodeType ?: "Node"
        }

    val displaySubtitle: String
        get() = when (normalizedNodeType) {
            "ORDER" -> customerName ?: orderStatus.orEmpty()
            "PICK_LIST" -> status.orEmpty().ifBlank { receivingStatus.orEmpty() }
            "LINE" -> {
                val sku = productSku?.trim().orEmpty()
                if (sku.isNotEmpty()) "SKU $sku" else packingStatus.orEmpty()
            }
            "BOX" -> {
                val type = packType?.trim().orEmpty()
                val code = sscc?.trim().orEmpty()
                when {
                    type.isNotEmpty() && code.isNotEmpty() -> "$type · $code"
                    code.isNotEmpty() -> code
                    type.isNotEmpty() -> type
                    else -> ""
                }
            }
            "PACK_ITEM" -> quantity?.let { "$it units" }.orEmpty()
            else -> productSku.orEmpty()
        }

    fun isReceivedForDisplay(): Boolean {
        if (isReceivingComplete == true) return true
        val status = receivingStatus?.uppercase().orEmpty()
        if (status in setOf("RECEIVED", "COMPLETE", "COMPLETED")) return true
        val session = receivingSessionStatus?.trim()?.uppercase().orEmpty()
        return session in setOf("COMPLETED", "RECEIVED")
    }

    val resolvedAPIOrderId: String?
        get() = orderId?.trim()?.takeIf { it.isNotBlank() }
            ?: orderNumber?.trim()?.takeIf { it.isNotBlank() }

    val displayReceivingStatusLabel: String
        get() {
            if (isReceivedForDisplay()) return "COMPLETED"
            val receiving = receivingStatus?.trim()?.uppercase()?.replace(' ', '_').orEmpty()
            if (receiving == "NOT_STARTED") return "NOT STARTED"
            if (receiving.isNotEmpty()) return receiving.replace('_', ' ')
            if ((totalPackedQty ?: 0) > 0) return "READY"
            return "PENDING"
        }

    val receivingProgressLabel: String?
        get() {
            val packed = totalPackedQty ?: return null
            if (packed <= 0) return null
            val received = totalReceivedQty ?: receivedQty ?: 0
            return "$received/$packed units"
        }

    val resolvedPackerName: String
        get() = packerName?.trim().orEmpty().ifBlank {
            assignedPacker?.let { "Packer #$it" }.orEmpty()
        }

    val resolvedToteNumber: String? get() = toteNumber?.trim()?.takeIf { it.isNotBlank() }
    val resolvedStagingLocationName: String? get() = stagingLocationName?.trim()?.takeIf { it.isNotBlank() }

    val resolvedPackType: String
        get() {
            val explicit = packType?.trim().orEmpty().uppercase()
            return if (explicit.isNotBlank()) {
                explicit
            } else {
                when (hierarchyLevel) {
                    3 -> "PALLET"
                    2 -> "CARTON"
                    else -> "CARTON"
                }
            }
        }

    val isCartonBox: Boolean
        get() = resolvedPackType in setOf("CARTON", "CASE", "INNER")

    val isPalletBox: Boolean
        get() = resolvedPackType == "PALLET" || hierarchyLevel == 3

    val pickListNodes: List<WmsPackingReceiverNode>
        get() = (children ?: emptyList()).filter { it.normalizedNodeType == "PICK_LIST" }

    val childLines: List<WmsPackingReceiverNode>
        get() = (children ?: emptyList()).filter { it.normalizedNodeType == "LINE" }

    val childBoxes: List<WmsPackingReceiverNode>
        get() = (children ?: emptyList()).filter { it.normalizedNodeType == "BOX" }

    val packItems: List<WmsPackingReceiverNode>
        get() = (children ?: emptyList()).filter { it.normalizedNodeType == "PACK_ITEM" }

    val boxProgressLabel: String?
        get() {
            val completed = completedBoxCount
            val total = totalBoxCount
            return if (completed != null && total != null && total > 0) {
                "$completed/$total boxes"
            } else null
        }
}

// ── Request bodies ────────────────────────────────────────────────────────────

@Serializable
data class WmsAssignPickListRequest(
    val pickerId: Int,
    val zone: String,
    val toteNumber: String? = null,
)

@Serializable
data class WmsPatchPickListLineUpdate(
    val pickLineId: String,
    val pickedQty: Int? = null,
    val sourceLocationId: String? = null,
    val status: String? = null,
)

@Serializable
data class WmsPatchPickListLinesRequest(
    val companyId: Int,
    val lines: List<WmsPatchPickListLineUpdate>,
)

@Serializable
data class WmsStagePickListRequest(
    val companyId: Int,
    val stagingLocationId: String? = null,
    val stagingLocationName: String? = null,
)

@Serializable
data class WmsAssignPackerRequest(
    val packerId: Int,
    val companyId: Int,
)

@Serializable
data class WmsCreatePackingBoxRequest(
    val companyId: Int,
    val packingOrderId: String,
    val packLabel: String,
    val packType: String,
    val hierarchyLevel: Int,
    val gs1CompanyPrefix: String = "0614141",
    // iOS default used by WMSApi.createPackingBox
    val capacityUnits: Int = 25,
    val createdBy: Int,
    val generateBarcodeImage: Boolean = true,
)

@Serializable
data class WmsAddPackingBoxItemRequest(
    val companyId: Int,
    val productId: Int,
    val quantity: Int,
    val packedBy: Int,
    val pickLineId: String,
)

@Serializable
data class WmsUpdatePackingBoxStatusRequest(
    val companyId: Int,
    val status: String,
    val packedBy: Int? = null,
)

@Serializable
data class WmsUpdatePackingPickListStatusRequest(
    val companyId: Int,
    val status: String,
    val packedBy: Int? = null,
)

@Serializable
data class WmsPackingReceiverVerifyLineUpdate(
    val pickLineId: String,
    val receivedQty: Int,
)

@Serializable
data class WmsPackingReceiverVerifyLinesRequest(
    val companyId: Int,
    val receiverId: Int,
    val lines: List<WmsPackingReceiverVerifyLineUpdate>,
)

@Serializable
data class WmsPackingReceiverCompleteRequest(
    val companyId: Int,
    val receiverId: Int,
    val notes: String? = null,
    val requireLineQtyMatch: Boolean = true,
    val requireBoxScan: Boolean = false,
)

@Serializable
data class WmsUser(
    val id: Int = 0,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val workStatus: String? = null,
    val isActive: Boolean? = true,
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(firstName, lastName).joinToString(" ").takeIf { it.isNotBlank() }
            ?: email ?: "User"
}

// ── JSON helpers ──────────────────────────────────────────────────────────────

internal fun decodePickListsPayload(raw: String): WmsPickListsPayload {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        val lists = element.mapNotNull { decodePickListItem(it) }
        return WmsPickListsPayload(lists, lists.size, null)
    }
    val obj = element.jsonObject
    val listsArray = obj.pickArray("lists", "pickLists", "data")
    val lists = listsArray.mapNotNull { decodePickListItem(it) }
    val counts = obj["statusCounts"]?.let {
        runCatching { wmsJson.decodeFromJsonElement(WmsPickListStatusCounts.serializer(), it) }.getOrNull()
    }
    return WmsPickListsPayload(
        pickLists = lists,
        count = obj["count"]?.jsonPrimitive?.intOrNull ?: lists.size,
        statusCounts = counts,
    )
}

internal fun decodePackingByPackerPayload(raw: String): WmsPackingPickListsByPackerPayload {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        val lists = element.mapNotNull { decodePackingPickListItem(it) }
        return WmsPackingPickListsByPackerPayload(null, lists.size, lists)
    }
    val obj = element.jsonObject
    val listsArray = obj.pickArray("lists", "pickLists", "data")
    val lists = listsArray.mapNotNull { decodePackingPickListItem(it) }
    return WmsPackingPickListsByPackerPayload(
        packerId = obj["packerId"]?.jsonPrimitive?.intOrNull,
        count = obj["count"]?.jsonPrimitive?.intOrNull ?: lists.size,
        lists = lists,
    )
}

internal fun decodeReceiverOrders(raw: String): List<WmsPackingReceiverNode> {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return element.mapNotNull { decodeReceiverNode(it) }
    }
    val obj = element.jsonObject
    val array = obj.pickArray("orders", "data", "nodes")
    return array.mapNotNull { decodeReceiverNode(it) }
}

internal fun decodeReceiverLines(raw: String): List<WmsPackingReceiverLine> {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return element.mapNotNull {
            runCatching { wmsJson.decodeFromJsonElement(WmsPackingReceiverLine.serializer(), it) }.getOrNull()
        }
    }
    val obj = element.jsonObject
    return obj.pickArray("lines", "data").mapNotNull {
        runCatching { wmsJson.decodeFromJsonElement(WmsPackingReceiverLine.serializer(), it) }.getOrNull()
    }
}

internal fun decodeReceiverReceivingStatus(raw: String): WmsPackingReceiverReceivingStatus {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonObject) {
        val nested = element["status"] ?: element["data"]
        if (nested != null) {
            runCatching {
                return wmsJson.decodeFromJsonElement(WmsPackingReceiverReceivingStatus.serializer(), nested)
            }
        }
        runCatching {
            return wmsJson.decodeFromJsonElement(WmsPackingReceiverReceivingStatus.serializer(), element)
        }
    }
    return wmsJson.decodeFromString(WmsPackingReceiverReceivingStatus.serializer(), raw)
}

internal fun decodePackingBoxes(raw: String): List<WmsPackingBoxSummary> {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return element.mapNotNull {
            runCatching { wmsJson.decodeFromJsonElement(WmsPackingBoxSummary.serializer(), it) }.getOrNull()
        }
    }
    val obj = element.jsonObject
    return obj.pickArray("boxes", "data").mapNotNull {
        runCatching { wmsJson.decodeFromJsonElement(WmsPackingBoxSummary.serializer(), it) }.getOrNull()
    }
}

/** Matches iOS `decodePackingBoxResponse(from:)` envelope + nested `box` handling. */
internal fun decodePackingBox(raw: String): WmsCreatedPackingBox {
    val element = wmsJson.parseToJsonElement(raw)
    decodePackingBoxElement(element)?.let { return it }
    return wmsJson.decodeFromString(WmsCreatedPackingBox.serializer(), raw)
}

private fun decodePackingBoxElement(element: JsonElement): WmsCreatedPackingBox? {
    if (element is JsonObject) {
        element["data"]?.let { decodePackingBoxElement(it) }?.let { return it }

        val decoded = runCatching {
            wmsJson.decodeFromJsonElement(WmsCreatedPackingBox.serializer(), element)
        }.getOrNull() ?: return null

        if (decoded.resolvedPackId.isNotBlank()) return decoded

        val nested = element["box"]?.let {
            runCatching { wmsJson.decodeFromJsonElement(WmsPackingBoxNested.serializer(), it) }.getOrNull()
        } ?: return decoded

        return decoded.copy(
            packId = nested.packId ?: decoded.packId,
            packLabel = decoded.packLabel ?: nested.packLabel,
            packType = decoded.packType ?: nested.packType,
            hierarchyLevel = decoded.hierarchyLevel ?: nested.hierarchyLevel,
            sscc = decoded.sscc ?: nested.sscc,
            barcodeType = decoded.barcodeType ?: nested.barcodeType,
            barcodeData = decoded.barcodeData ?: nested.barcodeData,
            barcodeImageUrl = decoded.barcodeImageUrl ?: nested.barcodeImageUrl,
            itemCount = decoded.itemCount ?: nested.itemCount,
            totalPackedQty = decoded.totalPackedQty ?: nested.totalPackedQty,
            status = decoded.status ?: nested.status,
            items = decoded.items?.takeIf { it.isNotEmpty() } ?: nested.items,
            box = nested,
        )
    }

    return runCatching {
        wmsJson.decodeFromJsonElement(WmsCreatedPackingBox.serializer(), element)
    }.getOrNull()
}

internal fun decodeUsers(raw: String): List<WmsUser> {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return element.mapNotNull {
            runCatching { wmsJson.decodeFromJsonElement(WmsUser.serializer(), it) }.getOrNull()
        }
    }
    val obj = element.jsonObject
    return obj.pickArray("users", "data").mapNotNull {
        runCatching { wmsJson.decodeFromJsonElement(WmsUser.serializer(), it) }.getOrNull()
    }
}

private fun JsonObject.pickArray(vararg keys: String): JsonArray {
    for (key in keys) {
        val arr = this[key] as? JsonArray
        if (arr != null) return arr
    }
    return JsonArray(emptyList())
}

private fun JsonObject.optionalInt(vararg keys: String): Int? {
    for (key in keys) {
        val prim = this[key]?.jsonPrimitive ?: continue
        prim.intOrNull?.let { return it }
        prim.contentOrNull?.trim()?.toIntOrNull()?.let { return it }
        prim.contentOrNull?.trim()?.toDoubleOrNull()?.let { return it.roundToInt() }
    }
    return null
}

private fun JsonObject.optionalString(vararg keys: String): String? {
    for (key in keys) {
        val el = this[key] ?: continue
        if (el is JsonNull) continue
        val prim = el.jsonPrimitive
        val value = prim.contentOrNull?.trim().orEmpty()
        if (value.isNotEmpty()) return value
    }
    return null
}

private fun JsonObject.optionalQtyString(vararg keys: String): String? {
    for (key in keys) {
        val el = this[key] ?: continue
        if (el is JsonNull) continue
        val prim = el.jsonPrimitive
        prim.intOrNull?.let { return it.toString() }
        prim.longOrNull?.let { return it.toString() }
        prim.doubleOrNull?.let { return it.roundToInt().toString() }
        prim.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

private fun parseQty(raw: String?): Int? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    trimmed.toIntOrNull()?.let { return it }
    trimmed.toDoubleOrNull()?.let { return it.roundToInt() }
    return null
}

private fun decodeOrderProduct(obj: JsonObject): WmsOrderProduct {
    val batches = (obj["batches"] as? JsonArray)?.mapNotNull { batchEl ->
        val batchObj = batchEl as? JsonObject ?: return@mapNotNull null
        WmsProductBatchRow(
            gtin = batchObj.optionalString("gtin"),
            batchNumber = batchObj.optionalString("batchNumber", "batch_number"),
            batchNumberSnake = batchObj.optionalString("batch_number"),
        )
    }
    return WmsOrderProduct(
        productName = obj.optionalString("productName", "product_name", "name"),
        productNameSnake = obj.optionalString("product_name"),
        productShortName = obj.optionalString("productShortName", "product_short_name", "shortName"),
        productShortNameSnake = obj.optionalString("product_short_name"),
        productSku = obj.optionalString("productSku", "product_sku"),
        productSkuSnake = obj.optionalString("product_sku"),
        sku = obj.optionalString("sku"),
        gtin = obj.optionalString("gtin"),
        identifier = obj.optionalString("identifier"),
        batchNumber = obj.optionalString("batchNumber", "batch_number"),
        batchNumberSnake = obj.optionalString("batch_number"),
        batches = batches,
    )
}

private fun decodePickListLine(element: JsonElement): WmsPickListLine? {
    val obj = element as? JsonObject ?: return null
    val location = obj.optionalNestedObject("location", "sourceLocation")
    val productObj = obj.optionalNestedObject("product")
    val orderObj = obj.optionalNestedObject("order")
    val orderLineObj = obj.optionalNestedObject("orderLine", "order_line")
    val product = productObj?.let(::decodeOrderProduct)

    val productName = obj.optionalString("productName", "product_name", "name")
        ?: product?.resolvedName?.takeIf { it.isNotBlank() }
    val productSku = obj.optionalString("productSku", "product_sku", "sku")
        ?: product?.resolvedSku?.takeIf { it.isNotBlank() }

    val lineId = obj.optionalString(
        "pickLineId",
        "pick_line_id",
        "id",
        "taskId",
        "orderLineId",
        "order_line_id",
    ) ?: return null

    return WmsPickListLine(
        pickLineId = lineId,
        id = obj.optionalString("id", "taskId"),
        orderNumber = obj.optionalString("orderNumber", "order_number", "sourceOrderRef")
            ?: orderObj?.optionalString("orderNumber", "order_number"),
        lineNumber = obj.optionalInt("lineNumber", "line_number", "sequence", "sequenceNumber")
            ?: orderLineObj?.optionalInt("lineNumber", "line_number"),
        productId = obj.optionalString("productId", "product_id"),
        productName = productName,
        productSku = productSku,
        requestedQty = obj.optionalQtyString(
            "requestedQty",
            "requested_qty",
            "quantity",
            "qty",
            "requiredQty",
            "required_qty",
        ),
        pickedQty = obj.optionalQtyString("pickedQty", "picked_qty"),
        remainingQty = obj.optionalInt("remainingQty", "remaining_qty"),
        status = obj.optionalString("status"),
        locationCode = obj.optionalString("locationCode", "location_code")
            ?: location?.optionalString("locationCode", "location_code"),
        locationName = obj.optionalString("locationName", "location_name")
            ?: location?.optionalString("locationName", "location_name"),
        zone = obj.optionalString("zone") ?: location?.optionalString("zone"),
        bin = obj.optionalString("bin") ?: location?.optionalString("bin"),
        pickInstruction = obj.optionalString("pickInstruction", "pick_instruction", "instruction"),
        sourceLocationId = obj.optionalString("sourceLocationId", "source_location_id")
            ?: location?.optionalString("locationId", "id"),
        product = product,
    )
}

internal fun decodePickListLinesPayload(raw: String): WmsPickListLinesPayload {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return WmsPickListLinesPayload(
            pickLines = element.mapNotNull(::decodePickListLine),
        )
    }
    val root = element.jsonObject
    for (key in listOf("data", "pickList", "list")) {
        root[key]?.jsonObject?.let { nested ->
            val payload = decodePickListLinesPayloadObject(nested, root)
            if (payload.pickLines.isNotEmpty()) return payload
        }
    }
    return decodePickListLinesPayloadObject(root, root)
}

private fun decodePickListLinesPayloadObject(obj: JsonObject, root: JsonObject): WmsPickListLinesPayload {
    val nestedHeader = obj.optionalNestedObject("pickList", "list")
    var linesArray = obj.pickArray("lines", "pickLines", "tasks", "pickTasks")
    if (linesArray.isEmpty()) {
        linesArray = nestedHeader?.pickArray("lines", "pickLines", "tasks", "pickTasks") ?: JsonArray(emptyList())
    }
    if (linesArray.isEmpty()) {
        linesArray = root.pickArray("lines", "pickLines", "tasks", "pickTasks")
    }
    val header = nestedHeader ?: obj
    val lines = linesArray.mapNotNull(::decodePickListLine)
    return WmsPickListLinesPayload(
        pickListId = header.optionalString("pickListId", "pick_list_id", "id")
            ?: obj.optionalString("pickListId", "pick_list_id", "id"),
        pickListNumber = header.optionalString("pickListNumber", "pick_list_number")
            ?: obj.optionalString("pickListNumber", "pick_list_number"),
        pickType = header.optionalString("pickType", "pick_type") ?: obj.optionalString("pickType", "pick_type"),
        status = header.optionalString("status") ?: obj.optionalString("status"),
        zone = header.optionalString("zone") ?: obj.optionalString("zone"),
        toteNumber = header.optionalString("toteNumber", "tote_number") ?: obj.optionalString("toteNumber", "tote_number"),
        assignedPicker = header.optionalInt("assignedPicker", "assigned_picker")
            ?: obj.optionalInt("assignedPicker", "assigned_picker"),
        orderId = header.optionalString("orderId", "order_id") ?: obj.optionalString("orderId", "order_id"),
        stagingLocationId = header.optionalString("stagingLocationId", "staging_location_id")
            ?: obj.optionalString("stagingLocationId", "staging_location_id"),
        defaultSourceLocationId = header.optionalString("defaultSourceLocationId", "default_source_location_id")
            ?: obj.optionalString("defaultSourceLocationId", "default_source_location_id"),
        lineCount = header.optionalInt("lineCount", "line_count")
            ?: obj.optionalInt("lineCount", "line_count"),
        totalRequestedQty = header.optionalInt("totalRequestedQty", "total_requested_qty")
            ?: obj.optionalInt("totalRequestedQty", "total_requested_qty"),
        totalPickedQty = header.optionalInt("totalPickedQty", "total_picked_qty")
            ?: obj.optionalInt("totalPickedQty", "total_picked_qty"),
        pctComplete = header.optionalInt("pctComplete", "pct_complete")
            ?: obj.optionalInt("pctComplete", "pct_complete"),
        pickLines = lines,
    )
}

private fun JsonObject.optionalNestedObject(vararg keys: String): JsonObject? {
    for (key in keys) {
        val obj = this[key]?.jsonObject
        if (obj != null) return obj
    }
    return null
}

private fun decodePickListItem(element: JsonElement): WmsPickListItem? {
    val base = runCatching { wmsJson.decodeFromJsonElement(WmsPickListItem.serializer(), element) }.getOrNull()
        ?: return null
    if (base.resolvedLines.isNotEmpty()) return base
    val obj = element.jsonObject
    val sidecar = obj.pickArray("lines", "pickLines", "tasks", "pickTasks")
    if (sidecar.isEmpty()) return base
    val lines = sidecar.mapNotNull(::decodePickListLine)
    return base.copy(lines = lines)
}

private fun decodePackingPickListItem(element: JsonElement): WmsPackingPickListItem? =
    runCatching { wmsJson.decodeFromJsonElement(WmsPackingPickListItem.serializer(), element) }.getOrNull()

private fun decodeReceiverNode(element: JsonElement): WmsPackingReceiverNode? =
    runCatching { wmsJson.decodeFromJsonElement(WmsPackingReceiverNode.serializer(), element) }.getOrNull()

fun packingBoxTypeConfig(uiLabel: String): Pair<String, Int> =
    // Must match iOS WMSApi.packingBoxTypeConfig(for:)
    // - Secondary/Carton/Case/Inner => (CARTON, level=2)
    // - Tertiary/Pallet/Master => (PALLET, level=3)
    when (uiLabel.trim().uppercase()) {
        "TERTIARY", "PALLET", "MASTER" -> "PALLET" to 3
        "SECONDARY", "CARTON", "CASE", "INNER" -> "CARTON" to 2
        else -> "CARTON" to 2
    }

fun mapPickListToTask(item: WmsPickListItem): PickerMyListTask {
    val status = (item.status ?: "PENDING").uppercase()
    val lines = item.lineCount ?: item.resolvedLines.size
    val total = item.totalRequestedQty ?: lines
    val picked = item.totalPickedQty ?: 0
    val locationParts = buildList {
        item.zoneCode?.takeIf { it.isNotBlank() }?.let { add("Zone $it") }
        item.orderNumber?.takeIf { it.isNotBlank() }?.let { add("Order $it") }
    }
    return PickerMyListTask(
        id = item.id,
        pickListId = item.pickListId ?: item.id,
        title = item.displayTitle,
        lineCount = lines,
        itemCount = total,
        pickedCount = picked,
        status = status,
        pickType = item.pickType,
        waveLabel = item.waveDisplayLabel,
        zone = item.zoneCode,
        toteNumber = item.toteNumber?.takeIf { it.isNotBlank() },
        locationLine = locationParts.joinToString(" • ").ifBlank { "Warehouse floor" },
        isLocked = status == "PENDING" && item.assignedPicker == null,
        lockReason = if (status == "PENDING") "Wait for assignment" else null,
        priority = item.priority ?: 5,
    )
}
