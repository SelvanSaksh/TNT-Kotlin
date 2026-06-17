package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.network.repository.WmsRepository
import core.network.wms.WmsPackingReceiverNode
import core.session.WarehouseAccess
import core.session.WarehouseStaffRole
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch

private sealed class ReceivingRoute {
    data object List : ReceivingRoute()
    data class Detail(val order: WmsPackingReceiverNode, val readOnly: Boolean = false) : ReceivingRoute()
}

@Composable
fun ReceivingWmsRoot(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
) {
    var route by remember { mutableStateOf<ReceivingRoute>(ReceivingRoute.List) }

    when (val current = route) {
        ReceivingRoute.List -> ReceivingWmsListScreen(
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            profileName = profileName,
            onLogout = onLogout,
            onOpenOrder = { order, readOnly ->
                route = ReceivingRoute.Detail(order, readOnly)
            },
        )
        is ReceivingRoute.Detail -> ReceivingOrderDetailScreen(
            order = current.order,
            readOnly = current.readOnly,
            onBack = { route = ReceivingRoute.List },
            onCompleted = { route = ReceivingRoute.List },
        )
    }
}

@Composable
fun ReceivingWmsListScreen(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
    onOpenOrder: (WmsPackingReceiverNode, Boolean) -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val userName = remember { WmsSession.userName(session) }
    val access = remember { WarehouseAccess(session) }
    val staffRole = remember { access.resolvedWarehouseStaffRole() ?: WarehouseStaffRole.Receiver }
    val headerName = profileName?.takeIf { it.isNotBlank() } ?: userName

    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var orders by remember { mutableStateOf<List<WmsPackingReceiverNode>>(emptyList()) }

    fun load(fromPullRefresh: Boolean = false) {
        scope.launch {
            if (fromPullRefresh) refreshing = true else loading = true
            error = null
            runCatching {
                orders = repo.fetchPackingReceiverOrders(companyId)
                    .filter { it.normalizedNodeType == "ORDER" }
            }.onFailure { error = it.message }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(companyId) { load() }

    val filtered = orders.filter {
        search.isBlank() ||
            (it.orderNumber ?: "").contains(search, ignoreCase = true) ||
            (it.customerName ?: "").contains(search, ignoreCase = true)
    }
    val inTransit = orders.count { !it.isReceivedForDisplay() && (it.totalPackedQty ?: 0) > 0 }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        if (profileName != null && onLogout != null) {
            WmsStaffHomeHeader(
                displayName = headerName,
                role = staffRole,
                onLogout = onLogout,
            )
        } else {
            WmsLightHeader(
                title = headerName,
                showBack = showBackNavigation,
                onBack = onBack,
            )
        }

        WmsPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { load(fromPullRefresh = true) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (loading && !refreshing) {
                        WmsStatPairSkeleton()
                    } else {
                        ReceivingListStatCard(
                            label = "Total Orders",
                            value = orders.size.toString(),
                            icon = Icons.Default.Inventory2,
                            iconTint = WmsColors.ActiveBlue,
                            modifier = Modifier.weight(1f),
                        )
                        ReceivingListStatCard(
                            label = "In Transit",
                            value = inTransit.toString(),
                            icon = Icons.Default.Sync,
                            iconTint = WmsColors.Warning,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search order, customer…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                )
                error?.let { WmsErrorBanner(it) }
                if (loading && !refreshing) {
                    WmsListSkeleton(count = 4)
                } else {
                    filtered.forEach { order ->
                        PackingReceiverOrderCard(order) {
                            onOpenOrder(order, order.isReceivedForDisplay())
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            "No orders to receive.",
                            color = WmsColors.TextSecondary,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}
