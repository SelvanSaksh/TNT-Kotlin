package features.app.warehouse.wms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import core.network.wms.PickerPickFilterCategory
import core.network.wms.pickerCardCategory
import core.network.wms.pickerStatusLabel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.PickerMyListTask
import core.network.wms.WmsPickListLine
import core.network.wms.mapPickListToTask
import core.session.WarehouseAccess
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.min

// ── Colors ────────────────────────────────────────────────────────────────────

private val Navy = Color(0xFF163C66)
private val SuccessGreen = Color(0xFF059669)
private val PageBg = Color(0xFFF5F6FA)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val TextMuted = Color(0xFF9CA3AF)
private val Border = Color(0xFFE5E7EB)

// ── Stats model ───────────────────────────────────────────────────────────────

private data class PickerStats(
    val todo: Int = 0,
    val active: Int = 0,
    val done: Int = 0,
    val units: Int = 0,
)

private enum class StatTab(val label: String) {
    TODO("To do"),
    ACTIVE("Active"),
    DONE("Done"),
    UNITS("Units"),
}

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun PickerHomeScreen(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
    onOpenTask: (PickerMyListTask) -> Unit = {},
    onGoToStaging: (PickerMyListTask) -> Unit = {},
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val access = remember { WarehouseAccess(session) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val userName = remember { WmsSession.userName(session) }
    val pickerId = remember { WmsSession.userId(session) }

    var isLoading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showLoadErrorPopup by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(PickerStats()) }
    var selectedTab by remember { mutableIntStateOf(StatTab.ACTIVE.ordinal) }
    var activePickLists by remember { mutableStateOf<List<PickerMyListTask>>(emptyList()) }
    var pickedTasks by remember { mutableStateOf<List<PickerMyListTask>>(emptyList()) }
    var completedTasks by remember { mutableStateOf<List<PickerMyListTask>>(emptyList()) }
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var previewTask by remember { mutableStateOf<PickerMyListTask?>(null) }

    fun toggleExpanded(taskId: String) {
        expandedIds = if (expandedIds.contains(taskId)) expandedIds - taskId else expandedIds + taskId
    }

    fun loadLists(fromPullRefresh: Boolean = false) {
        scope.launch {
            if (fromPullRefresh) refreshing = true else isLoading = true
            loadError = null
            if (pickerId <= 0) {
                loadError = "Sign in again to view your assigned pick lists."
                showLoadErrorPopup = true
                isLoading = false
                refreshing = false
                return@launch
            }
            runCatching {
                val payload = repo.fetchIndustryPickListsByPicker(pickerId, companyId)
                val tasks = payload.pickLists.map(::mapPickListToTask)
                applyPickerTasks(
                    tasks = tasks,
                    onStats = { stats = it },
                    onActivePickLists = { activePickLists = it },
                    onPicked = { pickedTasks = it },
                    onCompleted = { completedTasks = it },
                )
            }.onFailure {
                loadError = it.message ?: "Failed to load pick lists."
                showLoadErrorPopup = true
            }
            isLoading = false
            refreshing = false
        }
    }

    LaunchedEffect(companyId, pickerId) { loadLists() }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
    ) {
        PickerHomeHeader(
            userName = profileName?.takeIf { it.isNotBlank() } ?: userName,
            showBackNavigation = showBackNavigation,
            onBack = onBack,
        )

        PickerHomeStats(
            stats = stats,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )

        WmsRichErrorSheet(
            visible = showLoadErrorPopup,
            title = "Could not load pick lists",
            message = loadError.orEmpty(),
            onDismiss = {
                showLoadErrorPopup = false
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
            if (isLoading && !refreshing) {
                PickerHomeSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (activePickLists.isNotEmpty()) {
                        PickerHomeSectionHeader("My Pick Lists")
                        activePickLists.forEach { task ->
                            PickerPickListCard(
                                task = task,
                                lines = task.resolvedLines,
                                isExpanded = expandedIds.contains(task.id),
                                onToggleExpand = { toggleExpanded(task.id) },
                                onStartPicking = { onOpenTask(task) },
                            )
                        }
                    }

                    if (pickedTasks.isNotEmpty()) {
                        PickerHomeSectionHeader("Picked", color = SuccessGreen)
                        pickedTasks.forEach { task ->
                            PickerPickListCard(
                                task = task,
                                lines = task.resolvedLines,
                                isExpanded = expandedIds.contains(task.id),
                                onToggleExpand = { toggleExpanded(task.id) },
                                onStartPicking = { onOpenTask(task) },
                                onGoToStaging = { onGoToStaging(task) },
                                showStartButton = false,
                            )
                        }
                    }

                    if (completedTasks.isNotEmpty()) {
                        PickerHomeSectionHeader("Completed", color = SuccessGreen)
                        completedTasks.forEach { task ->
                            PickerCompletedTodayCard(
                                task = task,
                                lines = task.resolvedLines,
                                isExpanded = expandedIds.contains(task.id),
                                onToggleExpand = { toggleExpanded(task.id) },
                                onClick = { previewTask = task },
                            )
                        }
                    }

                    if (activePickLists.isEmpty() && pickedTasks.isEmpty() && completedTasks.isEmpty()) {
                        Text(
                            "No pick lists assigned to you right now.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    // ── Preview Sheet ──
    previewTask?.let { task ->
        PickerCompletedPreviewSheet(
            task = task,
            lines = task.resolvedLines,
            onDismiss = { previewTask = null },
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun PickerHomeHeader(
    userName: String,
    showBackNavigation: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBackNavigation) {
            WmsCircularBackButton(onClick = onBack)
            Spacer(Modifier.width(8.dp))
        }

        Text(
            userName,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Navy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Profile badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "PICKER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8EEF5)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    userName.take(2).uppercase().ifEmpty { "P" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy,
                )
            }
        }
    }
}

// ── Stats row ─────────────────────────────────────────────────────────────────

@Composable
private fun PickerHomeStats(
    stats: PickerStats,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            StatTab.entries.forEachIndexed { index, tab ->
                val value = when (tab) {
                    StatTab.TODO -> stats.todo
                    StatTab.ACTIVE -> stats.active
                    StatTab.DONE -> stats.done
                    StatTab.UNITS -> stats.units
                }
                val isSelected = selectedTab == index
                val accent = when (tab) {
                    StatTab.TODO -> TextPrimary
                    StatTab.ACTIVE -> Navy
                    StatTab.DONE -> SuccessGreen
                    StatTab.UNITS -> Color(0xFF374151)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        value.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
            }
        }
        HorizontalDivider(color = Border, thickness = 1.dp)
    }
}

// ── Pick list card (Admin Packing style) ──────────────────────────────────────

private fun PickerMyListTask.needsGoToStaging(): Boolean =
    status.equals("PICKED", ignoreCase = true)

@Composable
private fun PickerPickListCard(
    task: PickerMyListTask,
    lines: List<WmsPickListLine>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onStartPicking: () -> Unit,
    onGoToStaging: () -> Unit = {},
    showStartButton: Boolean = true,
) {
    val category = task.pickerCardCategory()
    val accent = Color(category.filterColorHex)
    val cardBg = when (category) {
        PickerPickFilterCategory.Pending -> Color(0xFFF9FAFB)
        PickerPickFilterCategory.InProgress -> Color(0xFFEFF6FF)
        PickerPickFilterCategory.Picked -> Color(0xFFECFDF5)
        PickerPickFilterCategory.Locked -> Color(0xFFF3F4F6)
    }
    val progress = if (task.itemCount > 0) task.pickedCount.toFloat() / task.itemCount else 0f
    val ctaLabel = when (category) {
        PickerPickFilterCategory.Picked -> "View Pick List"
        PickerPickFilterCategory.InProgress -> "Continue Picking"
        else -> "Start Picking"
    }
    val ctaIcon = when (category) {
        PickerPickFilterCategory.InProgress -> Icons.AutoMirrored.Filled.ArrowForward
        else -> Icons.Default.QrCodeScanner
    }

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
                        task.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (category == PickerPickFilterCategory.Locked) TextMuted else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            task.locationLine,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            maxLines = 2,
                        )
                    }
                }
                Text(
                    task.pickerStatusLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            if (category != PickerPickFilterCategory.Locked && task.itemCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${task.pickedCount} of ${task.itemCount} picked",
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
                            .background(Border),
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

            if (category == PickerPickFilterCategory.Locked) {
                Text(task.lockReason ?: "Unavailable", fontSize = 12.sp, color = TextMuted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.zone?.takeIf { it.isNotBlank() }?.let { PickerMetaChip(Icons.Default.Map, it) }
                    task.waveLabel?.takeIf { it.isNotBlank() }?.let { PickerMetaChip(Icons.Default.List, it) }
                    if (category == PickerPickFilterCategory.InProgress) {
                        task.toteNumber?.takeIf { it.isNotBlank() }?.let { PickerMetaChip(Icons.Default.Inventory2, it) }
                    }
                }
            }

            if (lines.isNotEmpty() && category != PickerPickFilterCategory.Locked) {
                PickerHomeLinesPreview(
                    lines = lines,
                    maxVisible = if (isExpanded) lines.size else 3,
                    onExpand = onToggleExpand,
                    showExpand = lines.size > 3,
                    isExpanded = isExpanded,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showStartButton && category != PickerPickFilterCategory.Locked && !task.isPickedStatus) {
                    Button(
                        onClick = onStartPicking,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Icon(ctaIcon, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(ctaLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else if (category == PickerPickFilterCategory.Picked && task.needsGoToStaging()) {
                    Button(
                        onClick = onGoToStaging,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Go to Staging", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(14.dp))
                    }
                } else if (category == PickerPickFilterCategory.Picked) {
                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                } else if (category == PickerPickFilterCategory.Locked) {
                    Icon(Icons.Default.Lock, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerHomeLinesPreview(
    lines: List<WmsPickListLine>,
    maxVisible: Int,
    onExpand: () -> Unit,
    showExpand: Boolean,
    isExpanded: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Line items",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 0.5.sp,
        )
        lines.take(maxVisible).forEach { line ->
            val required = line.resolvedRequestedQty()
            val picked = line.resolvedPickedQty()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF9FAFB))
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val name = line.displayName
                    if (name.isNotBlank() && name != line.displaySku) {
                        Text(
                            name,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.displaySku, fontSize = 11.sp, color = TextMuted)
                        val batch = line.displayBatch
                        if (batch != "—") {
                            Text("· $batch", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.displayLocation, fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$picked/$required",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                    )
                }
            }
        }
        when {
            showExpand -> {
                Text(
                    if (isExpanded) "Show less" else "Show all ${lines.size} lines",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Navy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExpand)
                        .padding(top = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PickerMetaChip(
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
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary, maxLines = 1)
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun PickerHomeSectionHeader(title: String, color: Color = TextPrimary) {
    Text(
        title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

// ── Shared button ─────────────────────────────────────────────────────────────

@Composable
private fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Navy else Navy.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun applyPickerTasks(
    tasks: List<PickerMyListTask>,
    onStats: (PickerStats) -> Unit,
    onActivePickLists: (List<PickerMyListTask>) -> Unit,
    onPicked: (List<PickerMyListTask>) -> Unit,
    onCompleted: (List<PickerMyListTask>) -> Unit,
) {
    val open = tasks.filter { it.status != "COMPLETED" && it.status != "CANCELLED" }
    val completed = tasks.filter { it.isCompletedStatus || it.status == "CANCELLED" }
    val picked = open.filter { it.isPickedStatus }
    val activeWork = open.filter { !it.isPickedStatus && !isTerminal(it.status) }

    var todo = 0
    var active = 0
    var units = 0
    for (t in activeWork) {
        if (t.hasAssignedTote) active++ else todo++
        units += maxOf(t.itemCount - t.pickedCount, 0)
    }

    onStats(
        PickerStats(
            todo = todo,
            active = active,
            done = completed.size + picked.size,
            units = units,
        )
    )

    onActivePickLists(
        activeWork.sortedWith(
            compareByDescending<PickerMyListTask> { it.hasAssignedTote }
                .thenBy { it.priority }
                .thenBy { it.title },
        ),
    )
    onPicked(picked.sortedBy { it.title })
    onCompleted(
        completed.sortedByDescending { it.resolvedCompletedTimestamp.orEmpty() },
    )
}

private fun formatPickerCompletedTime(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching {
        val local = Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault())
        val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            .getOrElse(local.monthNumber - 1) { "???" }
        "$month ${local.dayOfMonth}, ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }.getOrElse { value.take(16).replace('T', ' ') }
}

@Composable
private fun PickerCompletedTodayCard(
    task: PickerMyListTask,
    lines: List<WmsPickListLine>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit = {},
) {
    val completedLabel = formatPickerCompletedTime(task.resolvedCompletedTimestamp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFECFDF5))
            .border(1.5.dp, SuccessGreen.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(SuccessGreen),
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
                        task.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            task.locationLine,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            maxLines = 2,
                        )
                    }
                    completedLabel?.let {
                        Text(
                            "Completed $it",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessGreen,
                        )
                    }
                }
                Text(
                    "COMPLETED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                task.zone?.takeIf { it.isNotBlank() }?.let { PickerMetaChip(Icons.Default.Map, it) }
                task.waveLabel?.takeIf { it.isNotBlank() }?.let { PickerMetaChip(Icons.Default.List, it) }
                task.toteNumber?.takeIf { it.isNotBlank() }?.let { PickerMetaChip(Icons.Default.Inventory2, it) }
                task.stagingLocationName?.takeIf { it.isNotBlank() }?.let {
                    PickerMetaChip(Icons.Default.LocationOn, it)
                }
            }

            if (lines.isNotEmpty()) {
                PickerHomeLinesPreview(
                    lines = lines,
                    maxVisible = if (isExpanded) lines.size else 3,
                    onExpand = onToggleExpand,
                    showExpand = lines.size > 3,
                    isExpanded = isExpanded,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${task.pickedCount}/${task.itemCount} units",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun isTerminal(status: String): Boolean =
    status in setOf("COMPLETED", "CANCELLED", "PICKED", "STAGED")

// ── Completed Preview Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerCompletedPreviewSheet(
    task: PickerMyListTask,
    lines: List<WmsPickListLine>,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = null,
    ) {
        val completedLabel = formatPickerCompletedTime(task.resolvedCompletedTimestamp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFECFDF5))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            "Pick List Preview",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            "Completed${completedLabel?.let { " · $it" } ?: ""}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SuccessGreen,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            HorizontalDivider(color = Border)

            // ── Details ──
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Title & Location
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        task.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            task.locationLine,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                        )
                    }
                }

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PreviewStatBox("Lines", "${task.lineCount}", Color(0xFF2563EB), Modifier.weight(1f))
                    PreviewStatBox("Items", "${task.itemCount}", Color(0xFF7C3AED), Modifier.weight(1f))
                    PreviewStatBox("Picked", "${task.pickedCount}", SuccessGreen, Modifier.weight(1f))
                }

                // Meta Chips
                val metaChips = mutableListOf<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, Color>>()
                task.zone?.takeIf { it.isNotBlank() }?.let { metaChips.add(Triple(it, Icons.Default.Map, Color(0xFF059669))) }
                task.waveLabel?.takeIf { it.isNotBlank() }?.let { metaChips.add(Triple(it, Icons.Default.List, Color(0xFF7C3AED))) }
                task.toteNumber?.takeIf { it.isNotBlank() }?.let { metaChips.add(Triple(it, Icons.Default.Inventory2, Color(0xFFD97706))) }
                task.stagingLocationName?.takeIf { it.isNotBlank() }?.let { metaChips.add(Triple(it, Icons.Default.LocationOn, Color(0xFFDC2626))) }
                task.pickListCode?.takeIf { it.isNotBlank() }?.let { metaChips.add(Triple(it, Icons.Default.QrCodeScanner, Color(0xFF6366F1))) }

                if (metaChips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        metaChips.forEach { (label, icon, color) ->
                            PreviewMetaChip(icon = icon, label = label, color = color)
                        }
                    }
                }

                HorizontalDivider(color = Border)

                // ── Line Items ──
                if (lines.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "LINE ITEMS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.6.sp,
                        )
                        lines.forEach { line ->
                            PreviewLineItem(line)
                        }
                    }
                } else {
                    Text(
                        "No line item details available",
                        fontSize = 13.sp,
                        color = TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreviewStatBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.06f))
            .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PreviewMetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.06f))
            .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewLineItem(line: WmsPickListLine) {
    val productName = line.productName?.takeIf { it.isNotBlank() }
        ?: line.product?.resolvedName?.takeIf { it.isNotBlank() }
        ?: "Unknown Product"
    val sku = line.productSku?.takeIf { it.isNotBlank() }
        ?: line.product?.resolvedSku?.takeIf { it.isNotBlank() }
        ?: ""
    val location = line.locationCode?.takeIf { it.isNotBlank() }
        ?: line.locationName?.takeIf { it.isNotBlank() }
        ?: line.bin?.takeIf { it.isNotBlank() }
        ?: ""
    val requested = line.requestedQty?.toIntOrNull() ?: line.quantity ?: 0
    val picked = line.pickedQty?.toIntOrNull() ?: line.packedQty ?: 0
    val lineStatus = line.status?.trim()?.lowercase() ?: ""

    val statusColor = when {
        lineStatus == "completed" || lineStatus == "picked" -> SuccessGreen
        lineStatus == "exception" -> Color(0xFFD97706)
        else -> TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                productName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sku.isNotBlank()) {
                    Text(
                        "SKU: $sku",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                    )
                }
                if (location.isNotBlank()) {
                    Text(
                        "· $location",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "$picked/$requested",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                lineStatus.uppercase().ifBlank { "—" },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

// ── Skeleton loader ───────────────────────────────────────────────────────────

@Composable
private fun PickerHomeSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Stats skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(4) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SkeletonBar(modifier = Modifier.fillMaxWidth().height(10.dp))
                    SkeletonBar(modifier = Modifier.fillMaxWidth().height(18.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Priority card skeleton
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row {
                SkeletonBar(modifier = Modifier.weight(1f).height(20.dp))
                Spacer(Modifier.width(12.dp))
                SkeletonBar(modifier = Modifier.width(56.dp).height(14.dp))
            }
            SkeletonBar(modifier = Modifier.fillMaxWidth(0.6f).height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBar(modifier = Modifier.width(80.dp).height(24.dp), shape = RoundedCornerShape(12.dp))
                SkeletonBar(modifier = Modifier.width(60.dp).height(24.dp), shape = RoundedCornerShape(12.dp))
            }
            SkeletonBar(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
            SkeletonBar(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
            // Lines preview skeleton
            repeat(2) {
                SkeletonBar(modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp))
            }
            SkeletonBar(modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp))
        }

        // Section header skeleton
        SkeletonBar(modifier = Modifier.width(120.dp).height(18.dp))

        // Assigned cards skeleton
        repeat(2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkeletonBar(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
                        SkeletonBar(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    SkeletonBar(modifier = Modifier.size(14.dp))
                }
                SkeletonBar(modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(8.dp))
                SkeletonBar(modifier = Modifier.fillMaxWidth().height(38.dp), shape = RoundedCornerShape(8.dp))
            }
        }

        // Picked section skeleton
        SkeletonBar(modifier = Modifier.width(80.dp).height(18.dp))
        repeat(2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFECFDF5))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBar(modifier = Modifier.size(4.dp, 44.dp), shape = RoundedCornerShape(2.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        SkeletonBar(modifier = Modifier.weight(1f).height(14.dp))
                        Spacer(Modifier.width(8.dp))
                        SkeletonBar(modifier = Modifier.width(50.dp).height(16.dp), shape = RoundedCornerShape(12.dp))
                    }
                    SkeletonBar(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
                }
                Spacer(Modifier.width(8.dp))
                SkeletonBar(modifier = Modifier.size(22.dp), shape = CircleShape)
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFFE5E7EB).copy(alpha = 1f - alpha)),
    )
}
