package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.epcis.EpcisFlowService
import core.network.repository.WmsRepository
import core.network.wms.WmsDeliverDispatchRequest
import core.network.wms.WmsShippedPicklist
import core.network.wms.WmsShippedPicklistTask
import core.network.wms.WmsVerifyDispatchTaskRequest
import core.session.WarehouseAccess
import core.session.WarehouseStaffRole
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch

private sealed class ReceivingRoute {
    data object List : ReceivingRoute()
    data class Detail(val picklist: WmsShippedPicklist) : ReceivingRoute()
    data class Verify(val picklist: WmsShippedPicklist) : ReceivingRoute()
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
        ReceivingRoute.List -> ReceivingShippedListScreen(
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            profileName = profileName,
            onLogout = onLogout,
            onOpenPicklist = { route = ReceivingRoute.Detail(it) },
        )
        is ReceivingRoute.Detail -> ShippedPicklistDetailScreen(
            picklist = current.picklist,
            onBack = { route = ReceivingRoute.List },
            onVerify = { route = ReceivingRoute.Verify(it) },
            onDelivered = { route = ReceivingRoute.List },
        )
        is ReceivingRoute.Verify -> ShippedPicklistVerifyScreen(
            picklist = current.picklist,
            onBack = { route = ReceivingRoute.Detail(current.picklist) },
            onAllVerified = { route = ReceivingRoute.Detail(current.picklist) },
        )
    }
}

@Composable
fun ReceivingShippedListScreen(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
    onOpenPicklist: (WmsShippedPicklist) -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val userName = remember { WmsSession.userName(session) }
    val access = remember { WarehouseAccess(session) }
    val staffRole = remember { access.resolvedWarehouseStaffRole() ?: WarehouseStaffRole.Receiver }
    val headerName = profileName?.takeIf { it.isNotBlank() } ?: userName

    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picklists by remember { mutableStateOf<List<WmsShippedPicklist>>(emptyList()) }

    fun load(fromPullRefresh: Boolean = false) {
        scope.launch {
            if (fromPullRefresh) refreshing = true else loading = true
            error = null
            runCatching {
                picklists = repo.fetchShippedPicklists(receivingCompanyId = companyId).items
            }.onFailure { error = it.message }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(companyId) { load() }

    val filtered = picklists.filter {
        when (selectedFilter) {
            "pending" -> it.isPending
            "in_progress" -> it.isInProgress
            "delivered" -> it.isDelivered
            else -> true
        }
    }

    val pendingCount = picklists.count { it.isPending }
    val inProgressCount = picklists.count { it.isInProgress }
    val deliveredCount = picklists.count { it.isDelivered }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        if (profileName != null && onLogout != null) {
            WmsStaffHomeHeader(displayName = headerName, role = staffRole, onLogout = onLogout)
        } else {
            WmsLightHeader(title = "Receive", showBack = showBackNavigation, onBack = onBack)
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceivingListStatCard("Pending", pendingCount.toString(), Icons.Default.Sync, WmsColors.Warning, Modifier.weight(1f))
                    ReceivingListStatCard("In Progress", inProgressCount.toString(), Icons.Default.LocalShipping, WmsColors.ActiveBlue, Modifier.weight(1f))
                    ReceivingListStatCard("Delivered", deliveredCount.toString(), Icons.Default.Inventory2, WmsColors.Success, Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        null to Triple("All", Icons.Default.Inventory2, WmsColors.TextSecondary),
                        "pending" to Triple("Pending", Icons.Default.Sync, WmsColors.Warning),
                        "in_progress" to Triple("In Progress", Icons.Default.LocalShipping, WmsColors.ActiveBlue),
                        "delivered" to Triple("Delivered", Icons.Default.CheckCircle, WmsColors.Success),
                    ).forEach { (key, triple) ->
                        val (label, icon, chipColor) = triple
                        val isSelected = selectedFilter == key
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(if (isSelected) chipColor else chipColor.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .clickable { selectedFilter = key }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(icon, contentDescription = null, tint = if (isSelected) Color.White else chipColor, modifier = Modifier.height(14.dp))
                            Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) Color.White else chipColor)
                        }
                    }
                }

                error?.let { WmsErrorBanner(it) }

                if (loading && !refreshing) {
                    WmsListSkeleton(count = 4)
                } else if (filtered.isEmpty()) {
                    Text("No shipped picklists to receive.", color = WmsColors.TextSecondary, modifier = Modifier.padding(24.dp))
                } else {
                    filtered.forEach { item ->
                        ShippedPicklistCard(item) { onOpenPicklist(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShippedPicklistCard(picklist: WmsShippedPicklist, onClick: () -> Unit) {
    val statusLabel = when {
        picklist.isDelivered -> "Delivered"
        picklist.isInProgress -> "In Progress"
        else -> "Pending"
    }
    val statusColor = when {
        picklist.isDelivered -> WmsColors.Success
        picklist.isInProgress -> WmsColors.ActiveBlue
        else -> WmsColors.Warning
    }
    val statusIcon = when {
        picklist.isDelivered -> Icons.Default.CheckCircle
        picklist.isInProgress -> Icons.Default.LocalShipping
        else -> Icons.Default.Sync
    }
    val verifiedTasks = picklist.tasks.orEmpty().count { (it.receivedQty ?: 0) > 0 && it.receiverId != null }
    val totalTasks = picklist.tasks.orEmpty().size

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(IntrinsicSize.Max)
                .background(statusColor, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)),
        )
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(picklist.pickListTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = WmsColors.TextPrimary)
                    picklist.invoiceNumber?.let {
                        Text("Invoice: $it", fontSize = 12.sp, color = WmsColors.TextSecondary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            HorizontalDivider(color = WmsColors.Border, modifier = Modifier.padding(vertical = 2.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                picklist.receivingCompanyName?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = WmsColors.TextMuted, modifier = Modifier.size(14.dp))
                        Text(it, fontSize = 12.sp, color = WmsColors.TextSecondary, maxLines = 1)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = WmsColors.TextMuted, modifier = Modifier.size(14.dp))
                    Text("${picklist.lineCount} lines", fontSize = 12.sp, color = WmsColors.TextSecondary)
                }
                if (totalTasks > 0) {
                    Text("$verifiedTasks/$totalTasks verified", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = statusColor)
                }
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(end = 12.dp)) {
            Text("\u203A", fontSize = 22.sp, fontWeight = FontWeight.Light, color = WmsColors.TextMuted)
        }
    }
}

@Composable
fun ShippedPicklistDetailScreen(
    picklist: WmsShippedPicklist,
    onBack: () -> Unit,
    onVerify: (WmsShippedPicklist) -> Unit,
    onDelivered: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val receiverId = remember { WmsSession.userId(session) }

    var showDeliverSheet by remember { mutableStateOf(false) }
    var deliverNotes by remember { mutableStateOf("") }
    var delivering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    val tasks = picklist.tasks.orEmpty()
    val verifiedCount = tasks.count { (it.receivedQty ?: 0) > 0 && it.receiverId != null }
    val allVerified = tasks.isNotEmpty() && verifiedCount == tasks.size

    if (showDeliverSheet) {
        AlertDialog(
            onDismissRequest = { showDeliverSheet = false },
            title = { Text("Confirm delivery") },
            text = {
                Column {
                    Text("Mark all items as delivered?")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deliverNotes,
                        onValueChange = { deliverNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickingListId = picklist.resolvedId
                        scope.launch {
                            delivering = true
                            runCatching {
                                val response = repo.markDispatchDelivered(
                                    WmsDeliverDispatchRequest(pickingListId, receiverId, deliverNotes),
                                )
                                runCatching {
                                    val shipped = repo.fetchShippedPicklists(receivingCompanyId = WmsSession.companyId(session))
                                        .items.firstOrNull { it.resolvedId == pickingListId }
                                    val pickListId = (shipped?.id ?: picklist.resolvedId).toString()
                                    if (pickListId.isNotEmpty()) {
                                        val bizId = shipped?.invoiceNumber?.trim()?.takeIf { it.isNotEmpty() }
                                            ?: "ASN-$pickListId"
                                        for (task in picklist.tasks.orEmpty()) {
                                            val gtin = task.gtin?.trim()?.filter { c -> c.isDigit() }?.takeIf { it.isNotEmpty() } ?: continue
                                            val serial = "PL-${task.resolvedId}-1"
                                            val batch = task.batch?.trim()?.takeIf { it.isNotEmpty() } ?: "NA"
                                            val verifyResult = EpcisFlowService.verifyL4Receipt(
                                                session = session,
                                                bizTransactionId = bizId,
                                                gtin = gtin,
                                                serial = serial,
                                            ).getOrNull()
                                            if (verifyResult?.readyForL4Receive == true) {
                                                EpcisFlowService.receiveL4(
                                                    session = session,
                                                    userId = receiverId,
                                                    gtin = gtin,
                                                    bizTransactionId = bizId,
                                                    sourceGln = verifyResult.l3Shipment?.sourceGln,
                                                    destinationGln = verifyResult.l3Shipment?.destinationGln,
                                                    serial = serial,
                                                    batch = batch,
                                                    scannerId = "kmp-receiver-$receiverId",
                                                    receivingConfirmed = true,
                                                )
                                            }
                                        }
                                    }
                                }
                                alertMessage = response.message ?: "All items delivered successfully."
                                showDeliverSheet = false
                                onDelivered()
                            }.onFailure { error = it.message }
                            delivering = false
                        }
                    },
                    enabled = !delivering,
                ) { Text("Deliver") }
            },
            dismissButton = {
                TextButton(onClick = { showDeliverSheet = false }) { Text("Cancel") }
            },
        )
    }

    alertMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            title = { Text("Delivery") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { alertMessage = null }) { Text("OK") } },
        )
    }

    val isDelivered = picklist.isDelivered

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WmsCircularBackButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                val statusColor = when {
                    isDelivered -> WmsColors.Success
                    allVerified -> WmsColors.ActiveBlue
                    verifiedCount > 0 -> WmsColors.Warning
                    else -> WmsColors.TextSecondary
                }
                val statusLabel = when {
                    isDelivered -> "Delivered"
                    allVerified -> "All Verified"
                    verifiedCount > 0 -> "$verifiedCount/${tasks.size} Verified"
                    else -> "Pending"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        if (isDelivered) Icons.Default.CheckCircle else Icons.Default.Sync,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(statusLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(picklist.pickListTitle, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = WmsColors.TextPrimary)
            picklist.invoiceNumber?.let {
                Text("Invoice: $it", fontSize = 13.sp, color = WmsColors.TextSecondary)
            }
        }
        HorizontalDivider(color = WmsColors.Border)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DetailInfoItem(Icons.Default.LocalShipping, "From", picklist.receivingCompanyName ?: "—")
                DetailInfoItem(Icons.Default.Inventory2, "Lines", "${picklist.lineCount}")
                DetailInfoItem(Icons.Default.CheckCircle, "Verified", "$verifiedCount/${tasks.size}")
            }

            picklist.carrier?.let {
                Row(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.LocalShipping, null, tint = WmsColors.ActiveBlue, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Carrier", fontSize = 11.sp, color = WmsColors.TextMuted)
                        Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextPrimary)
                    }
                }
            }
            picklist.trackingNumber?.let {
                Row(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Inventory2, null, tint = WmsColors.ActiveBlue, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Tracking", fontSize = 11.sp, color = WmsColors.TextMuted)
                        Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextPrimary)
                    }
                }
            }

            if (!isDelivered) {
                Button(
                    onClick = { onVerify(picklist) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (allVerified) "All items verified" else "Verify items ($verifiedCount/${tasks.size})",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            error?.let { WmsErrorBanner(it) }

            Text("Line Items", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WmsColors.TextPrimary)
            tasks.forEach { task -> ShippedTaskRow(task) }
        }

        if (!isDelivered && allVerified) {
            Button(
                onClick = { showDeliverSheet = true },
                enabled = !delivering,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Success),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (delivering) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("Mark as Delivered", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailInfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .background(WmsColors.ActiveBlue.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = WmsColors.ActiveBlue, modifier = Modifier.size(18.dp))
        }
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = WmsColors.TextPrimary)
        Text(label, fontSize = 11.sp, color = WmsColors.TextMuted)
    }
}

@Composable
private fun ShippedTaskRow(task: WmsShippedPicklistTask) {
    val verified = (task.receivedQty ?: 0) > 0 && task.receiverId != null
    val bgColor = if (verified) WmsColors.SuccessBg else Color.White
    val borderColor = if (verified) WmsColors.SuccessBorder else WmsColors.Border

    Row(
        Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (verified) WmsColors.Success.copy(alpha = 0.1f) else WmsColors.TabInactiveBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (verified) {
                Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(20.dp))
            } else {
                Text("${task.resolvedId}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(task.productName ?: task.sku ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = WmsColors.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                task.sku?.let { Text("SKU: $it", fontSize = 11.sp, color = WmsColors.TextMuted) }
                task.batch?.let { Text("Batch: $it", fontSize = 11.sp, color = WmsColors.ActiveBlue) }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${task.receivedQty ?: 0}/${task.requestedQty ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (verified) WmsColors.Success else WmsColors.TextPrimary)
            Text("received", fontSize = 10.sp, color = WmsColors.TextMuted)
        }
    }
}

@Composable
fun ShippedPicklistVerifyScreen(
    picklist: WmsShippedPicklist,
    onBack: () -> Unit,
    onAllVerified: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val receiverId = remember { WmsSession.userId(session) }

    val tasks = remember(picklist) { picklist.tasks.orEmpty() }
    val receivedQty = remember { mutableStateMapOf<Int, String>() }
    val notes = remember { mutableStateMapOf<Int, String>() }
    val verifiedIds = remember { mutableStateOf(setOf<Int>()) }
    var verifyingTaskId by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tasks) {
        tasks.forEach { task ->
            val id = task.resolvedId
            if (receivedQty[id] == null) {
                receivedQty[id] = (task.packedQty ?: task.requestedQty ?: 0).toString()
            }
            if ((task.receivedQty ?: 0) > 0 && task.receiverId != null) {
                verifiedIds.value = verifiedIds.value + id
            }
        }
    }

    LaunchedEffect(verifiedIds.value.size, tasks.size) {
        if (tasks.isNotEmpty() && verifiedIds.value.size == tasks.size) {
            onAllVerified()
        }
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WmsCircularBackButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                val allDone = tasks.isNotEmpty() && verifiedIds.value.size == tasks.size
                val statusColor = when {
                    allDone -> WmsColors.Success
                    verifiedIds.value.isNotEmpty() -> WmsColors.Warning
                    else -> WmsColors.TextSecondary
                }
                val statusLabel = when {
                    allDone -> "All Verified"
                    verifiedIds.value.isNotEmpty() -> "${verifiedIds.value.size}/${tasks.size} Verified"
                    else -> "Pending"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        if (allDone) Icons.Default.CheckCircle else Icons.Default.Sync,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(statusLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Verify Items", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = WmsColors.TextPrimary)
            Text(picklist.pickListTitle, fontSize = 13.sp, color = WmsColors.TextSecondary)
        }
        HorizontalDivider(color = WmsColors.Border)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { WmsErrorBanner(it) }

            tasks.forEach { task ->
                val taskId = task.resolvedId
                val isVerified = verifiedIds.value.contains(taskId)
                val cardBg = if (isVerified) WmsColors.SuccessBg else Color.White

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(cardBg, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(
                                    if (isVerified) WmsColors.Success.copy(alpha = 0.1f) else WmsColors.ActiveBlue.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isVerified) {
                                Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(20.dp))
                            } else {
                                Text("${task.resolvedId}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WmsColors.ActiveBlue)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(task.productName ?: task.sku ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = WmsColors.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                task.sku?.let { Text("SKU: $it", fontSize = 11.sp, color = WmsColors.TextMuted) }
                                task.batch?.let { Text("Batch: $it", fontSize = 11.sp, color = WmsColors.ActiveBlue) }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailInfoItem(Icons.Default.Inventory2, "Packed", "${task.packedQty ?: 0}")
                        DetailInfoItem(Icons.Default.Sync, "Requested", "${task.requestedQty ?: 0}")
                    }

                    if (!isVerified) {
                        HorizontalDivider(color = WmsColors.Border)
                        OutlinedTextField(
                            value = receivedQty[taskId].orEmpty(),
                            onValueChange = { receivedQty[taskId] = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Received qty") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                        )
                        OutlinedTextField(
                            value = notes[taskId].orEmpty(),
                            onValueChange = { notes[taskId] = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Notes (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                        )
                        Button(
                            onClick = {
                                val qty = receivedQty[taskId]?.toIntOrNull() ?: 0
                                scope.launch {
                                    verifyingTaskId = taskId
                                    error = null
                                    runCatching {
                                        repo.verifyDispatchTask(
                                            WmsVerifyDispatchTaskRequest(
                                                taskId = taskId,
                                                receivedQty = qty,
                                                receiverId = receiverId,
                                                receiveNotes = notes[taskId].orEmpty(),
                                            ),
                                        )

                                        val taskGtin = task.gtin?.trim()?.filter { c -> c.isDigit() }?.takeIf { it.isNotEmpty() }
                                        if (taskGtin != null) {
                                            println("=== L4 PER-ITEM: fetching L3 by gtin=$taskGtin ===")
                                            val l3Result = EpcisFlowService.fetchL3ByGtin(session, taskGtin).getOrNull()
                                            val l3Shipment = l3Result?.shipments?.firstOrNull()
                                            val bizId = l3Shipment?.bizTransactionId
                                                ?: picklist.invoiceNumber?.trim()?.takeIf { it.isNotEmpty() }
                                                ?: "ASN-${picklist.resolvedId}"
                                            val srcGln = l3Shipment?.sourceGln
                                                ?: picklist.companyId?.toString()?.padStart(13, '0')
                                            val dstGln = l3Shipment?.destinationGln ?: srcGln
                                            val taskSerial = l3Shipment?.serial ?: "PL-${taskId}-1"
                                            val taskBatch = l3Shipment?.batch
                                                ?: task.batch?.trim()?.takeIf { it.isNotEmpty() }
                                                ?: "NA"
                                            println("=== L4 PER-ITEM RECEIVE: bizId=$bizId srcGln=$srcGln dstGln=$dstGln serial=$taskSerial batch=$taskBatch ===")
                                            EpcisFlowService.receiveL4(
                                                session = session,
                                                userId = receiverId,
                                                gtin = taskGtin,
                                                bizTransactionId = bizId,
                                                sourceGln = srcGln,
                                                destinationGln = dstGln,
                                                serial = taskSerial,
                                                batch = taskBatch,
                                                scannerId = "kmp-receiver-$receiverId",
                                                receivingConfirmed = true,
                                                verifyAgainstShipment = false,
                                            )
                                        }

                                        verifiedIds.value = verifiedIds.value + taskId
                                    }.onFailure { error = it.message }
                                    verifyingTaskId = null
                                }
                            },
                            enabled = verifyingTaskId != taskId,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (verifyingTaskId == taskId) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Verifying...")
                            } else {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verify Item", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WmsColors.Success.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(18.dp))
                            Text("Verified", color = WmsColors.Success, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
