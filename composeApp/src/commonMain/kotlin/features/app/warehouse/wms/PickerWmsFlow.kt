package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.PickerMyListTask
import core.network.wms.mapPickListToTask
import core.session.WarehouseAccess
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch

private sealed class PickerRoute {
    data object Lists : PickerRoute()
    data class Tote(val task: PickerMyListTask) : PickerRoute()
    data class Session(
        val task: PickerMyListTask,
        val tote: String = "",
        val skippedToteAssign: Boolean = false,
    ) : PickerRoute()
}

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
            onOpenTask = { task ->
                when {
                    task.isPickedStatus -> Unit
                    task.status == "IN_PROGRESS" -> {
                        route = PickerRoute.Session(
                            task = task,
                            tote = task.toteNumber.orEmpty(),
                            skippedToteAssign = true,
                        )
                    }
                    else -> route = PickerRoute.Tote(task)
                }
            },
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

@Composable
fun PickerMyListsScreen(
    refreshToken: Int = 0,
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
    onOpenTask: (PickerMyListTask) -> Unit,
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
    var priorityTask by remember { mutableStateOf<PickerMyListTask?>(null) }
    var assignedTasks by remember { mutableStateOf<List<PickerMyListTask>>(emptyList()) }
    var pickedTasks by remember { mutableStateOf<List<PickerMyListTask>>(emptyList()) }
    var lockedTasks by remember { mutableStateOf<List<PickerMyListTask>>(emptyList()) }
    var todoCount by remember { mutableIntStateOf(0) }
    var activeCount by remember { mutableIntStateOf(0) }
    var doneCount by remember { mutableIntStateOf(0) }
    var unitsCount by remember { mutableIntStateOf(0) }

    fun loadLists(fromPullRefresh: Boolean = false) {
        scope.launch {
            if (fromPullRefresh) {
                refreshing = true
            } else {
                loading = true
            }
            error = null
            val pickerId = WmsSession.userId(session)
            if (companyId <= 0) {
                error = "Company not configured."
                loading = false
                refreshing = false
                return@launch
            }
            if (pickerId <= 0) {
                error = "Sign in again to load your pick lists."
                loading = false
                refreshing = false
                return@launch
            }
            runCatching {
                val payload = repo.fetchPickListsByPicker(pickerId, companyId)
                val tasks = payload.pickLists.map(::mapPickListToTask)
                val open = tasks.filter { !it.isLocked && it.status !in setOf("COMPLETED", "CANCELLED") }
                val locked = tasks.filter { it.isLocked }
                val picked = open.filter { it.isPickedStatus }
                val activeWork = open.filter { !it.isPickedStatus }
                priorityTask = activeWork.firstOrNull { it.status == "IN_PROGRESS" }
                    ?: activeWork.maxByOrNull { it.priority }
                assignedTasks = activeWork.filter { it.id != priorityTask?.id }
                pickedTasks = picked
                lockedTasks = locked
                todoCount = payload.statusCounts?.assigned
                    ?: activeWork.count { it.status == "ASSIGNED" }
                activeCount = payload.statusCounts?.inProgress
                    ?: activeWork.count { it.status == "IN_PROGRESS" }
                val doneFromTasks = tasks.count { task ->
                    !task.isLocked && (task.isPickedStatus || task.status == "COMPLETED")
                }
                val doneFromApi = (payload.statusCounts?.picked ?: 0) +
                    (payload.statusCounts?.staged ?: 0) +
                    (payload.statusCounts?.completed ?: 0)
                doneCount = maxOf(doneFromTasks, doneFromApi)
                unitsCount = activeWork.sumOf { maxOf(it.itemCount - it.pickedCount, 0) }
            }.onFailure {
                error = it.message ?: "Failed to load pick lists."
            }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(companyId, refreshToken) { loadLists() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WmsColors.PageBg),
    ) {
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

        WmsStatsOverviewBar(
            todo = todoCount,
            active = activeCount,
            done = doneCount,
            units = unitsCount,
        )

        if (error != null) {
            WmsErrorBanner(error!!)
            Spacer(Modifier.height(8.dp))
        }

        WmsPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadLists(fromPullRefresh = true) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (loading && !refreshing) {
                    WmsPickerHomeSkeleton()
                } else {
            priorityTask?.let { task ->
                WmsSectionHeading("Priority Task")
                WmsPriorityTaskCard(
                    title = task.title,
                    itemCount = task.itemCount,
                    locationLine = task.locationLine,
                    onStart = { onOpenTask(task) },
                )
            }

            if (assignedTasks.isNotEmpty()) {
                WmsSectionHeading("Assigned")
                assignedTasks.forEach { task ->
                    PickerTaskCard(task) {
                        onOpenTask(task)
                    }
                }
            }

            if (pickedTasks.isNotEmpty()) {
                WmsSectionHeading("Picked", color = WmsColors.Success)
                pickedTasks.forEach { task ->
                    WmsPickedTaskCard(
                        title = task.title,
                        lineSummary = "${task.lineCount} lines • ${task.pickedCount}/${task.itemCount} units picked",
                    )
                }
            }

            if (lockedTasks.isNotEmpty()) {
                WmsSectionHeading("Locked")
                lockedTasks.forEach { task ->
                    PickerTaskCard(task, locked = true) {}
                }
            }

            if (!loading && !refreshing && priorityTask == null && assignedTasks.isEmpty() && pickedTasks.isEmpty()) {
                Text("No pick lists assigned.", color = WmsColors.TextSecondary, modifier = Modifier.padding(24.dp))
            }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PickerTaskCard(
    task: PickerMyListTask,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (!locked) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = WmsColors.TextMuted)
        }
        Text(task.locationLine, fontSize = 13.sp, color = WmsColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WmsColors.Navy)
            task.waveLabel?.let { Text(it, fontSize = 11.sp, color = WmsColors.TextMuted) }
        }
        WmsProgressBar(task.progress)
        Text("${task.pickedCount} / ${task.itemCount} units", fontSize = 12.sp, color = WmsColors.TextMuted)
        if (locked) task.lockReason?.let { Text(it, fontSize = 12.sp, color = WmsColors.Warning) }
    }
}

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

    fun confirmManualTote() {
        val trimmed = manualTote.trim()
        if (trimmed.length < 2) {
            error = "Enter at least 2 characters."
            return
        }
        error = null
        confirmedTote = trimmed.uppercase()
    }

    fun handleToteScan(code: String) {
        val trimmed = code.trim()
        if (trimmed.length < 2) {
            error = "Invalid tote barcode."
            return
        }
        error = null
        confirmedTote = trimmed.uppercase()
    }

    fun resetTote() {
        confirmedTote = null
        manualTote = ""
        error = null
    }

    fun proceedToLineItems(toteNumber: String?) {
        scope.launch {
            loading = true
            error = null
            val zone = task.zone?.trim().orEmpty()
            if (zone.isEmpty()) {
                error = "Pick list zone is missing."
                loading = false
                return@launch
            }
            if (pickerId <= 0) {
                error = "Sign in again to continue."
                loading = false
                return@launch
            }
            runCatching {
                val trimmedTote = toteNumber?.trim()?.takeIf { it.isNotEmpty() }
                repo.assignPickList(
                    pickListId = task.pickListId,
                    pickerId = pickerId,
                    zone = zone,
                    toteNumber = trimmedTote,
                )
                onContinue(trimmedTote.orEmpty())
            }.onFailure {
                error = it.message ?: "Could not assign tote."
            }
            loading = false
        }
    }

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

            error?.let {
                WmsErrorBanner(it)
                Spacer(Modifier.height(8.dp))
            }

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
