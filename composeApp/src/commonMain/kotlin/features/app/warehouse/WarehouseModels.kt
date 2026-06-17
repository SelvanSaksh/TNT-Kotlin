package features.app.warehouse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

object WarehouseHomeConfig {
    const val restrictToUserModules = true
}

object WarehouseFlowConfig {
    // Live WMS /wms/ APIs (same as iOS floor flows).
    const val useWmsApi = true
    const val useMockPicking = false
    const val useMockPacking = false
    const val useMockReceiving = false
}

data class WarehouseModuleItem(
    val id: Int,
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
)

enum class WarehouseRoute {
    Picking,
    Packing,
    Receiving,
}

fun resolveWarehouseModuleItem(name: String): WarehouseModuleItem? {
    val normalized = name.trim()
    if (normalized.isEmpty()) return null
    return when {
        normalized.contains("picking", ignoreCase = true) -> defaultWarehouseModules[0].copy(name = normalized)
        normalized.contains("packing", ignoreCase = true) -> defaultWarehouseModules[1].copy(name = normalized)
        normalized.contains("receiving", ignoreCase = true) -> defaultWarehouseModules[2].copy(name = normalized)
        else -> null
    }
}

enum class PickingListMode(val label: String) {
    Orders("Orders"),
    PickList("Pick list"),
    Products("Products"),
}

data class PickingAppOrder(
    val id: Int,
    val orderNo: String,
    val zone: String,
    val status: String,
    val items: List<PickingAppItem>,
) {
    val isPending: Boolean get() = status.equals("pending", ignoreCase = true)
}

data class PickingAppItem(
    val productId: Int,
    val name: String,
    val sku: String,
    val batch: String,
    val gtin: String,
    val bin: String,
    val pickedQty: Int,
    val totalQty: Int,
) {
    val id: String get() = "${productId}_${sku}_$batch"
    val isComplete: Boolean get() = totalQty > 0 && pickedQty >= totalQty
}

data class PickingProductGroup(
    val id: String,
    val name: String,
    val sku: String,
    val gtin: String,
    val batch: String,
    val bins: List<String>,
    val orderNos: List<String>,
    val totalQty: Int,
    val pickedQty: Int,
)

data class PackingItem(
    val id: Int,
    val orderId: String,
    val productId: Int,
    val name: String,
    val code: String,
    val quantity: Int,
    val remaining: Int,
    val packed: Int,
    val total: Int,
    val batch: String?,
    val sku: String?,
    val gtin: String?,
    val mainOrderId: String,
    val pickingOrderId: String,
    val status: String,
    val orderedQty: Int,
    val cartonSSCC: String?,
    val dispatched: Boolean,
)

enum class PackingOrderTab(val label: String) {
    All("All"),
    InProgress("In-progress"),
    ReadyToDispatch("Ready To Dispatch"),
}

data class DispatchedOrder(
    val id: Int,
    val packageType: String?,
    val ssc: String,
    val orderId: Int?,
    val status: String,
    val quantity: Int,
    val items: List<DispatchedItem>,
    val vendorName: String?,
    val purchaseOrderRef: String?,
    val receivingLocation: String?,
    val dueSummary: String?,
) {
    val displayPurchaseOrderRef: String
        get() = purchaseOrderRef?.takeIf { it.isNotEmpty() }
            ?: orderId?.let { "PO-$it" }
            ?: "PO-$id"

    val displayVendor: String get() = vendorName?.takeIf { it.isNotEmpty() } ?: "—"

    val totalExpectedUnits: Int =
        items.sumOf { it.quantity }.takeIf { it > 0 } ?: quantity

    val totalReceivedUnits: Int = items.sumOf { it.receivedQuantity }

    val receivingProgress: Float
        get() {
            val exp = totalExpectedUnits
            if (exp <= 0) return 0f
            return (totalReceivedUnits.toFloat() / exp).coerceAtMost(1f)
        }

    val isReceivingComplete: Boolean
        get() {
            val s = status.lowercase()
            return s == "received" || s == "approved"
        }

    val listStatusKind: ReceivingListStatus
        get() = when {
            isReceivingComplete -> ReceivingListStatus.Complete
            totalReceivedUnits > 0 -> ReceivingListStatus.Active
            else -> ReceivingListStatus.Pending
        }
}

data class DispatchedItem(
    val id: Int,
    val productName: String,
    val status: String,
    val gtin: String,
    val batch: String,
    val pickedQuantity: Int,
    val packedQuantity: Int,
    val receivedQuantity: Int,
    val quantity: Int,
)

enum class ReceivingListStatus {
    Active,
    Pending,
    Complete,
}
