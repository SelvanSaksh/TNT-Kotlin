package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.AdminPackingFilterCategory
import core.network.wms.WmsPickListItem
import core.network.wms.WmsUser
import core.network.wms.adminPackingCardTitle
import core.network.wms.adminPackingFilterCategory
import core.network.wms.adminPackingLocationLine
import core.network.wms.adminPackingPackedQty
import core.network.wms.adminPackingPickStatusLabel
import core.network.wms.adminPackingRequestedQty
import core.network.wms.adminPackingResolvedLineCount
import core.network.wms.adminPackingStatusLabel
import core.network.wms.hasPackerAssigned
import core.network.wms.resolvedPackerName
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch
import kotlin.math.min

private sealed class AdminPackingRoute {
    data object Queue : AdminPackingRoute()
    data class Assign(val item: WmsPickListItem) : AdminPackingRoute()
    data class Dispatch(val item: WmsPickListItem) : AdminPackingRoute()
}

@Composable
fun AdminPackingQueueScreen(onBack: () -> Unit) {
    var route by remember { mutableStateOf<AdminPackingRoute>(AdminPackingRoute.Queue) }

    when (val current = route) {
        AdminPackingRoute.Queue -> AdminPackingQueueListScreen(
            onBack = onBack,
            onAssign = { route = AdminPackingRoute.Assign(it) },
            onDispatch = { route = AdminPackingRoute.Dispatch(it) },
        )
        is AdminPackingRoute.Assign -> AdminPackingAssignScreen(
            item = current.item,
            onBack = { route = AdminPackingRoute.Queue },
            onAssigned = { route = AdminPackingRoute.Queue },
        )
        is AdminPackingRoute.Dispatch -> DispatchInvoiceScreen(
            pickList = current.item,
            onBack = { route = AdminPackingRoute.Queue },
            onDispatched = { route = AdminPackingRoute.Queue },
        )
    }
}

@Composable
private fun AdminPackingQueueListScreen(
    onBack: () -> Unit,
    onAssign: (WmsPickListItem) -> Unit,
    onDispatch: (WmsPickListItem) -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }

    var filter by remember { mutableStateOf(AdminPackingFilterCategory.All) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lists by remember { mutableStateOf<List<WmsPickListItem>>(emptyList()) }
    var lineItemsItem by remember { mutableStateOf<WmsPickListItem?>(null) }

    fun load(fromPullRefresh: Boolean = false) {
        scope.launch {
            if (fromPullRefresh) refreshing = true else loading = true
            error = null
            runCatching {
                lists = repo.fetchAdminPackingPickLists(companyId).pickLists.filter {
                    val status = (it.status ?: "").uppercase()
                    status == "COMPLETED" || status == "PICKED" || status == "STAGED"
                }
            }.onFailure { error = it.message }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(companyId) { load() }

    val filtered = lists.filter { item ->
        filter == AdminPackingFilterCategory.All || item.adminPackingFilterCategory() == filter
    }

    fun filterCount(category: AdminPackingFilterCategory): Int =
        if (category == AdminPackingFilterCategory.All) {
            lists.size
        } else {
            lists.count { it.adminPackingFilterCategory() == category }
        }

    lineItemsItem?.let { item ->
        AdminLineItemsSheet(item = item, onDismiss = { lineItemsItem = null })
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBg)) {
        AdminPackingHeader(onBack = onBack)
        AdminPackingSummaryBar(readyCount = lists.size, loading = loading && !refreshing)
        AdminPackingFilterTabs(
            selected = filter,
            onSelect = { filter = it },
            filterCount = ::filterCount,
            loading = loading && !refreshing,
        )

        error?.let {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(WmsColors.ErrorBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(it, color = WmsColors.ErrorFg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { load() }) {
                    Text("Retry", color = WmsColors.Navy, fontWeight = FontWeight.Bold)
                }
            }
        }

        WmsPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { load(fromPullRefresh = true) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                loading && !refreshing -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        AdminPackingQueueSkeleton(count = 4)
                    }
                }
                filtered.isEmpty() -> {
                    AdminPackingEmptyState()
                }
                else -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        filtered.forEach { item ->
                            AdminPackingPickListCard(
                                item = item,
                                onAssign = { onAssign(item) },
                                onDispatch = { onDispatch(item) },
                                onShowLines = { lineItemsItem = item },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPackingHeader(onBack: () -> Unit) {
    WmsLightHeader(
        title = "Packing",
        subtitle = "Assign packers to picked lists",
        showBack = true,
        onBack = onBack,
    )
}

@Composable
private fun AdminPackingSummaryBar(readyCount: Int, loading: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            Column(Modifier.weight(1f)) {
                AdminPackingSummarySkeleton()
            }
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "PICKED LISTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextMuted,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    "$readyCount ready for packaging",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.Navy,
                )
            }
        }
        Text(
            "ADMIN",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.Success,
            modifier = Modifier
                .clip(CircleShape)
                .background(WmsColors.SuccessBg)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
    HorizontalDivider(color = WmsColors.Border)
}

@Composable
private fun AdminPackingFilterTabs(
    selected: AdminPackingFilterCategory,
    onSelect: (AdminPackingFilterCategory) -> Unit,
    filterCount: (AdminPackingFilterCategory) -> Int,
    loading: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (loading) {
            AdminPackingFilterTabsSkeleton()
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdminPackingFilterCategory.entries.forEach { category ->
                    AdminFilterChip(
                        label = category.label,
                        count = if (category == AdminPackingFilterCategory.All) null else filterCount(category),
                        icon = adminFilterIcon(category),
                        tint = Color(category.filterColorHex),
                        selected = selected == category,
                        onClick = { onSelect(category) },
                    )
                }
            }
        }
    }
    HorizontalDivider(color = WmsColors.Border)
}

@Composable
private fun AdminFilterChip(
    label: String,
    count: Int?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) tint else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else tint.copy(alpha = 0.3f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = if (selected) Color.White else tint, modifier = Modifier.size(14.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else tint,
        )
        if (count != null) {
            Text(
                "$count",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else tint,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.3f) else tint.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

private fun adminFilterIcon(category: AdminPackingFilterCategory) = when (category) {
    AdminPackingFilterCategory.All -> Icons.Default.Inventory2
    AdminPackingFilterCategory.Pending -> Icons.Default.AccessTime
    AdminPackingFilterCategory.InProgress -> Icons.Default.Sync
    AdminPackingFilterCategory.ReadyToDispatch -> Icons.Default.LocalShipping
    AdminPackingFilterCategory.Dispatched -> Icons.Default.LocalShipping
}

@Composable
private fun AdminPackingEmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Inventory2, null, tint = WmsColors.TextMuted, modifier = Modifier.size(30.dp))
        Text("No pick lists", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
        Text(
            "Pick lists with the selected filter will appear here.",
            fontSize = 13.sp,
            color = WmsColors.TextSecondary,
        )
    }
}

@Composable
private fun AdminPackingPickListCard(
    item: WmsPickListItem,
    onAssign: () -> Unit,
    onDispatch: () -> Unit,
    onShowLines: () -> Unit,
) {
    val category = item.adminPackingFilterCategory()
    val accent = Color(category.filterColorHex)
    val cardBg = when (category) {
        AdminPackingFilterCategory.Pending -> Color(0xFFFFFBEB)
        AdminPackingFilterCategory.InProgress -> Color(0xFFEFF6FF)
        AdminPackingFilterCategory.ReadyToDispatch -> Color(0xFFECFDF5)
        AdminPackingFilterCategory.Dispatched -> Color(0xFFF9FAFB)
        AdminPackingFilterCategory.All -> Color.White
    }
    val requested = item.adminPackingRequestedQty
    val packed = item.adminPackingPackedQty
    val progress = if (requested > 0) packed.toFloat() / requested else 0f
    val statusBadge = if (category == AdminPackingFilterCategory.ReadyToDispatch) {
        "READY TO DISPATCH"
    } else {
        item.adminPackingStatusLabel.uppercase()
    }
    val pickStatus = item.adminPackingPickStatusLabel

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.5.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.adminPackingCardTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            item.adminPackingLocationLine,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = WmsColors.TextSecondary,
                            maxLines = 2,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        statusBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    if (pickStatus != statusBadge.uppercase()) {
                        Text(pickStatus, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextMuted)
                    }
                }
            }

            if (requested > 0 && category != AdminPackingFilterCategory.Dispatched) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "$packed of $requested packed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151),
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(WmsColors.Border),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(min(progress, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(accent),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.stagingLocationName?.takeIf { it.isNotBlank() }?.let { location ->
                    AdminMetaChip(Icons.Default.LocationOn, location)
                }
                item.toteNumber?.takeIf { it.isNotBlank() }?.let { tote ->
                    AdminMetaChip(Icons.Default.Inventory2, tote)
                }
                item.resolvedZoneCode?.takeIf { it.isNotBlank() }?.let { zone ->
                    AdminMetaChip(Icons.Default.Map, zone)
                }
            }

            item.resolvedPackerName?.takeIf { it.isNotBlank() }?.let { packer ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Person, null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Assigned to $packer",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151),
                    )
                }
            }

            if (category == AdminPackingFilterCategory.Dispatched) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF9FAFB))
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.receivingCompanyName?.takeIf { it.isNotBlank() }?.let { company ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Business, null, tint = WmsColors.TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(company, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                        }
                    }
                    item.invoiceNumber?.takeIf { it.isNotBlank() }?.let { invoice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, tint = WmsColors.TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Invoice: $invoice", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (item.adminPackingResolvedLineCount > 0) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F4F6))
                            .clickable(onClick = onShowLines)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.List, null, tint = Color(0xFF4B5563), modifier = Modifier.size(14.dp))
                        Text(
                            "${item.adminPackingResolvedLineCount} lines",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4B5563),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                when (category) {
                    AdminPackingFilterCategory.ReadyToDispatch -> {
                        Button(
                            onClick = onDispatch,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Success),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) {
                            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ready to Dispatch", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    AdminPackingFilterCategory.Dispatched -> Unit
                    else -> {
                        Row(
                            Modifier.clickable(onClick = onAssign),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (item.hasPackerAssigned) "Reassign" else "Assign Packer",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WmsColors.Navy,
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = WmsColors.Navy, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = WmsColors.TextSecondary, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminLineItemsSheet(item: WmsPickListItem, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${item.adminPackingCardTitle} · Line items",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextPrimary,
            )
            item.resolvedLines.forEach { line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF8FAFC))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("SKU ${line.displaySku}", fontSize = 11.sp, color = WmsColors.TextSecondary)
                    }
                    Text(line.displayQty, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WmsColors.Navy)
                }
            }
        }
    }
}

@Composable
private fun AdminPackingAssignScreen(
    item: WmsPickListItem,
    onBack: () -> Unit,
    onAssigned: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }

    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var packers by remember { mutableStateOf<List<WmsUser>>(emptyList()) }
    var selectedPackerId by remember { mutableStateOf<Int?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    fun loadPackers() {
        scope.launch {
            loading = true
            error = null
            runCatching { packers = repo.fetchPackersForAssignment(companyId) }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(companyId) { loadPackers() }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false; onAssigned() },
            title = { Text("Packer assigned") },
            text = { Text("Pick list ${item.adminPackingCardTitle} is assigned for packaging.") },
            confirmButton = {
                TextButton(onClick = { showSuccess = false; onAssigned() }) { Text("OK") }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBg)) {
        WmsLightHeader(title = "Assign Packer", showBack = true, onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                Text("PICK LIST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextMuted)
                Text(item.adminPackingCardTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                item.orderNumber?.let { Text("Order $it", fontSize = 13.sp, color = WmsColors.TextSecondary) }
            }

            Text("ASSIGN PACKER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextMuted)
            error?.let { WmsErrorBanner(it) }

            if (loading) {
                CircularProgressIndicator(color = WmsColors.Navy, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                packers.forEach { packer ->
                    val selected = selectedPackerId == packer.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) WmsColors.Navy.copy(alpha = 0.08f) else Color.White,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { selectedPackerId = packer.id }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(packer.name, fontWeight = FontWeight.SemiBold)
                            packer.email?.let { Text(it, fontSize = 12.sp, color = WmsColors.TextSecondary) }
                        }
                        if (selected) {
                            Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val packerId = selectedPackerId ?: return@Button
                scope.launch {
                    submitting = true
                    error = null
                    val pickListId = item.resolvedAPIListId
                    runCatching {
                        repo.assignPackerToPickList(pickListId, packerId, companyId)
                        showSuccess = true
                    }.onFailure { error = it.message }
                    submitting = false
                }
            },
            enabled = selectedPackerId != null && !submitting,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (submitting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Assign Packer", fontWeight = FontWeight.Bold)
        }
    }
}
