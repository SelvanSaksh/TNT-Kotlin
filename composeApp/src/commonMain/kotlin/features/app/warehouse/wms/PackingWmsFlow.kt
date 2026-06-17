package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Divider
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
import core.network.repository.WmsRepository
import core.session.WarehouseAccess
import core.session.WarehouseStaffRole
import core.network.wms.WmsAddPackingBoxItemRequest
import core.network.wms.WmsCreatePackingBoxRequest
import core.network.wms.WmsCreatedPackingBox
import core.network.wms.WmsPackingBoxSummary
import core.network.wms.WmsPackingPickListItem
import core.network.wms.WmsPackingPickListLine
import core.network.wms.WmsUpdatePackingPickListStatusRequest
import core.network.wms.WmsUpdatePackingBoxStatusRequest
import core.network.wms.packingBoxTypeConfig
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch

private sealed class PackingRoute {
    data object Track : PackingRoute()
    data class BoxSession(
        val list: WmsPackingPickListItem,
        val box: WmsCreatedPackingBox,
        val packTypeLabel: String,
    ) : PackingRoute()
    data class AddQty(
        val list: WmsPackingPickListItem,
        val box: WmsCreatedPackingBox,
        val packTypeLabel: String,
        val line: WmsPackingPickListLine,
    ) : PackingRoute()
    data class PackageTree(
        val pickList: WmsPackingPickListItem,
    ) : PackingRoute()
}

@Composable
fun PackingWmsRoot(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
) {
    var route by remember { mutableStateOf<PackingRoute>(PackingRoute.Track) }
    var pendingBeginSession by remember { mutableStateOf<Pair<WmsPackingPickListItem, String>?>(null) }

    when (val current = route) {
        PackingRoute.Track -> PackingTrackScreen(
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            profileName = profileName,
            onLogout = onLogout,
            pendingBeginSession = pendingBeginSession,
            onPendingBeginSessionHandled = { pendingBeginSession = null },
            onOpenBoxSession = { list, box, type ->
                route = PackingRoute.BoxSession(list, box, type)
            },
            onOpenPackageTree = { pickList ->
                route = PackingRoute.PackageTree(pickList)
            },
        )
        is PackingRoute.BoxSession -> PackingBoxSessionScreen(
            list = current.list,
            box = current.box,
            packTypeLabel = current.packTypeLabel,
            onBack = { route = PackingRoute.Track },
            onAddQty = { line ->
                route = PackingRoute.AddQty(current.list, current.box, current.packTypeLabel, line)
            },
            onCompletePackage = { route = PackingRoute.Track },
            onCompleteAndNewPackage = { list, packType ->
                pendingBeginSession = list to packType
                route = PackingRoute.Track
            },
            onRequestNewPackage = {
                pendingBeginSession = current.list to current.packTypeLabel
                route = PackingRoute.Track
            },
        )
        is PackingRoute.AddQty -> PackingLineAddQtyScreen(
            list = current.list,
            line = current.line,
            box = current.box,
            packTypeLabel = current.packTypeLabel,
            onBack = {
                route = PackingRoute.BoxSession(current.list, current.box, current.packTypeLabel)
            },
            onBoxUpdated = { updatedBox ->
                route = PackingRoute.BoxSession(current.list, updatedBox, current.packTypeLabel)
            },
            onRequestNewPackage = {
                pendingBeginSession = current.list to current.packTypeLabel
                route = PackingRoute.Track
            },
        )

        is PackingRoute.PackageTree -> {
            PackingPickListHierarchyScreen(
                pickList = current.pickList,
                onBack = { route = PackingRoute.Track },
            )
        }
    }
}

@Composable
fun PackingTrackScreen(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
    pendingBeginSession: Pair<WmsPackingPickListItem, String>? = null,
    onPendingBeginSessionHandled: () -> Unit = {},
    onOpenBoxSession: (WmsPackingPickListItem, WmsCreatedPackingBox, String) -> Unit,
    onOpenPackageTree: (WmsPackingPickListItem) -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val packerId = remember { WmsSession.userId(session) }
    val userName = remember { WmsSession.userName(session) }
    val access = remember { WarehouseAccess(session) }
    val staffRole = remember { access.resolvedWarehouseStaffRole() ?: WarehouseStaffRole.Packer }

    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lists by remember { mutableStateOf<List<WmsPackingPickListItem>>(emptyList()) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var showPackTypeDialog by remember { mutableStateOf(false) }
    var showExistingBoxSheet by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<WmsPackingPickListItem?>(null) }
    var incompleteBoxes by remember { mutableStateOf<List<WmsPackingBoxSummary>>(emptyList()) }
    var pendingPackType by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun resumeExistingBox(list: WmsPackingPickListItem, summary: WmsPackingBoxSummary) {
        scope.launch {
            creating = true
            error = null
            runCatching {
                val box = repo.fetchPackingBox(summary.resolvedPackId, companyId)
                onOpenBoxSession(list, box, summary.packTypeUiLabel())
            }.onFailure { error = it.message }
            creating = false
            showExistingBoxSheet = false
            incompleteBoxes = emptyList()
            pendingPackType = null
        }
    }

    fun beginBoxSession(list: WmsPackingPickListItem, packTypeLabel: String) {
        scope.launch {
            creating = true
            error = null
            val (_, level) = packingBoxTypeConfig(packTypeLabel)
            runCatching {
                val openBoxes = repo.fetchIncompletePackingBoxes(
                    companyId = companyId,
                    packingOrderId = list.packingOrderId,
                    pickListId = list.pickListId,
                    hierarchyLevel = level,
                )
                when {
                    openBoxes.size == 1 -> {
                        val box = repo.fetchPackingBox(openBoxes.first().resolvedPackId, companyId)
                        onOpenBoxSession(list, box, packTypeLabel)
                    }
                    openBoxes.isNotEmpty() -> {
                        selectedList = list
                        incompleteBoxes = openBoxes
                        pendingPackType = packTypeLabel
                        showExistingBoxSheet = true
                    }
                    else -> {
                        val (packType, hierarchyLevel) = packingBoxTypeConfig(packTypeLabel)
                        val box = repo.createPackingBox(
                            WmsCreatePackingBoxRequest(
                                companyId = companyId,
                                packingOrderId = list.packingOrderId ?: list.id,
                                packLabel = "${list.displayTitle}-$packTypeLabel",
                                packType = packType,
                                hierarchyLevel = hierarchyLevel,
                                createdBy = packerId,
                            ),
                        )
                        onOpenBoxSession(list, box, packTypeLabel)
                    }
                }
            }.onFailure { error = it.message }
            creating = false
        }
    }

    fun onStartPack(list: WmsPackingPickListItem) {
        selectedList = list
        val embedded = list.resolvedInProgressBoxes
        when {
            embedded.size == 1 -> {
                resumeExistingBox(list, embedded.first())
                return
            }
            embedded.size > 1 -> {
                incompleteBoxes = embedded
                pendingPackType = null
                showExistingBoxSheet = true
                return
            }
        }

        if (list.isPackingInProgress) {
            scope.launch {
                creating = true
                error = null
                runCatching {
                    val openBoxes = repo.fetchIncompletePackingBoxes(
                        companyId = companyId,
                        packingOrderId = list.packingOrderId,
                        pickListId = list.pickListId,
                    )
                    when {
                        openBoxes.size == 1 -> {
                            val summary = openBoxes.first()
                            val box = repo.fetchPackingBox(summary.resolvedPackId, companyId)
                            onOpenBoxSession(list, box, summary.packTypeUiLabel())
                        }
                        openBoxes.isNotEmpty() -> {
                            incompleteBoxes = openBoxes
                            pendingPackType = null
                            showExistingBoxSheet = true
                        }
                        else -> showPackTypeDialog = true
                    }
                }.onFailure { error = it.message }
                creating = false
            }
            return
        }

        showPackTypeDialog = true
    }

    fun loadLists(fromPullRefresh: Boolean = false) {
        scope.launch {
            if (fromPullRefresh) {
                refreshing = true
            } else {
            loading = true
            }
            error = null
            runCatching {
                val payload = repo.fetchPackingPickListsByPacker(packerId, companyId)
                lists = payload.lists
                payload.lists.filter { it.isReadyToMarkPacked }.forEach { list ->
                    val pickListId = list.pickListId ?: list.id
                    runCatching {
                        repo.updatePackingPickListStatus(pickListId, companyId, "PACKED", packerId)
                    }
                }
            }.onFailure { error = it.message }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(companyId, packerId) { loadLists() }

    LaunchedEffect(pendingBeginSession) {
        val request = pendingBeginSession ?: return@LaunchedEffect
        onPendingBeginSessionHandled()
        beginBoxSession(request.first, request.second)
    }

    val filtered = lists.filter {
        search.isBlank() ||
            it.displayTitle.contains(search, ignoreCase = true) ||
            (it.orderNumber ?: "").contains(search, ignoreCase = true)
    }

    if (showPackTypeDialog && selectedList != null) {
        ChoosePackTypeSheet(
            creating = creating,
            onDismiss = { showPackTypeDialog = false },
            onSelect = { packTypeLabel ->
                val target = selectedList ?: return@ChoosePackTypeSheet
                            showPackTypeDialog = false
                beginBoxSession(target, packTypeLabel)
            },
        )
    }

    if (showExistingBoxSheet && selectedList != null) {
        val target = selectedList!!
        ResumeExistingBoxSheet(
            packTypeLabel = pendingPackType,
            boxes = incompleteBoxes,
            creating = creating,
            onDismiss = {
                showExistingBoxSheet = false
                incompleteBoxes = emptyList()
                pendingPackType = null
            },
            onSelect = { summary -> resumeExistingBox(target, summary) },
        )
    }

    Box(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        Column(Modifier.fillMaxSize()) {
            if (profileName != null && onLogout != null) {
                WmsStaffHomeHeader(
                    displayName = profileName,
                    role = staffRole,
                    onLogout = onLogout,
                )
            } else {
                WmsLightHeader(
                    title = userName,
                    showBack = showBackNavigation,
                    onBack = onBack,
                )
            }

            WmsPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { loadLists(fromPullRefresh = true) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "My Packing Lists",
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = WmsColors.Navy,
                        )
                        Text(
                            "${lists.size} assignments",
                            fontSize = 13.sp,
                            color = WmsColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

            Button(
                onClick = {
                    if (lists.isNotEmpty()) {
                        selectedList = lists.first()
                        showPackTypeDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(26.dp)
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Start New Pack", fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                        }
                    }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search packing #, pick list, order…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
            )

            error?.let { WmsErrorBanner(it) }

                    if (loading && !refreshing) {
                        WmsListSkeleton(count = 3)
                    }

                    if (!loading || refreshing) {
                        filtered.forEach { item ->
                            val id = item.id
                            val isPacked = item.isPackingQtyComplete || item.isPackingStatusPacked
                val isExpanded = expanded[id] == true

                            PackingPickListAccordionCard(
                                item = item,
                                isPacked = isPacked,
                                isExpanded = isExpanded,
                                onCardClick = {
                                    if (isPacked) {
                                        onOpenPackageTree(item)
                                    } else {
                                        expanded[id] = !(expanded[id] == true)
                                    }
                                },
                                onStartPack = { onStartPack(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun WmsPackingPickListItem.cardStatusLabel(): String {
    if (isPackingStatusPacked) return "PACKED"
    if (isPackingQtyComplete) return "PACKED"
    return displayPackingStatusLabel
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoosePackTypeSheet(
    creating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Choose Pack Type",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextPrimary,
            )
            Text(
                text = "Select package level to generate SSCC",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PackTypeOptionCard(
                    title = "Secondary",
                    subtitle = "Carton",
                    accent = WmsColors.Navy,
                    icon = Icons.Default.LocalShipping,
                    enabled = !creating,
                    onClick = { onSelect("Secondary") },
                    modifier = Modifier.weight(1f),
                )
                PackTypeOptionCard(
                    title = "Tertiary",
                    subtitle = "Pallet",
                    accent = WmsColors.Success,
                    icon = Icons.Default.Inventory2,
                    enabled = !creating,
                    onClick = { onSelect("Tertiary") },
                    modifier = Modifier.weight(1f),
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !creating,
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResumeExistingBoxSheet(
    packTypeLabel: String?,
    boxes: List<WmsPackingBoxSummary>,
    creating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (WmsPackingBoxSummary) -> Unit,
) {
    var showCreateNewWarning by remember { mutableStateOf(false) }
    val typeLabel = packTypeLabel ?: "package"
    val title = if (packTypeLabel != null) "Resume $packTypeLabel" else "Resume Package"

    if (showCreateNewWarning) {
        AlertDialog(
            onDismissRequest = { showCreateNewWarning = false },
            title = { Text("Complete open packages") },
            text = {
                Text(
                    "You have ${boxes.size} open ${typeLabel.lowercase()}(s) that are not completed. " +
                        "Resume and complete one before creating a new package.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showCreateNewWarning = false }) { Text("OK") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = null,
    ) {
                Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextPrimary,
            )
            Text(
                text = "Open ${typeLabel.lowercase()} boxes found. Resume one to continue packing.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                boxes.forEach { box ->
                    Row(
                        modifier = Modifier
                        .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                            .clickable(enabled = !creating) { onSelect(box) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = box.displayTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WmsColors.TextPrimary,
                            )
                            val subtitle = buildList {
                                box.packType?.takeIf { it.isNotBlank() }?.let { add(it) }
                                box.status?.takeIf { it.isNotBlank() }?.let { add(it) }
                                if (box.resolvedSscc.isNotEmpty()) add(box.resolvedSscc)
                            }.joinToString(" · ")
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                fontSize = 12.sp,
                                color = WmsColors.TextSecondary,
                            )
                            }
                        }
                        Text(
                            text = box.packTypeUiLabel(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WmsColors.Navy,
                            modifier = Modifier
                                .background(WmsColors.TabInactiveBg, RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            if (packTypeLabel != null) {
                Button(
                    onClick = { showCreateNewWarning = true },
                    enabled = !creating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Create New $packTypeLabel", fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !creating,
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PackTypeOptionCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(38.dp)
                .background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }

        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.TextPrimary,
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = WmsColors.TextSecondary,
        )
    }
}

@Composable
private fun PackingMetaChip(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF374151),
        modifier = Modifier
            .background(WmsColors.TabInactiveBg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        maxLines = 1,
    )
}

@Composable
private fun PackingLineRow(line: WmsPackingPickListLine, index: Int) {
    val required = line.requiredCount
    val packed = line.packedCount
    val status = when {
        required <= 0 -> "—"
        packed >= required -> "PACKED"
        packed > 0 -> "PARTIAL"
        else -> "PENDING"
    }
    val statusColor = when (status) {
        "PACKED" -> WmsColors.Success
        "PARTIAL" -> WmsColors.Warning
        else -> WmsColors.TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(22.dp)
                .background(WmsColors.Navy.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = line.productName.orEmpty().ifBlank { line.productSku.orEmpty() },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = WmsColors.TextPrimary,
            )
            Text(
                text = "SKU ${line.productSku.orEmpty()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
            )
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "$packed/$required",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextPrimary,
            )
            Text(
                text = status,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun PackingPickListAccordionCard(
    item: WmsPackingPickListItem,
    isPacked: Boolean,
    isExpanded: Boolean,
    onCardClick: () -> Unit,
    onStartPack: () -> Unit,
) {
    val accent = if (isPacked) WmsColors.Success else WmsColors.Navy
    val statusLabel = item.cardStatusLabel()
    val lines = item.lineCount ?: item.packLines.size
    val packed = item.resolvedTotalPackedQty
    val total = item.resolvedTotalRequestedQty

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .clickable { onCardClick() },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxSize()
                    .background(accent),
            )

            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            item.displayTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = WmsColors.Navy,
                            maxLines = 1,
                        )
                        val subtitle = item.pickListNumber?.takeIf { it.isNotBlank() }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = WmsColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }

                    Text(
                        statusLabel.replace("_", " "),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    item.orderNumber?.takeIf { it.isNotBlank() }?.let { PackingMetaChip(it) }
                    item.toteNumber?.takeIf { it.isNotBlank() }?.let { PackingMetaChip(it) }
                    item.stagingLocationName?.takeIf { it.isNotBlank() }?.let { PackingMetaChip(it) }
                    item.packingProgress?.takeIf { it.isNotBlank() }?.let { PackingMetaChip(it.replace("_", " ").uppercase()) }
                }

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$lines lines · $packed/$total packed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WmsColors.TextSecondary,
                    )
                    Spacer(Modifier.weight(1f))

                    if (isPacked) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = WmsColors.TextMuted)
                    } else {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = WmsColors.TextMuted,
                        )
                    }
                }
            }
        }

        if (isExpanded && !isPacked) {
            Divider()
            item.packLines.forEachIndexed { index, line ->
                PackingLineRow(line = line, index = index)
                if (index != item.packLines.lastIndex) {
                    Divider()
                }
            }

            Button(
                onClick = onStartPack,
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
            ) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Start Pack", fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            }
        }
    }
}
