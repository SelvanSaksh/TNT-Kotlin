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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.ui.draw.clip
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.WmsAddPackingBoxItemRequest
import core.network.wms.WmsCreatePackingBoxRequest
import core.network.wms.WmsCreatedPackingBox
import core.network.wms.toCreatedPackingBox
import core.network.wms.WmsPackingBoxSummary
import core.network.wms.WmsPackingPickListItem
import core.network.wms.WmsPackingPickListLine
import core.network.wms.WmsUpdatePackingPickListStatusRequest
import core.network.wms.WmsUpdatePackingBoxStatusRequest
import core.network.wms.WmsIndustryPackingStatus
import core.network.wms.packingBoxTypeConfig
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch

private enum class PackerListStatus(val label: String) {
    Pending("PENDING"),
    InProgress("IN PROGRESS"),
    Completed("COMPLETED"),
}

private fun statusForPackingItem(item: WmsPackingPickListItem): PackerListStatus {
    val statusLabel = item.displayPackingStatusLabel
    if (WmsIndustryPackingStatus.isListPacked(statusLabel) && item.isPackingQtyComplete) {
        return PackerListStatus.Completed
    }
    if (item.isPackingSessionActive) return PackerListStatus.InProgress
    return PackerListStatus.Pending
}

private fun shouldOfferFinishFlow(item: WmsPackingPickListItem): Boolean =
    !item.isPackingStatusPacked &&
        !item.hasOpenPackingBoxes &&
        (item.isReadyForPackerFinish || item.isPackingQtyComplete)

private fun latestOpenBox(boxes: List<WmsPackingBoxSummary>): WmsPackingBoxSummary? =
    boxes.maxByOrNull { openBoxSortKey(it) }

private fun openBoxSortKey(box: WmsPackingBoxSummary): String =
    box.packLabel?.trim().orEmpty().ifBlank { box.barcodeData?.trim().orEmpty() }.ifBlank { box.id }

private fun uiPackTypeForBox(box: WmsPackingBoxSummary): String =
    if (packingBoxTypeConfig(box.packType ?: "Secondary").second == 3) "Tertiary" else "Secondary"

private fun finishResumeTarget(allBoxes: List<WmsPackingBoxSummary>): Pair<WmsPackingBoxSummary, String>? {
    val candidates = allBoxes.filter { !it.isPackedListSummary }
    if (candidates.isEmpty()) return null

    latestOpenBox(candidates.filter { it.isTertiaryPackage && it.isInProgress })?.let {
        return it to "Tertiary"
    }
    latestOpenBox(candidates.filter { it.isTertiaryPackage })?.let {
        return it to "Tertiary"
    }
    latestOpenBox(candidates.filter { it.isSecondaryPackage && it.isCompleted })?.let {
        return it to "Secondary"
    }
    latestOpenBox(candidates.filter { it.isCompleted })?.let {
        return it to uiPackTypeForBox(it)
    }
    return null
}

private fun statusAccent(status: PackerListStatus): Color = when (status) {
    PackerListStatus.Pending -> Color(0xFFF59E0B)
    PackerListStatus.InProgress -> Color(0xFF1E3A5F)
    PackerListStatus.Completed -> Color(0xFF10B981)
}

private fun filterAccent(filter: String): Color = when (filter) {
    "Pending" -> Color(0xFFF59E0B)
    "In-Progress" -> Color(0xFF1E3A5F)
    "Completed" -> Color(0xFF10B981)
    else -> WmsColors.TextSecondary
}

private fun formatPackingTimestamp(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching {
        val instant = Instant.parse(value)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            .getOrElse(local.monthNumber - 1) { "???" }
        "$month ${local.dayOfMonth}, ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }.getOrElse { value.take(16).replace('T', ' ') }
}

private sealed class PackingRoute {
    data object Track : PackingRoute()
    data class BoxSession(
        val list: WmsPackingPickListItem,
        val box: WmsCreatedPackingBox,
        val packTypeLabel: String,
        val triggerNewPackage: Boolean = false,
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
            triggerNewPackageOnOpen = current.triggerNewPackage,
            onBack = { route = PackingRoute.Track },
            onAddQty = { line, latestBox ->
                route = PackingRoute.AddQty(current.list, latestBox, current.packTypeLabel, line)
            },
            onActiveBoxChanged = { activeBox ->
                val packType = if (
                    (activeBox.hierarchyLevel ?: 0) == 3 ||
                    activeBox.packType?.trim()?.uppercase() == "PALLET"
                ) {
                    "Tertiary"
                } else {
                    current.packTypeLabel
                }
                route = PackingRoute.BoxSession(
                    list = current.list,
                    box = activeBox,
                    packTypeLabel = packType,
                    triggerNewPackage = false,
                )
            },
            onCompletePackage = { route = PackingRoute.Track },
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
                route = PackingRoute.BoxSession(
                    list = current.list,
                    box = current.box,
                    packTypeLabel = current.packTypeLabel,
                    triggerNewPackage = true,
                )
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

    var selectedFilter by remember { mutableStateOf("All") }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lists by remember { mutableStateOf<List<WmsPackingPickListItem>>(emptyList()) }
    var showPackTypeDialog by remember { mutableStateOf(false) }
    var showExistingBoxSheet by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<WmsPackingPickListItem?>(null) }
    var incompleteBoxes by remember { mutableStateOf<List<WmsPackingBoxSummary>>(emptyList()) }
    var pendingPackType by remember { mutableStateOf<String?>(null) }
    var loadingBoxSheet by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    fun resumeExistingBox(list: WmsPackingPickListItem, summary: WmsPackingBoxSummary) {
        scope.launch {
            creating = true
            error = null
            runCatching {
                val payload = repo.fetchPackingPickListsByPacker(packerId, companyId)
                lists = payload.lists
                val fresh = payload.lists.find { it.id == list.id } ?: list
                onOpenBoxSession(fresh, summary.toCreatedPackingBox(), summary.packTypeUiLabel())
            }.onFailure { err -> error = err.message }
            showExistingBoxSheet = false
            incompleteBoxes = emptyList()
            pendingPackType = null
            loadingBoxSheet = false
            creating = false
        }
    }

    fun createNewPackageFromSheet(list: WmsPackingPickListItem, packTypeLabel: String) {
        scope.launch {
            creating = true
            error = null
            runCatching {
                val (packType, hierarchyLevel) = packingBoxTypeConfig(packTypeLabel)
                val box = repo.createPackingBoxWithFallback(
                    pickListId = list.pickListId,
                    packerId = packerId,
                    packTypeLabel = packTypeLabel,
                    legacyRequest = WmsCreatePackingBoxRequest(
                        companyId = companyId,
                        packingOrderId = list.packingOrderId ?: list.id,
                        packLabel = "${list.displayTitle}-$packTypeLabel",
                        packType = packType,
                        hierarchyLevel = hierarchyLevel,
                        createdBy = packerId,
                    ),
                )
                showExistingBoxSheet = false
                incompleteBoxes = emptyList()
                pendingPackType = null
                loadingBoxSheet = false
                onOpenBoxSession(list, box, packTypeLabel)
            }.onFailure { err -> error = err.message }
            creating = false
        }
    }

    fun beginNewBox(list: WmsPackingPickListItem) {
        selectedList = list
        showPackTypeDialog = true
    }

    fun resumePackingDirectly(list: WmsPackingPickListItem) {
        selectedList = list
        scope.launch {
            creating = true
            error = null
            val pickListId = list.pickListId?.trim().orEmpty()
            if (pickListId.isEmpty()) {
                beginNewBox(list)
                creating = false
                return@launch
            }
            runCatching {
                val payload = repo.fetchPackingPickListsByPacker(packerId, companyId)
                lists = payload.lists
                val fresh = payload.lists.find { it.id == list.id } ?: list
                selectedList = fresh

                val allBoxes = runCatching { repo.fetchIndustryPackingBoxes(pickListId) }
                    .getOrElse { fresh.boxes.orEmpty() }
                val openBoxes = allBoxes.filter { !it.isCompleted && !it.isPackedListSummary }

                val latestOpen = latestOpenBox(openBoxes)
                if (latestOpen != null) {
                    onOpenBoxSession(fresh, latestOpen.toCreatedPackingBox(), uiPackTypeForBox(latestOpen))
                    return@runCatching
                }

                if (shouldOfferFinishFlow(fresh)) {
                    finishResumeTarget(allBoxes)?.let { (box, packType) ->
                        onOpenBoxSession(fresh, box.toCreatedPackingBox(), packType)
                        return@runCatching
                    }
                }

                if (!fresh.isPackingQtyComplete) {
                    beginNewBox(fresh)
                    return@runCatching
                }

                finishResumeTarget(allBoxes)?.let { (box, packType) ->
                    onOpenBoxSession(fresh, box.toCreatedPackingBox(), packType)
                    return@runCatching
                }

                beginNewBox(fresh)
            }.onFailure { err -> error = err.message }
            creating = false
        }
    }

    fun continuePacking(list: WmsPackingPickListItem) {
        resumePackingDirectly(list)
    }

    fun beginBoxSession(list: WmsPackingPickListItem, packTypeLabel: String) {
        selectedList = list
        pendingPackType = packTypeLabel
        incompleteBoxes = emptyList()
        loadingBoxSheet = true
        showExistingBoxSheet = true
        scope.launch {
            creating = true
            error = null
            val (_, level) = packingBoxTypeConfig(packTypeLabel)
            runCatching {
                val pickListId = list.pickListId?.trim().orEmpty()
                val openBoxes = if (pickListId.isNotEmpty()) {
                    runCatching { repo.fetchIncompleteIndustryPackingBoxes(pickListId) }.getOrElse {
                        repo.fetchIncompletePackingBoxes(
                            companyId = companyId,
                            packingOrderId = list.packingOrderId,
                            pickListId = list.pickListId,
                            hierarchyLevel = level,
                        )
                    }
                } else {
                    repo.fetchIncompletePackingBoxes(
                        companyId = companyId,
                        packingOrderId = list.packingOrderId,
                        pickListId = list.pickListId,
                        hierarchyLevel = level,
                    )
                }
                loadingBoxSheet = false
                when {
                    openBoxes.isNotEmpty() -> {
                        incompleteBoxes = openBoxes
                    }
                    else -> {
                        showExistingBoxSheet = false
                        loadingBoxSheet = false
                        val (packType, hierarchyLevel) = packingBoxTypeConfig(packTypeLabel)
                        val box = repo.createPackingBoxWithFallback(
                            pickListId = list.pickListId,
                            packerId = packerId,
                            packTypeLabel = packTypeLabel,
                            legacyRequest = WmsCreatePackingBoxRequest(
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
            }.onFailure {
                error = it.message
                showExistingBoxSheet = false
                loadingBoxSheet = false
            }
            creating = false
        }
    }

    fun onStartPack(list: WmsPackingPickListItem) {
        if (list.isPackingSessionActive) {
            resumePackingDirectly(list)
        } else {
            beginNewBox(list)
        }
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

    val pendingCount = lists.count { statusForPackingItem(it) == PackerListStatus.Pending }
    val inProgressCount = lists.count { statusForPackingItem(it) == PackerListStatus.InProgress }
    val completedCount = lists.count { statusForPackingItem(it) == PackerListStatus.Completed }

    val filtered = lists.filter { item ->
        when (selectedFilter) {
            "Pending" -> statusForPackingItem(item) == PackerListStatus.Pending
            "In-Progress" -> statusForPackingItem(item) == PackerListStatus.InProgress
            "Completed" -> statusForPackingItem(item) == PackerListStatus.Completed
            else -> true
        }
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
            packTypeLabel = pendingPackType ?: "Package",
            boxes = incompleteBoxes,
            loading = loadingBoxSheet,
            creating = creating,
            onDismiss = {
                showExistingBoxSheet = false
                incompleteBoxes = emptyList()
                pendingPackType = null
                loadingBoxSheet = false
            },
            onSelect = { summary -> resumeExistingBox(target, summary) },
            onCreateNew = { createNewPackageFromSheet(target, pendingPackType ?: "Secondary") },
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        Column(Modifier.fillMaxSize()) {
            if (profileName != null && onLogout != null) {
                WmsPackerHomeHeader(
                    displayName = profileName,
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
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PackerStatusCard("Pending", pendingCount, Color(0xFFF59E0B), Icons.Default.AccessTime, Modifier.weight(1f))
                        PackerStatusCard("In Progress", inProgressCount, Color(0xFF1E3A5F), Icons.Default.Sync, Modifier.weight(1f))
                        PackerStatusCard("Completed", completedCount, Color(0xFF10B981), Icons.Default.CheckCircle, Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("All", "Pending", "In-Progress", "Completed").forEach { filter ->
                            val count = when (filter) {
                                "Pending" -> pendingCount
                                "In-Progress" -> inProgressCount
                                "Completed" -> completedCount
                                else -> lists.size
                            }
                            val selected = selectedFilter == filter
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) Color(0xFF1E3A5F) else Color.Transparent)
                                    .border(
                                        width = if (selected) 0.dp else 1.dp,
                                        color = Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(999.dp),
                                    )
                                    .clickable { selectedFilter = filter }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    filter,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else WmsColors.TextPrimary,
                                )
                                if (filter != "All") {
                                    Text(
                                        count.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else filterAccent(filter),
                                        modifier = Modifier
                                            .background(
                                                if (selected) Color.White.copy(alpha = 0.25f)
                                                else filterAccent(filter).copy(alpha = 0.15f),
                                                RoundedCornerShape(999.dp),
                                            )
                                            .padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    error?.let { WmsErrorBanner(it) }

                    if (loading && !refreshing) {
                        WmsListSkeleton(count = 3)
                    } else if (filtered.isEmpty()) {
                        Text(
                            "No packing lists in this category.",
                            color = WmsColors.TextSecondary,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        filtered.forEach { item ->
                            val status = statusForPackingItem(item)
                            val isCompleted = status == PackerListStatus.Completed
                            PackingPickListCard(
                                item = item,
                                status = status,
                                onCardClick = {
                                    if (isCompleted) {
                                        onOpenPackageTree(item)
                                    } else if (item.isPackingSessionActive) {
                                        resumePackingDirectly(item)
                                    } else {
                                        beginNewBox(item)
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

private fun WmsPackingPickListItem.cardStatusLabel(): String =
    when (statusForPackingItem(this)) {
        PackerListStatus.Completed -> "COMPLETED"
        PackerListStatus.InProgress -> "IN PROGRESS"
        PackerListStatus.Pending -> "PENDING"
    }

@Composable
private fun PackerStatusCard(
    title: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary)
    }
}

@Composable
private fun PackingMetaChipWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .background(Color(0xFFF1F5F9), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = Color(0xFF64748B), modifier = Modifier.size(11.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151), maxLines = 1)
    }
}

@Composable
private fun PackingPickListCard(
    item: WmsPackingPickListItem,
    status: PackerListStatus,
    onCardClick: () -> Unit,
    onStartPack: () -> Unit,
) {
    val accent = statusAccent(status)
    val isCompleted = status == PackerListStatus.Completed
    val lines = item.lineCount ?: item.packLines.size
    val packed = item.resolvedTotalPackedQty
    val total = item.resolvedTotalRequestedQty
    val progress = if (total > 0) (packed.toFloat() / total).coerceIn(0f, 1f) else 0f
    val hasInProgressBoxes = item.isPackingSessionActive
    val offerFinish = shouldOfferFinishFlow(item)
    val continueLabel = when {
        offerFinish -> "Finish Packing"
        hasInProgressBoxes -> "Continue Pack"
        else -> "Start Pack"
    }
    val continueButtonColor = if (offerFinish) Color(0xFF10B981) else Color(0xFF1E3A5F)
    val timestamp = formatPackingTimestamp(item.packedAt ?: item.packingStartedAt)
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), cardShape)
            .clickable(onClick = onCardClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.cardTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        status.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item.resolvedZoneCode?.let { PackingMetaChipWithIcon(Icons.Default.Place, it) }
                    item.toteNumber?.takeIf { it.isNotBlank() }?.let {
                        PackingMetaChipWithIcon(Icons.Outlined.Inventory2, it)
                    }
                    if (isCompleted) {
                        item.packingProgressLabel?.let {
                            PackingMetaChipWithIcon(Icons.Default.List, it)
                        }
                    }
                }
                item.stagingLocationName?.takeIf { it.isNotBlank() }?.let {
                    PackingMetaChipWithIcon(Icons.Default.Place, it)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "$packed/$total packed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (packed >= total && total > 0) Color(0xFF10B981) else WmsColors.TextPrimary,
                    )
                    timestamp?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(Icons.Default.AccessTime, null, tint = WmsColors.TextSecondary, modifier = Modifier.size(12.dp))
                            Text(it, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFE2E8F0)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.List, null, tint = WmsColors.TextSecondary, modifier = Modifier.size(12.dp))
                        Text(
                            "$lines line${if (lines == 1) "" else "s"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = WmsColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (!isCompleted) {
                        Button(
                            onClick = onStartPack,
                            colors = ButtonDefaults.buttonColors(containerColor = continueButtonColor),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                if (offerFinish) Icons.Default.CheckCircle else Icons.Default.Inventory2,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                continueLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
    }
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Choose Pack Type",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                )
                Text(
                    text = "Select package level to generate SSCC",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .clickable(enabled = !creating) { onDismiss() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 16.sp,
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
    packTypeLabel: String,
    boxes: List<WmsPackingBoxSummary>,
    loading: Boolean,
    creating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (WmsPackingBoxSummary) -> Unit,
    onCreateNew: () -> Unit,
) {
    var showCreateNewConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showCreateNewConfirm) {
        AlertDialog(
            onDismissRequest = { showCreateNewConfirm = false },
            title = { Text("Open boxes still in progress") },
            text = {
                Text(
                    "You have ${boxes.size} open ${packTypeLabel.lowercase()} box${if (boxes.size == 1) "" else "es"}. " +
                        "It is recommended to resume and complete an existing box before creating a new one.",
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateNewConfirm = false }) { Text("Resume Existing") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCreateNewConfirm = false
                    onCreateNew()
                }) { Text("Create New Anyway") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFF8FAFC),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Resume $packTypeLabel",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss, enabled = !creating) {
                    Text("Cancel", color = WmsColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = when {
                    loading -> "Loading open ${packTypeLabel.lowercase()} boxes…"
                    boxes.isEmpty() -> "No open ${packTypeLabel.lowercase()} boxes found."
                    else -> "${boxes.size} open ${packTypeLabel.lowercase()} box${if (boxes.size == 1) "" else "es"} found. Resume one or start a new package."
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator(color = WmsColors.Navy)
                    boxes.isEmpty() -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = WmsColors.TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                        Text("No open boxes", fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
                        Text(
                            "Create a new package to start packing",
                            fontSize = 13.sp,
                            color = WmsColors.TextSecondary.copy(alpha = 0.7f),
                        )
                    }
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        boxes.forEach { box -> ResumeBoxPickerRow(box = box, enabled = !creating, onSelect = onSelect) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            HorizontalDivider(color = WmsColors.Border)
            Button(
                onClick = {
                    if (boxes.isEmpty()) onCreateNew() else showCreateNewConfirm = true
                },
                enabled = !creating && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create New $packTypeLabel", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ResumeBoxPickerRow(
    box: WmsPackingBoxSummary,
    enabled: Boolean,
    onSelect: (WmsPackingBoxSummary) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onSelect(box) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(WmsColors.Navy.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Inventory2, null, tint = WmsColors.Navy, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(box.displayTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary, maxLines = 1)
            box.displaySubtitle.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "OPEN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    modifier = Modifier
                        .background(Color(0xFFD97706).copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (box.resolvedSscc.isNotBlank()) {
                    Text(
                        box.resolvedSscc,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = WmsColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Text(
            "Resume",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .background(WmsColors.Navy, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
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
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(cardShape)
            .background(Color.White)
            .border(1.5.dp, accent.copy(alpha = 0.25f), cardShape)
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextPrimary,
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
            )
        }
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
