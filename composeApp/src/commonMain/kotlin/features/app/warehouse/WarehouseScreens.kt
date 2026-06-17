package features.app.warehouse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.app.warehouse.wms.PackingWmsEntry
import features.app.warehouse.wms.PickingWmsEntry
import features.app.warehouse.wms.ReceivingWmsRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PickingScreen(
    onBack: () -> Unit,
    showBackNavigation: Boolean = true,
) {
    if (WarehouseFlowConfig.useWmsApi) {
        PickingWmsEntry(showBackNavigation = showBackNavigation, onBack = onBack)
    } else {
        LegacyPickingScreen(onBack = onBack, showBackNavigation = showBackNavigation)
    }
}

@Composable
fun PackingScreen(
    onBack: () -> Unit,
    showBackNavigation: Boolean = true,
) {
    if (WarehouseFlowConfig.useWmsApi) {
        PackingWmsEntry(showBackNavigation = showBackNavigation, onBack = onBack)
    } else {
        LegacyPackingScreen(onBack = onBack, showBackNavigation = showBackNavigation)
    }
}

@Composable
fun ReceivingScreen(
    onBack: () -> Unit,
    showBackNavigation: Boolean = true,
) {
    if (WarehouseFlowConfig.useWmsApi) {
        ReceivingWmsRoot(
            showBackNavigation = showBackNavigation,
            onBack = onBack,
        )
    } else {
        LegacyReceivingScreen(
            onBack = onBack,
            showBackNavigation = showBackNavigation,
        )
    }
}

@Composable
private fun LegacyPickingScreen(
    onBack: () -> Unit,
    showBackNavigation: Boolean = true,
) {
    var loading by remember { mutableStateOf(true) }
    var orders by remember { mutableStateOf<List<PickingAppOrder>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var listMode by remember { mutableStateOf(PickingListMode.Orders) }
    var detailOrder by remember { mutableStateOf<PickingAppOrder?>(null) }

    LaunchedEffect(Unit) {
        delay(250)
        orders = if (WarehouseFlowConfig.useMockPicking) {
            WarehouseMockData.mockPickingOrders()
        } else {
            emptyList()
        }
        loading = false
    }

    detailOrder?.let { order ->
        PickingOrderDetailScreen(
            order = order,
            onBack = { detailOrder = null },
            onOrderUpdated = { updated ->
                orders = orders.map { if (it.orderNo == updated.orderNo) updated else it }
                detailOrder = updated
            },
        )
        return
    }

    val statusOptions = listOf("All", "Pending", "Picked", "Received", "Dispatched")
    val filtered = orders.filter { order ->
        val matchesSearch = search.isBlank() ||
            order.orderNo.contains(search, ignoreCase = true) ||
            order.items.any { it.name.contains(search, ignoreCase = true) }
        val matchesStatus = statusFilter == "All" ||
            order.status.equals(statusFilter, ignoreCase = true) ||
            (statusFilter == "Pending" && order.isPending)
        matchesSearch && matchesStatus
    }
    val activeCount = filtered.count {
        !listOf("picked", "received", "dispatched", "completed").contains(it.status.lowercase())
    }

    WarehouseListScaffold(
        title = "Picking",
        activeCount = activeCount,
        loading = loading,
        loadingMessage = "Loading pick lists…",
        onBack = onBack,
        accentBarWidth = null,
        showBackNavigation = showBackNavigation,
    ) {
        PickingModeTabs(selected = listMode, onSelect = { listMode = it })
        StatusChipRow(statusOptions, statusFilter) { statusFilter = it }
        WarehouseSearchRow(
            placeholder = "Search order / zone / product",
            query = search,
            onQueryChange = { search = it },
            filterOptions = statusOptions,
            selectedFilter = statusFilter,
            onFilterSelected = { statusFilter = it },
        )

        when (listMode) {
            PickingListMode.Orders -> {
                if (filtered.isEmpty()) {
                    WarehouseEmptyState("📦", "No orders", "Try another filter or search.")
                } else {
                    filtered.forEach { order ->
                        PickingOrderCard(order) { detailOrder = order }
                    }
                }
            }
            PickingListMode.PickList -> {
                val rows = filtered.flatMap { order ->
                    order.items.map { order.orderNo to it }
                }
                if (rows.isEmpty()) {
                    WarehouseEmptyState("📋", "No pick list items", "Orders with line items will appear here.")
                } else {
                    rows.forEach { (orderNo, item) ->
                        PickingPickListCard(orderNo, item) {
                            detailOrder = filtered.find { it.orderNo == orderNo }
                        }
                    }
                }
            }
            PickingListMode.Products -> {
                val groups = buildProductGroups(filtered)
                if (groups.isEmpty()) {
                    WarehouseEmptyState("🏷️", "No products", "Product groups appear when orders have items.")
                } else {
                    groups.forEach { group ->
                        PickingPickListCard(
                            orderNo = group.orderNos.firstOrNull() ?: "—",
                            item = PickingAppItem(
                                productId = 0,
                                name = group.name,
                                sku = group.sku,
                                batch = group.batch,
                                gtin = group.gtin,
                                bin = group.bins.firstOrNull() ?: "—",
                                pickedQty = group.pickedQty,
                                totalQty = group.totalQty,
                            ),
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyPackingScreen(
    onBack: () -> Unit,
    showBackNavigation: Boolean = true,
) {
    var loading by remember { mutableStateOf(true) }
    var packingItems by remember { mutableStateOf<List<PackingItem>>(emptyList()) }
    var orderIdMap by remember { mutableStateOf<Map<String, Pair<String, String>>>(emptyMap()) }
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(PackingOrderTab.All) }
    var dispatchingId by remember { mutableStateOf<String?>(null) }
    var detail: PackingDetailState? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(250)
        if (WarehouseFlowConfig.useMockPacking) {
            val (items, map) = WarehouseMockData.mockPackingDataset()
            packingItems = items
            orderIdMap = map
        }
        loading = false
    }

    detail?.let { state ->
        PackingDetailScreen(
            orderId = state.orderId,
            pickingOrderId = state.pickingOrderId,
            items = state.items,
            onBack = { detail = null },
            onItemsUpdated = { updatedLines ->
                val updatedIds = updatedLines.associateBy { it.id }
                packingItems = packingItems.map { updatedIds[it.id] ?: it }
                detail = state.withItems(updatedLines)
            },
        )
        return
    }

    val grouped = packingItems.groupBy { it.orderId }
    val filtered = grouped
        .filter { (orderId, items) ->
            (search.isBlank() || orderId.contains(search, ignoreCase = true)) &&
                filterPackingItems(items, tab)
        }
        .toList()
        .sortedBy { it.first }

    val activeCount = filtered.count { (_, items) -> !items.all { it.status == "packed" } }

    WarehouseListScaffold(
        title = "Packing",
        activeCount = activeCount,
        loading = loading,
        loadingMessage = "Loading packing jobs…",
        onBack = onBack,
        showBackNavigation = showBackNavigation,
    ) {
        WarehouseSearchRow(
            placeholder = "Search order…",
            query = search,
            onQueryChange = { search = it },
            filterOptions = PackingOrderTab.entries.map { it.label },
            selectedFilter = tab.label,
            onFilterSelected = { label ->
                tab = PackingOrderTab.entries.first { it.label == label }
            },
        )

        if (filtered.isEmpty()) {
            WarehouseEmptyState("📦", "No packing jobs", "Try another filter or pull to refresh when orders are ready.")
        } else {
            filtered.forEach { (orderId, items) ->
                val pickId = orderIdMap[orderId]?.second ?: orderId
                val packId = packDisplayId(pickId)
                val station = stationLabel(orderId)
                val itemCount = items.sumOf { maxOf(it.quantity, 0) }
                val boxCount = boxCount(items)
                val ready = items.all { it.status == "packed" }
                PackingJobCard(
                    packId = packId,
                    stationLabel = station,
                    orderRef = orderId,
                    itemCount = itemCount,
                    boxCount = boxCount,
                    isReadyToDispatch = ready,
                    isDispatching = dispatchingId == orderId,
                    onOpen = {
                        val ids = orderIdMap[orderId] ?: (orderId to orderId)
                        detail = PackingDetailState(orderId, ids.second, items)
                    },
                    onDispatch = {
                        dispatchingId = orderId
                        scope.launch {
                            delay(600)
                            dispatchingId = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LegacyReceivingScreen(
    onBack: () -> Unit,
    showBackNavigation: Boolean = true,
) {
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<DispatchedOrder>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var detail by remember { mutableStateOf<DispatchedOrder?>(null) }

    LaunchedEffect(Unit) {
        delay(250)
        items = if (WarehouseFlowConfig.useMockReceiving) {
            WarehouseMockData.mockReceivingOrders()
        } else {
            emptyList()
        }
        loading = false
    }

    detail?.let { order ->
        ReceivingDetailScreen(
            order = order,
            onBack = { detail = null },
            onOrderUpdated = { updated ->
                items = items.map { if (it.id == updated.id) updated else it }
                detail = updated
            },
        )
        return
    }

    val filtered = items.filter { order ->
        val matchesSearch = search.isBlank() ||
            order.displayPurchaseOrderRef.contains(search, ignoreCase = true) ||
            order.displayVendor.contains(search, ignoreCase = true) ||
            (order.orderId?.toString()?.contains(search, ignoreCase = true) == true)
        val status = order.status.lowercase()
        val matchesStatus = when (statusFilter) {
            "All" -> true
            "Dispatched" -> status == "dispatched"
            "Approved" -> status == "received" || status == "approved"
            else -> true
        }
        matchesSearch && matchesStatus
    }
    val activeCount = filtered.count { !it.isReceivingComplete }

    WarehouseListScaffold(
        title = "Receiving",
        activeCount = activeCount,
        loading = loading,
        loadingMessage = "Loading receiving…",
        onBack = onBack,
        showBackNavigation = showBackNavigation,
    ) {
        ReceivingDockBanner()
        WarehouseSearchRow(
            placeholder = "Search PO or vendor…",
            query = search,
            onQueryChange = { search = it },
            filterOptions = listOf("All", "Dispatched", "Approved"),
            selectedFilter = statusFilter,
            onFilterSelected = { statusFilter = it },
        )

        if (filtered.isEmpty()) {
            WarehouseEmptyState("📥", "No purchase orders", "Try another filter or search.")
        } else {
            filtered.forEach { order ->
                ReceivingPoCard(order) { detail = order }
            }
            val firstOpen = filtered.firstOrNull { !it.isReceivingComplete } ?: filtered.firstOrNull()
            TextButton(
                onClick = { firstOpen?.let { detail = it } },
                enabled = firstOpen != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (firstOpen != null) WarehouseColors.Accent
                        else WarehouseColors.Accent.copy(alpha = 0.45f),
                    ),
            ) {
                Text(
                    "SCAN PACKAGE",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun WarehouseListScaffold(
    title: String,
    activeCount: Int,
    loading: Boolean,
    loadingMessage: String,
    onBack: () -> Unit,
    accentBarWidth: Int? = 56,
    showBackNavigation: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarehouseColors.Background),
    ) {
        WarehouseModuleHeader(title, activeCount, onBack, accentBarWidth, showBackNavigation)
        if (loading) {
            WarehouseLoadingState(loadingMessage)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

private data class PackingDetailState(
    val orderId: String,
    val pickingOrderId: String,
    val items: List<PackingItem>,
) {
    fun withItems(items: List<PackingItem>) = PackingDetailState(orderId, pickingOrderId, items)
}

private fun filterPackingItems(items: List<PackingItem>, tab: PackingOrderTab): Boolean = when (tab) {
    PackingOrderTab.All -> true
    PackingOrderTab.InProgress -> items.any { it.status == "picked" }
    PackingOrderTab.ReadyToDispatch -> items.all { it.status == "packed" }
}

private fun packDisplayId(pickingOrderId: String): String {
    val digits = pickingOrderId.filter { it.isDigit() }
    val n = digits.toIntOrNull() ?: kotlin.math.abs(pickingOrderId.hashCode() % 100_000)
    return "PACK-%05d".format(n % 100_000)
}

private fun stationLabel(orderId: String): String {
    val n = (kotlin.math.abs(orderId.hashCode()) % 9) + 1
    return "STATION %02d".format(n)
}

private fun boxCount(items: List<PackingItem>): Int {
    val ssccs = items.mapNotNull { it.cartonSSCC }.filter { it.isNotEmpty() }.toSet()
    return if (ssccs.isNotEmpty()) maxOf(1, ssccs.size) else 1
}

private fun buildProductGroups(orders: List<PickingAppOrder>): List<PickingProductGroup> {
    val groups = mutableListOf<PickingProductGroup>()
    for (order in orders) {
        for (item in order.items) {
            val key = "${item.sku}|${item.gtin}|${item.batch}"
            val idx = groups.indexOfFirst { it.id == key }
            if (idx >= 0) {
                val g = groups[idx]
                groups[idx] = g.copy(
                    totalQty = g.totalQty + item.totalQty,
                    pickedQty = g.pickedQty + item.pickedQty,
                    orderNos = (g.orderNos + order.orderNo).distinct(),
                    bins = (g.bins + item.bin).distinct(),
                )
            } else {
                groups += PickingProductGroup(
                    id = key,
                    name = item.name,
                    sku = item.sku,
                    gtin = item.gtin,
                    batch = item.batch,
                    bins = listOf(item.bin),
                    orderNos = listOf(order.orderNo),
                    totalQty = item.totalQty,
                    pickedQty = item.pickedQty,
                )
            }
        }
    }
    return groups.sortedBy { it.name.lowercase() }
}
