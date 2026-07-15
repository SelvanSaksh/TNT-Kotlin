package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import core.network.wms.PickerMyListTask
import core.network.wms.PickListDisplayStatus
import core.network.wms.PickListStatusFilter
import core.network.wms.WmsPickListLine
import core.network.wms.WmsUser
import core.network.wms.mapPickListToTask
import core.session.WarehouseAccess
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch

// ── Status Colors (iOS PickListDisplayStatus colors) ─────────────────────────

object PickListColors {
    // Created: Orange
    val CreatedAccent = Color(0xFFF59E0B)
    val CreatedBadgeFg = Color(0xFFB45309)
    val CreatedBadgeBg = Color(0xFFFFF7ED)
    val CreatedCardBg = Color(0xFFFAFAFA)
    val CreatedCardBorder = Color(0xFFFDE68A)

    // Assigned: Blue
    val AssignedAccent = Color(0xFF3B82F6)
    val AssignedBadgeFg = Color(0xFF1D4ED8)
    val AssignedBadgeBg = Color(0xFFEFF6FF)
    val AssignedCardBg = Color(0xFFFAFAFA)
    val AssignedCardBorder = Color(0xFFBFDBFE)

    // InProgress: Indigo
    val InProgressAccent = Color(0xFF6366F1)
    val InProgressBadgeFg = Color(0xFF4338CA)
    val InProgressBadgeBg = Color(0xFFEEF2FF)
    val InProgressCardBg = Color(0xFFFAFAFA)
    val InProgressCardBorder = Color(0xFFC7D2FE)

    // Picked: Green
    val PickedAccent = Color(0xFF10B981)
    val PickedBadgeFg = Color(0xFF047857)
    val PickedBadgeBg = Color(0xFFECFDF5)
    val PickedCardBg = Color(0xFFFAFAFA)
    val PickedCardBorder = Color(0xFFA7F3D0)

    // Staged: Emerald
    val StagedAccent = Color(0xFF059669)
    val StagedBadgeFg = Color(0xFF065F46)
    val StagedBadgeBg = Color(0xFFD1FAE5)
    val StagedCardBg = Color(0xFFFAFAFA)
    val StagedCardBorder = Color(0xFF6EE7B7)

    // Completed: Teal
    val CompletedAccent = Color(0xFF14B8A6)
    val CompletedBadgeFg = Color(0xFF0F766E)
    val CompletedBadgeBg = Color(0xFFF0FDFA)
    val CompletedCardBg = Color(0xFFF9FDFC)
    val CompletedCardBorder = Color(0xFF99F6E4)

    // Cancelled: Gray
    val CancelledAccent = Color(0xFF9CA3AF)
    val CancelledBadgeFg = Color(0xFF4B5563)
    val CancelledBadgeBg = Color(0xFFF9FAFB)
    val CancelledCardBg = Color(0xFFF9FAFB)
    val CancelledCardBorder = Color(0xFFE5E7EB)

    fun accentColor(status: PickListDisplayStatus): Color = when (status) {
        PickListDisplayStatus.Created -> CreatedAccent
        PickListDisplayStatus.Assigned -> AssignedAccent
        PickListDisplayStatus.InProgress -> InProgressAccent
        PickListDisplayStatus.Picked -> PickedAccent
        PickListDisplayStatus.Staged -> StagedAccent
        PickListDisplayStatus.Completed -> CompletedAccent
        PickListDisplayStatus.Cancelled -> CancelledAccent
        PickListDisplayStatus.Unknown -> WmsColors.TextMuted
    }

    fun badgeFg(status: PickListDisplayStatus): Color = when (status) {
        PickListDisplayStatus.Created -> CreatedBadgeFg
        PickListDisplayStatus.Assigned -> AssignedBadgeFg
        PickListDisplayStatus.InProgress -> InProgressBadgeFg
        PickListDisplayStatus.Picked -> PickedBadgeFg
        PickListDisplayStatus.Staged -> StagedBadgeFg
        PickListDisplayStatus.Completed -> CompletedBadgeFg
        PickListDisplayStatus.Cancelled -> CancelledBadgeFg
        PickListDisplayStatus.Unknown -> WmsColors.TextSecondary
    }

    fun badgeBg(status: PickListDisplayStatus): Color = when (status) {
        PickListDisplayStatus.Created -> CreatedBadgeBg
        PickListDisplayStatus.Assigned -> AssignedBadgeBg
        PickListDisplayStatus.InProgress -> InProgressBadgeBg
        PickListDisplayStatus.Picked -> PickedBadgeBg
        PickListDisplayStatus.Staged -> StagedBadgeBg
        PickListDisplayStatus.Completed -> CompletedBadgeBg
        PickListDisplayStatus.Cancelled -> CancelledBadgeBg
        PickListDisplayStatus.Unknown -> WmsColors.TabInactiveBg
    }

    fun cardBorder(status: PickListDisplayStatus): Color = when (status) {
        PickListDisplayStatus.Created -> CreatedCardBorder
        PickListDisplayStatus.Assigned -> AssignedCardBorder
        PickListDisplayStatus.InProgress -> InProgressCardBorder
        PickListDisplayStatus.Picked -> PickedCardBorder
        PickListDisplayStatus.Staged -> StagedCardBorder
        PickListDisplayStatus.Completed -> CompletedCardBorder
        PickListDisplayStatus.Cancelled -> CancelledCardBorder
        PickListDisplayStatus.Unknown -> WmsColors.Border
    }

    fun cardBg(status: PickListDisplayStatus): Color = when (status) {
        PickListDisplayStatus.Created -> CreatedCardBg
        PickListDisplayStatus.Assigned -> AssignedCardBg
        PickListDisplayStatus.InProgress -> InProgressCardBg
        PickListDisplayStatus.Picked -> PickedCardBg
        PickListDisplayStatus.Staged -> StagedCardBg
        PickListDisplayStatus.Completed -> CompletedCardBg
        PickListDisplayStatus.Cancelled -> CancelledCardBg
        PickListDisplayStatus.Unknown -> Color.White
    }
}

// ── Routes ───────────────────────────────────────────────────────────────────

private sealed class PickerRoute {
    data object Lists : PickerRoute()
    data class Tote(val task: PickerMyListTask) : PickerRoute()
    data class Session(
        val task: PickerMyListTask,
        val tote: String = "",
        val skippedToteAssign: Boolean = false,
    ) : PickerRoute()
}

// ── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun PickerWmsRoot(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
) {
    var route by remember { mutableStateOf<PickerRoute>(PickerRoute.Lists) }
    var listsRefreshToken by remember { mutableIntStateOf(0) }

    fun goToLists(refresh: Boolean = false) {
        if (refresh) listsRefreshToken++
        route = PickerRoute.Lists
    }

    when (val current = route) {
        PickerRoute.Lists -> PickerMyListsScreen(
            refreshToken = listsRefreshToken,
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            profileName = profileName,
            onLogout = onLogout,
        )
        is PickerRoute.Tote -> PickerToteAssignScreen(
            task = current.task,
            onBack = { goToLists(refresh = true) },
            onContinue = { tote ->
                route = PickerRoute.Session(current.task, tote, skippedToteAssign = false)
            },
        )
        is PickerRoute.Session -> PickerPickingFlow(
            task = current.task,
            toteNumber = current.tote,
            skippedToteAssign = current.skippedToteAssign,
            onBack = {
                if (current.skippedToteAssign) {
                    goToLists(refresh = true)
                } else {
                    route = PickerRoute.Tote(current.task)
                }
            },
            onFinished = { goToLists(refresh = true) },
        )
    }
}

// ── Main Lists Screen ───────────────────────────────────────────────────────

@Composable
fun PickerMyListsScreen(
    refreshToken: Int = 0,
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val access = remember { WarehouseAccess(session) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val userName = remember { WmsSession.userName(session) }
    val staffRole = remember { access.resolvedWarehouseStaffRole() }

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showErrorPopup by remember { mutableStateOf(false) }
    var allTasks by remember { mutableStateOf<List<PickerMyListTask>>(emptyList()) }
    var statusCounts by remember { mutableStateOf<Map<PickListStatusFilter, Int>>(emptyMap()) }
    var selectedFilter by remember { mutableStateOf(PickListStatusFilter.ALL) }
    var showAssignSheet by remember { mutableStateOf<PickerMyListTask?>(null) }
    var showLinesSheet by remember { mutableStateOf<PickerMyListTask?>(null) }
    val isWarehouseAdmin = remember { access.isWarehouseAdminRole }

    fun loadLists(fromPullRefresh: Boolean = false) {
        scope.launch {
            if (fromPullRefresh) refreshing = true else loading = true
            error = null
            if (companyId <= 0) {
                error = "Company not configured."
                showErrorPopup = true
                loading = false
                refreshing = false
                return@launch
            }
            runCatching {
                val payload = repo.fetchIndustryPickLists(companyId)
                val tasks = payload.pickLists.map(::mapPickListToTask)
                allTasks = tasks
                val counts = mutableMapOf<PickListStatusFilter, Int>()
                counts[PickListStatusFilter.ALL] = tasks.size
                for (filter in PickListStatusFilter.entries.filter { it != PickListStatusFilter.ALL }) {
                    counts[filter] = tasks.count { task ->
                        val displayStatus = task.displayStatus
                        PickListStatusFilter.fromDisplayStatus(displayStatus) == filter
                    }
                }
                statusCounts = counts
            }.onFailure {
                error = it.message ?: "Failed to load pick lists."
                showErrorPopup = true
            }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(companyId, refreshToken) { loadLists() }

    val filteredTasks = remember(allTasks, selectedFilter) {
        if (selectedFilter == PickListStatusFilter.ALL) allTasks
        else allTasks.filter { task ->
            PickListStatusFilter.fromDisplayStatus(task.displayStatus) == selectedFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WmsColors.PageBg),
    ) {
        PickerListsHeader(
            userName = userName,
            profileName = profileName,
            staffRole = staffRole,
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            onLogout = onLogout,
        )

        PickListSummaryBar(
            counts = statusCounts,
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
        )

        WmsRichErrorSheet(
            visible = showErrorPopup,
            title = "Could not load pick lists",
            message = error.orEmpty(),
            onDismiss = {
                showErrorPopup = false
                loadLists()
            },
        )

        WmsPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadLists(fromPullRefresh = true) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (loading && !refreshing) {
                WmsPickerHomeSkeleton()
            } else {
                    PickListCardList(
                        tasks = filteredTasks,
                        selectedFilter = selectedFilter,
                        isWarehouseAdmin = isWarehouseAdmin,
                        onAssignPicker = { showAssignSheet = it },
                        onViewLines = { showLinesSheet = it },
                    )
            }
        }

        // PickListBottomActions()
    }

    showAssignSheet?.let { task ->
        AssignPickerBottomSheet(
            task = task,
            session = session,
            companyId = companyId,
            repo = repo,
            onDismiss = { showAssignSheet = null },
            onAssigned = {
                showAssignSheet = null
                loadLists()
            },
        )
    }

    showLinesSheet?.let { task ->
        PickListLinesBottomSheet(
            task = task,
            onDismiss = { showLinesSheet = null },
        )
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun PickerListsHeader(
    userName: String,
    profileName: String?,
    staffRole: core.session.WarehouseStaffRole?,
    showBackNavigation: Boolean,
    onBack: () -> Unit,
    onLogout: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (showBackNavigation) {
            WmsCircularBackButton(onClick = onBack)
        }
        Text(
            "Pick Lists",
            modifier = Modifier.align(Alignment.Center),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.TextPrimary,
        )
        IconButton(
            onClick = { },
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF0F0F0)),
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = WmsColors.Navy,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Summary Bar (Status Filter Tabs) ────────────────────────────────────────

@Composable
private fun PickListSummaryBar(
    counts: Map<PickListStatusFilter, Int>,
    selectedFilter: PickListStatusFilter,
    onFilterSelected: (PickListStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PickListStatusFilter.entries.forEach { filter ->
            val count = counts[filter] ?: 0
            val isSelected = filter == selectedFilter
            PickListFilterChip(
                label = filter.label,
                count = count,
                isSelected = isSelected,
                filter = filter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun PickListFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    filter: PickListStatusFilter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = when (filter) {
        PickListStatusFilter.ALL -> Color(0xFF6B7280)
        PickListStatusFilter.PENDING -> Color(0xFFF59E0B)
        PickListStatusFilter.ASSIGNED -> Color(0xFF3B82F6)
        PickListStatusFilter.IN_PROGRESS -> Color(0xFF8B5CF6)
        PickListStatusFilter.COMPLETED -> Color(0xFF10B981)
    }
    val bgColor = if (isSelected) baseColor else Color.White
    val fgColor = if (isSelected) Color.White else baseColor
    val borderColor = if (isSelected) baseColor else baseColor.copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = fgColor,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = fgColor,
        )
    }
}

// ── Card List ───────────────────────────────────────────────────────────────

@Composable
private fun PickListCardList(
    tasks: List<PickerMyListTask>,
    selectedFilter: PickListStatusFilter,
    isWarehouseAdmin: Boolean,
    onAssignPicker: (PickerMyListTask) -> Unit,
    onViewLines: (PickerMyListTask) -> Unit,
) {
    if (tasks.isEmpty()) {
        PickListEmptyState(filter = selectedFilter)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            PickListCard(
                task = task,
                isWarehouseAdmin = isWarehouseAdmin,
                onAssignPicker = onAssignPicker,
                onViewLines = onViewLines,
            )
        }
    }
}

// ── Pick List Card (design/alignment refresh — icons added, no logic changes) ─

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickListCard(
    task: PickerMyListTask,
    isWarehouseAdmin: Boolean = false,
    onAssignPicker: (PickerMyListTask) -> Unit,
    onViewLines: (PickerMyListTask) -> Unit,
) {
    val status = task.displayStatus
    val accent = PickListColors.accentColor(status)
    val badgeFg = PickListColors.badgeFg(status)
    val badgeBg = PickListColors.badgeBg(status)
    val cardBorder = PickListColors.cardBorder(status)
    val cardBg = PickListColors.cardBg(status)
    val isCompleted = status == PickListDisplayStatus.Completed || status == PickListDisplayStatus.Cancelled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.5.dp, cardBorder, RoundedCornerShape(16.dp)),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(IntrinsicSize.Min)
                    .background(accent, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Title row: title/code on the left, status badge on the right ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            task.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = WmsColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (task.pickListCode != null && task.pickListCode != task.title) {
                            Text(
                                task.pickListCode,
                                fontSize = 13.sp,
                                color = WmsColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    PickListStatusBadge(
                        status = status,
                        accent = accent,
                        badgeFg = badgeFg,
                        badgeBg = badgeBg,
                    )
                }

                if (task.subtitle.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = WmsColors.TextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            task.subtitle,
                            fontSize = 13.sp,
                            color = WmsColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    val assignee = task.assigneeLabel
                    if (assignee != null) {
                        Text(
                            assignee,
                            fontSize = 13.sp,
                            color = WmsColors.TextPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                    } else if (isWarehouseAdmin) {
                        Text(
                            "Tap to assign",
                            fontSize = 13.sp,
                            color = accent,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onAssignPicker(task) },
                        )
                    } else {
                        Text(
                            "Unassigned",
                            fontSize = 13.sp,
                            color = WmsColors.TextMuted,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ListAlt,
                            contentDescription = null,
                            tint = WmsColors.TextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${task.lineCount} lines",
                            fontSize = 12.sp,
                            color = WmsColors.TextSecondary,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = WmsColors.TextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${task.itemCount} units",
                            fontSize = 12.sp,
                            color = WmsColors.TextSecondary,
                        )
                    }
                    if (isCompleted && task.itemCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = WmsColors.TextMuted,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                task.progressLabel,
                                fontSize = 12.sp,
                                color = WmsColors.TextSecondary,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            task.progressLabel,
                            fontSize = 12.sp,
                            color = WmsColors.TextMuted,
                        )
                        Text(
                            "${(task.progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(WmsColors.Border),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(task.progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent),
                        )
                    }
                }

                if (task.linePreview.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                tint = WmsColors.TextMuted,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Lines",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WmsColors.TextMuted,
                                letterSpacing = 0.3.sp,
                            )
                        }
                        task.linePreview.forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.6f))
                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val name = line.displayName
                                    if (name.isNotBlank() && name != line.displaySku) {
                                        Text(
                                            name,
                                            fontSize = 12.sp,
                                            color = WmsColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            line.displaySku,
                                            fontSize = 11.sp,
                                            color = WmsColors.TextMuted,
                                        )
                                        val batch = line.displayBatch
                                        if (batch != "—") {
                                            Text(
                                                "· $batch",
                                                fontSize = 11.sp,
                                                color = WmsColors.TextMuted,
                                            )
                                        }
                                        Text(
                                            "· Qty: ${line.displayQty}",
                                            fontSize = 11.sp,
                                            color = WmsColors.TextMuted,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = WmsColors.TextMuted,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        line.displayLocation,
                                        fontSize = 12.sp,
                                        color = WmsColors.TextSecondary,
                                        modifier = Modifier.padding(start = 2.dp),
                                    )
                                }
                            }
                        }
                        if (task.resolvedLines.size > 3) {
                            Text(
                                "View all ${task.resolvedLines.size} lines",
                                fontSize = 12.sp,
                                color = accent,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onViewLines(task) }
                                    .padding(top = 2.dp),
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    task.waveLabel?.let { label ->
                        PickListTag(label)
                    }
                    if (task.skuCount > 0) {
                        PickListTag("${task.pickedSkuCount}/${task.skuCount} SKUs")
                    }
                    if (task.batchCount > 0) {
                        PickListTag("${task.batchCount} batches")
                    }
                }
            }
        }
    }
}


@Composable
private fun PickListStatusBadge(
    status: PickListDisplayStatus,
    accent: Color,
    badgeFg: Color,
    badgeBg: Color,
) {
    val label = when (status) {
        PickListDisplayStatus.Created -> "CREATED"
        PickListDisplayStatus.Assigned -> "ASSIGNED"
        PickListDisplayStatus.InProgress -> "PICKING"
        PickListDisplayStatus.Picked -> "PICKED"
        PickListDisplayStatus.Staged -> "STAGED"
        PickListDisplayStatus.Completed -> "COMPLETED"
        PickListDisplayStatus.Cancelled -> "CANCELLED"
        PickListDisplayStatus.Unknown -> "UNKNOWN"
    }
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = badgeFg,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun PickListTag(label: String) {
    Text(
        text = label,
        fontSize = 11.sp,
        color = WmsColors.TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(WmsColors.TabInactiveBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun PickListEmptyState(filter: PickListStatusFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Inventory2,
            contentDescription = null,
            tint = WmsColors.TextMuted,
            modifier = Modifier.size(48.dp),
        )
        Text(
            when (filter) {
                PickListStatusFilter.ALL -> "No pick lists found"
                PickListStatusFilter.PENDING -> "No pending pick lists"
                PickListStatusFilter.ASSIGNED -> "No assigned pick lists"
                PickListStatusFilter.IN_PROGRESS -> "No pick lists in progress"
                PickListStatusFilter.COMPLETED -> "No completed pick lists"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = WmsColors.TextSecondary,
        )
        Text(
            when (filter) {
                PickListStatusFilter.ALL -> "No pick lists available for this warehouse."
                PickListStatusFilter.PENDING -> "All pick lists are assigned or in progress."
                PickListStatusFilter.ASSIGNED -> "No pick lists waiting for picker."
                PickListStatusFilter.IN_PROGRESS -> "No active picking sessions."
                PickListStatusFilter.COMPLETED -> "No pick lists completed yet."
            },
            fontSize = 14.sp,
            color = WmsColors.TextMuted,
            lineHeight = 20.sp,
        )
    }
}

// ── Bottom Actions Bar ──────────────────────────────────────────────────────

@Composable
private fun PickListBottomActions() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = { },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = WmsColors.Navy,
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New list", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = { },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Build wave", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Assign Picker Bottom Sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignPickerBottomSheet(
    task: PickerMyListTask,
    session: SessionManager,
    companyId: Int,
    repo: WmsRepository,
    onDismiss: () -> Unit,
    onAssigned: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var pickers by remember { mutableStateOf<List<WmsUser>>(emptyList()) }
    var loadingPickers by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching {
            pickers = repo.fetchPickers(companyId)
        }.onFailure {
            error = it.message ?: "Failed to load pickers."
        }
        loadingPickers = false
    }

    val filteredPickers = remember(pickers, searchQuery) {
        if (searchQuery.isBlank()) pickers
        else pickers.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Assign Picker",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = WmsColors.TextMuted)
                }
            }

            Text(
                task.title,
                fontSize = 14.sp,
                color = WmsColors.TextSecondary,
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search pickers…", color = WmsColors.TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WmsColors.Navy,
                    unfocusedBorderColor = WmsColors.Border,
                ),
            )

            error?.let {
                WmsErrorBanner(it)
            }

            if (loadingPickers) {
                Text("Loading pickers…", color = WmsColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredPickers) { picker ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        runCatching {
                                            val zone = task.zone?.trim().orEmpty()
                                            if (zone.isEmpty()) {
                                                error = "Pick list zone is missing."
                                                loading = false
                                                return@launch
                                            }
                                            repo.assignPickList(
                                                pickListId = task.pickListId,
                                                pickerId = picker.id,
                                            )
                                            onAssigned()
                                        }.onFailure {
                                            error = it.message ?: "Failed to assign."
                                        }
                                        loading = false
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WmsColors.Navy.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    picker.name.firstOrNull()?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    color = WmsColors.Navy,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                picker.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = WmsColors.TextPrimary,
                            )
                        }
                    }
                }
            }

            if (loading) {
                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                    enabled = false,
                ) {
                    Text("Assigning…", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Lines Bottom Sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickListLinesBottomSheet(
    task: PickerMyListTask,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sortMode by remember { mutableIntStateOf(0) }

    val lines: List<WmsPickListLine> = task.resolvedLines
    val sortedLines: List<WmsPickListLine> = remember(lines, sortMode) {
        when (sortMode) {
            1 -> lines.sortedBy { it.displayLocation }
            else -> lines
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Line Items",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = WmsColors.TextMuted)
                }
            }

            Text(task.title, fontSize = 14.sp, color = WmsColors.TextSecondary)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(WmsColors.TabInactiveBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val sortIcons = listOf(Icons.Default.ListAlt, Icons.Default.Place)
                listOf("Line Order", "By Location").forEachIndexed { index, label ->
                    val isSelected = sortMode == index
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { sortMode = index }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            sortIcons[index],
                            contentDescription = null,
                            tint = if (isSelected) WmsColors.Navy else WmsColors.TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) WmsColors.Navy else WmsColors.TextSecondary,
                        )
                    }
                }
            }

            HorizontalDivider(color = WmsColors.Border)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sortedLines, key = { it.resolvedId }) { line ->
                    PickListLineRow(line)
                }
            }
        }
    }
}

@Composable
private fun PickListLineRow(line: WmsPickListLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val name = line.displayName
            if (name.isNotBlank() && name != line.displaySku) {
                Text(
                    name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    line.displaySku,
                    fontSize = 12.sp,
                    color = WmsColors.TextSecondary,
                )
                val batch = line.displayBatch
                if (batch != "—") {
                    Text(
                        "· $batch",
                        fontSize = 12.sp,
                        color = WmsColors.TextSecondary,
                    )
                }
                Text(
                    "· Qty: ${line.displayQty}",
                    fontSize = 12.sp,
                    color = WmsColors.TextSecondary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = WmsColors.TextMuted,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                line.displayLocation,
                fontSize = 12.sp,
                color = WmsColors.TextMuted,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

// ── Tote Assign Screen (unchanged) ─────────────────────────────────────────

@Composable
private fun PickerToteAssignScreen(
    task: PickerMyListTask,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val pickerId = remember { WmsSession.userId(session) }

    var manualTote by remember { mutableStateOf("") }
    var confirmedTote by remember { mutableStateOf<String?>(null) }
    var inputMode by remember { mutableStateOf(WmsScanInputMode.Manual) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showErrorPopup by remember { mutableStateOf(false) }

    fun showError(message: String) {
        error = message
        showErrorPopup = true
    }

    fun confirmManualTote() {
        val trimmed = manualTote.trim()
        if (trimmed.length < 2) {
            showError("Enter at least 2 characters.")
            return
        }
        confirmedTote = trimmed.uppercase()
    }

    fun handleToteScan(code: String) {
        val trimmed = code.trim()
        if (trimmed.length < 2) {
            showError("Invalid tote barcode.")
            return
        }
        confirmedTote = trimmed.uppercase()
    }

    fun resetTote() {
        confirmedTote = null
        manualTote = ""
    }

    fun proceedToLineItems(toteNumber: String?) {
        scope.launch {
            loading = true
            error = null
            if (pickerId <= 0) {
                showError("Sign in again to continue.")
                loading = false
                return@launch
            }
            runCatching {
                val trimmedTote = toteNumber?.trim()?.takeIf { it.isNotEmpty() }
                if (trimmedTote != null) {
                    repo.assignTote(
                        pickListId = task.pickListId,
                        toteNumber = trimmedTote,
                        pickerId = pickerId,
                        markPreviousFilled = false,
                    )
                } else {
                    repo.startIndustryPickList(task.pickListId)
                }
                onContinue(trimmedTote.orEmpty())
            }.onFailure {
                showError(it.message ?: "Could not assign tote.")
            }
            loading = false
        }
    }

    WmsRichErrorSheet(
        visible = showErrorPopup,
        title = "Could not continue",
        message = error.orEmpty(),
        onDismiss = { showErrorPopup = false },
    )

    Column(Modifier.fillMaxSize().background(WmsColors.PageBg)) {
        WmsDetailHeader(
            title = "Assign Tote",
            subtitle = task.title,
            onBack = onBack,
            onSkip = { proceedToLineItems(null) },
            skipLabel = if (loading) "Saving…" else "Skip",
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            if (confirmedTote != null) {
                PickerVerifiedToteCard(
                    tote = confirmedTote!!,
                    onChange = { resetTote() },
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { proceedToLineItems(confirmedTote) },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                ) {
                    if (loading) {
                        Text("Saving…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Continue to Pick Items", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                WmsScanInputTabs(
                    mode = inputMode,
                    onModeChange = { inputMode = it },
                )

                Spacer(Modifier.height(16.dp))

                when (inputMode) {
                    WmsScanInputMode.Scan -> {
                        WmsBarcodeCameraPreview(
                            instruction = "Point camera at tote / box barcode",
                            enabled = !loading,
                            onBarcodeScanned = { handleToteScan(it) },
                        )
                    }
                    WmsScanInputMode.Manual -> {
                        WmsManualEntryCard(
                            label = "Tote number",
                            value = manualTote,
                            onValueChange = { manualTote = it.uppercase() },
                            placeholder = "e.g. TOTE-00142",
                            confirmLabel = "Confirm Tote",
                            enabled = manualTote.trim().length >= 2,
                            loading = false,
                            onConfirm = { confirmManualTote() },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                WmsWalkToToteFooter(stepLabel = "② Walk to tote")
            }
        }
    }
}

@Composable
private fun PickerVerifiedToteCard(
    tote: String,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .background(WmsColors.Success, RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Default.Inventory2,
            contentDescription = null,
            tint = WmsColors.Success,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "TOTE ASSIGNED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Success,
                letterSpacing = 0.8.sp,
            )
            Text(
                tote,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextPrimary,
            )
        }
        Text(
            "Change",
            modifier = Modifier.clickable(onClick = onChange),
            color = WmsColors.Navy,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}