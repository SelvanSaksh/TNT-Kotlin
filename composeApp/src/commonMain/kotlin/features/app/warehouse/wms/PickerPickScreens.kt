package features.app.warehouse.wms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.PickerMyListTask
import core.network.wms.WmsPatchPickListLineUpdate
import core.network.wms.WmsPatchPickListLinesRequest
import core.network.wms.WmsPickListLine
import core.network.wms.WmsPickListLinesPayload
import core.network.wms.WmsStagePickListRequest
import core.storage.SessionManager
import core.storage.getLocalStorage
import features.app.warehouse.WarehouseMlKitScanner
import features.app.warehouse.batchMatchesScan
import features.app.warehouse.productMatchesScan
import kotlinx.coroutines.launch

// ── Session models ────────────────────────────────────────────────────────────

enum class PickLineUiState { Completed, Active, Pending }

data class PickSessionLineUi(
    val id: String,
    val wms: WmsPickListLine,
    val state: PickLineUiState,
    val pickedCount: Int,
    val requiredCount: Int,
) {
    val locationLabel: String get() = wms.displayLocation
    val productName: String get() = wms.displayName
    val sku: String get() = wms.displaySku
    val instruction: String?
        get() = wms.pickInstruction?.trim()?.takeIf { it.isNotEmpty() }
}

object PickSessionSupport {
    fun buildSessionLines(lines: List<WmsPickListLine>): List<PickSessionLineUi> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedWith(
            compareBy<WmsPickListLine> { it.lineNumber ?: Int.MAX_VALUE }
                .thenBy { it.resolvedId },
        )
        var assignedActive = false
        return sorted.map { line ->
            val required = maxOf(line.resolvedRequestedQty(), 1)
            var picked = minOf(line.resolvedPickedQty(), required)
            val status = line.status.orEmpty().uppercase()
            if (status == "PICKED" || status == "COMPLETED") {
                picked = required
            }
            val isComplete = picked >= required
            val state = when {
                isComplete -> PickLineUiState.Completed
                !assignedActive -> {
                    assignedActive = true
                    PickLineUiState.Active
                }
                else -> PickLineUiState.Pending
            }
            PickSessionLineUi(
                id = line.resolvedId,
                wms = line,
                state = state,
                pickedCount = picked,
                requiredCount = required,
            )
        }
    }

    fun applyPickedQty(
        pickLineId: String,
        qty: Int,
        lines: List<PickSessionLineUi>,
    ): List<PickSessionLineUi> {
        val index = lines.indexOfFirst { it.id == pickLineId }
        if (index < 0) return lines
        val updated = lines.toMutableList()
        val required = updated[index].requiredCount
        val picked = qty.coerceIn(0, required)
        val isComplete = picked >= required
        val wms = updated[index].wms
        updated[index] = updated[index].copy(
            pickedCount = picked,
            state = if (isComplete) PickLineUiState.Completed else PickLineUiState.Active,
            wms = wms.copy(
                pickedQty = picked.toString(),
                status = if (isComplete) "PICKED" else wms.status,
            ),
        )
        return normalizeActiveLine(updated)
    }

    fun normalizeActiveLine(lines: List<PickSessionLineUi>): List<PickSessionLineUi> {
        var hasActive = false
        return lines.map { line ->
            if (line.state == PickLineUiState.Completed) return@map line
            if (!hasActive) {
                hasActive = true
                line.copy(state = PickLineUiState.Active)
            } else {
                line.copy(state = PickLineUiState.Pending)
            }
        }
    }

    fun allLinesComplete(lines: List<PickSessionLineUi>): Boolean =
        lines.isNotEmpty() && lines.all { it.state == PickLineUiState.Completed }

    fun nextIncompleteIndex(lines: List<PickSessionLineUi>, after: Int): Int? {
        if (after + 1 >= lines.size) return null
        for (i in (after + 1) until lines.size) {
            if (lines[i].state != PickLineUiState.Completed) return i
        }
        return null
    }

    fun firstIncompleteIndex(lines: List<PickSessionLineUi>): Int? =
        lines.indexOfFirst { it.state != PickLineUiState.Completed }.takeIf { it >= 0 }
}

// ── Picking flow root ─────────────────────────────────────────────────────────

@Composable
fun PickerPickingFlow(
    task: PickerMyListTask,
    toteNumber: String,
    skippedToteAssign: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    var scanLineId by remember { mutableStateOf<String?>(null) }
    var sessionLines by remember { mutableStateOf<List<PickSessionLineUi>>(emptyList()) }
    var wavePctComplete by remember { mutableIntStateOf(-1) }
    var waveLineCount by remember { mutableIntStateOf(-1) }
    var headerTitle by remember { mutableStateOf(task.title) }
    var resolvedTote by remember { mutableStateOf(toteNumber) }

    fun applySessionLines(updated: List<PickSessionLineUi>) {
        sessionLines = updated
        val total = waveLineCount.takeIf { it > 0 } ?: updated.size
        if (total > 0) {
            val completed = updated.count { it.state == PickLineUiState.Completed }
            wavePctComplete = ((completed.toFloat() / total) * 100).toInt()
        }
    }

    if (scanLineId != null) {
        val startIndex = sessionLines.indexOfFirst { it.id == scanLineId }.coerceAtLeast(0)
        PickerBoxScanScreen(
            task = task,
            headerTitle = headerTitle,
            toteNumber = resolvedTote,
            sessionLines = sessionLines,
            startIndex = startIndex,
            onBack = { scanLineId = null },
            onSessionLinesChange = ::applySessionLines,
            onLineConfirmed = {
                scanLineId = null
            },
            onAllLinesComplete = {
                scanLineId = null
                onFinished()
            },
        )
    } else {
        PickerStartPickingScreen(
            task = task,
            headerTitle = headerTitle,
            toteNumber = resolvedTote,
            sessionLines = sessionLines,
            wavePctComplete = wavePctComplete.takeIf { it >= 0 },
            waveLineCount = waveLineCount.takeIf { it >= 0 },
            onSessionLoaded = { lines, payload ->
                applySessionLines(lines)
                waveLineCount = payload.lineCount ?: lines.size
                payload.displayTitle.takeIf { it.isNotBlank() }?.let { headerTitle = it }
                payload.toteNumber?.takeIf { it.isNotBlank() }?.let { resolvedTote = it }
                if (payload.pctComplete != null) {
                    wavePctComplete = payload.pctComplete
                }
            },
            onBack = onBack,
            onScanLine = { lineId -> scanLineId = lineId },
            onAllLinesComplete = onFinished,
        )
    }
}

// ── Line list screen ──────────────────────────────────────────────────────────

@Composable
private fun PickerStartPickingScreen(
    task: PickerMyListTask,
    headerTitle: String,
    toteNumber: String,
    sessionLines: List<PickSessionLineUi>,
    wavePctComplete: Int?,
    waveLineCount: Int?,
    onSessionLoaded: (List<PickSessionLineUi>, WmsPickListLinesPayload) -> Unit,
    onBack: () -> Unit,
    onScanLine: (String) -> Unit,
    onAllLinesComplete: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun applyPayload(payload: WmsPickListLinesPayload) {
        val built = PickSessionSupport.buildSessionLines(payload.pickLines)
        onSessionLoaded(built, payload)
        if (PickSessionSupport.allLinesComplete(built)) {
            onAllLinesComplete()
        }
    }

    fun loadSession(fromPullRefresh: Boolean = false) {
        scope.launch {
            if (fromPullRefresh) {
                refreshing = true
            } else {
                loading = true
            }
            loadError = null
            if (companyId <= 0) {
                loadError = "Company is not set."
                loading = false
                refreshing = false
                return@launch
            }
            runCatching {
                repo.fetchPickListLines(task.pickListId, companyId)
            }.onSuccess { payload ->
                applyPayload(payload)
            }.onFailure {
                loadError = it.message ?: "Failed to load line items."
            }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(task.pickListId) { loadSession() }

    val completedCount = sessionLines.count { it.state == PickLineUiState.Completed }
    val totalLines = waveLineCount ?: maxOf(sessionLines.size, task.lineCount)
    val progressFraction = when {
        totalLines > 0 -> (completedCount.toFloat() / totalLines).coerceIn(0f, 1f)
        wavePctComplete != null -> (wavePctComplete / 100f).coerceIn(0f, 1f)
        else -> 0f
    }
    val progressPercent = if (sessionLines.isNotEmpty()) {
        (progressFraction * 100).toInt()
    } else {
        wavePctComplete ?: 0
    }
    val isFullyPicked = task.isPickedStatus ||
        (sessionLines.isNotEmpty() && sessionLines.all { it.state == PickLineUiState.Completed })

    Box(Modifier.fillMaxSize()) {
        WmsGridBackground()
        Column(Modifier.fillMaxSize()) {
        PickerSessionHeader(
            title = headerTitle,
            waveLabel = task.waveLabel,
            toteNumber = toteNumber,
            onBack = onBack,
        )

        WmsPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadSession(fromPullRefresh = true) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
            loading && sessionLines.isEmpty() && !refreshing -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PickerWaveProgressCard(
                        progressPercent = 0,
                        completedCount = 0,
                        totalLines = task.lineCount.coerceAtLeast(1),
                        progressFraction = 0f,
                    )
                    WmsLineListSkeleton(count = task.lineCount.coerceIn(2, 4))
                }
            }
            loadError != null && sessionLines.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(loadError!!, color = WmsColors.TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Retry",
                        color = WmsColors.Navy,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { loadSession() },
                    )
                }
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PickerWaveProgressCard(
                        progressPercent = progressPercent,
                        completedCount = completedCount,
                        totalLines = totalLines,
                        progressFraction = progressFraction,
                    )
                    if (sessionLines.isEmpty()) {
                        Text(
                            "No line items on this pick list.",
                            fontSize = 14.sp,
                            color = WmsColors.TextSecondary,
                        )
                    } else {
                        sessionLines.forEach { line ->
                            when (line.state) {
                                PickLineUiState.Completed -> PickerCompletedLineCard(line)
                                PickLineUiState.Active -> PickerActiveLineCard(
                                    line = line,
                                    showScanButton = !isFullyPicked,
                                    onScan = { onScanLine(line.id) },
                                )
                                PickLineUiState.Pending -> PickerPendingLineCard(line)
                            }
                        }
                    }
                }
            }
            }
        }
        }
    }
}

@Composable
private fun PickerSessionHeader(
    title: String,
    waveLabel: String?,
    toteNumber: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WmsBackButton(onClick = onBack)
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.Navy,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
        )
        waveLabel?.let {
            Text(
                it,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextSecondary,
                modifier = Modifier
                    .background(Color(0xFFF3F4F6), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        val tote = toteNumber.trim()
        if (tote.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .background(WmsColors.TabInactiveBg, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = WmsColors.Navy,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    tote,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = WmsColors.Navy,
                )
            }
        }
    }
}

@Composable
private fun PickerWaveProgressCard(
    progressPercent: Int,
    completedCount: Int,
    totalLines: Int,
    progressFraction: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "WAVE PROGRESS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.TextMuted,
            letterSpacing = 0.6.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$progressPercent% Complete",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$completedCount / $totalLines Lines",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = WmsColors.TextSecondary,
            )
        }
        WmsProgressBar(progressFraction)
    }
}

@Composable
private fun PickerLineCardShell(
    accentColor: Color,
    backgroundColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(208.dp)
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .background(backgroundColor, RoundedCornerShape(14.dp)),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxSize()
                .background(accentColor, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun PickerLocationBadge(text: String, fill: Color, textColor: Color) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = Modifier
            .background(fill, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun PickerCompletedLineCard(line: PickSessionLineUi) {
    PickerLineCardShell(accentColor = WmsColors.Success) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PickerLocationBadge(line.locationLabel, Color(0xFFD1FAE5), WmsColors.Success)
            Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(line.productName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary, maxLines = 2)
        Text("SKU: ${line.sku}", fontSize = 13.sp, color = WmsColors.TextSecondary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(
            "${line.pickedCount}/${line.requiredCount} Picked",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = WmsColors.Success,
        )
    }
}

@Composable
private fun PickerActiveLineCard(
    line: PickSessionLineUi,
    showScanButton: Boolean,
    onScan: () -> Unit,
) {
    PickerLineCardShell(accentColor = WmsColors.Navy) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PickerLocationBadge(line.locationLabel, WmsColors.TabInactiveBg, WmsColors.Navy)
            Text(
                "${line.requiredCount} Units Required",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(line.productName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary, maxLines = 2)
        Text("SKU: ${line.sku}", fontSize = 13.sp, color = WmsColors.TextSecondary, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                .dashedBorder(Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                line.instruction?.let { "Pick instruction: $it" }
                    ?: "Pick instruction: ${line.requiredCount} units from bin",
                fontSize = 12.sp,
                color = Color(0xFF374151),
                maxLines = 2,
            )
            if (showScanButton) {
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan item", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun PickerPendingLineCard(line: PickSessionLineUi) {
    val pending = maxOf(line.requiredCount - line.pickedCount, line.requiredCount)
    PickerLineCardShell(
        accentColor = WmsColors.Border,
        backgroundColor = Color.White.copy(alpha = 0.85f),
    ) {
        PickerLocationBadge(line.locationLabel, Color(0xFFF3F4F6), WmsColors.TextMuted)
        Spacer(Modifier.height(8.dp))
        Text(line.productName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextMuted, maxLines = 2)
        Text("SKU: ${line.sku}", fontSize = 13.sp, color = Color(0xFFD1D5DB), maxLines = 1)
        Text("Qty: ${line.requiredCount}", fontSize = 13.sp, color = WmsColors.TextMuted)
        Spacer(Modifier.weight(1f))
        Text("$pending Pending", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextMuted)
    }
}

// ── Scan screen ───────────────────────────────────────────────────────────────

@Composable
private fun PickerBoxScanScreen(
    task: PickerMyListTask,
    headerTitle: String,
    toteNumber: String,
    sessionLines: List<PickSessionLineUi>,
    startIndex: Int,
    onBack: () -> Unit,
    onSessionLinesChange: (List<PickSessionLineUi>) -> Unit,
    onLineConfirmed: () -> Unit,
    onAllLinesComplete: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }

    var currentIndex by remember(sessionLines, startIndex) { mutableIntStateOf(startIndex) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val completedCount = sessionLines.count { it.state == PickLineUiState.Completed }
    val line = sessionLines.getOrNull(currentIndex)

    if (line == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    fun confirmLine(pickLineId: String, qty: Int) {
        if (isSaving) return
        scope.launch {
            isSaving = true
            saveError = null
            val index = sessionLines.indexOfFirst { it.id == pickLineId }
            if (index < 0) {
                isSaving = false
                return@launch
            }
            val sessionLine = sessionLines[index]
            val required = sessionLine.requiredCount
            val newPicked = qty.coerceIn(0, required)
            val isComplete = newPicked >= required
            if (companyId <= 0) {
                saveError = "Company is not set."
                isSaving = false
                return@launch
            }
            runCatching {
                repo.patchPickListLines(
                    task.pickListId,
                    WmsPatchPickListLinesRequest(
                        companyId = companyId,
                        lines = listOf(
                            WmsPatchPickListLineUpdate(
                                pickLineId = pickLineId,
                                pickedQty = newPicked,
                                sourceLocationId = sessionLine.wms.sourceLocationId,
                                status = if (isComplete) "PICKED" else null,
                            ),
                        ),
                    ),
                )
                val updated = PickSessionSupport.applyPickedQty(pickLineId, newPicked, sessionLines)
                onSessionLinesChange(updated)
                if (PickSessionSupport.allLinesComplete(updated)) {
                    onAllLinesComplete()
                } else {
                    onLineConfirmed()
                }
            }.onFailure {
                saveError = it.message ?: "Could not save pick."
            }
            isSaving = false
        }
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        PickerScanHeader(
            zoneLabel = task.zone,
            pickListNumber = headerTitle,
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (sessionLines.size > 1) {
                PickerLineNavigator(
                    lineIndex = currentIndex,
                    lineCount = sessionLines.size,
                    canGoPrev = currentIndex > 0,
                    canGoNext = currentIndex < sessionLines.size - 1,
                    isSaving = isSaving,
                    onGoPrev = { if (currentIndex > 0 && !isSaving) currentIndex-- },
                    onGoNext = { if (currentIndex < sessionLines.size - 1 && !isSaving) currentIndex++ },
                )
            }
            PickerScanProgressCard(
                linesPicked = completedCount,
                totalLines = sessionLines.size,
                locationCode = line.locationLabel,
                toteNumber = toteNumber,
            )
            PickerScanProductCard(
                productName = line.productName,
                sku = line.sku,
                gtin = line.wms.scanGtin,
                batch = line.wms.scanBatchNumber,
                required = line.requiredCount,
            )
            PickerScanContent(
                line = line,
                isSaving = isSaving,
                onConfirmPick = ::confirmLine,
            )
            saveError?.let {
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF991B1B))
            }
        }
    }
}

@Composable
private fun PickerScanHeader(
    zoneLabel: String?,
    pickListNumber: String,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            WmsBackButton(onClick = onBack)
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    zoneLabel?.let { "ZONE $it" } ?: "PICK LIST",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF374151),
                )
                Text(
                    pickListNumber,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(WmsColors.Border))
    }
}

@Composable
private fun PickerLineNavigator(
    lineIndex: Int,
    lineCount: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    isSaving: Boolean,
    onGoPrev: () -> Unit,
    onGoNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Prev",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (canGoPrev && !isSaving) WmsColors.Navy else WmsColors.TextMuted,
            modifier = Modifier.clickable(enabled = canGoPrev && !isSaving) { onGoPrev() },
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            null,
            tint = if (canGoPrev && !isSaving) WmsColors.Navy else WmsColors.TextMuted,
            modifier = Modifier
                .size(18.dp)
                .clickable(enabled = canGoPrev && !isSaving) { onGoPrev() },
        )
        Text(
            "Line ${lineIndex + 1} of $lineCount",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF374151),
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "Next",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (canGoNext && !isSaving) WmsColors.Navy else WmsColors.TextMuted,
            modifier = Modifier.clickable(enabled = canGoNext && !isSaving) { onGoNext() },
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = if (canGoNext && !isSaving) WmsColors.Navy else WmsColors.TextMuted,
            modifier = Modifier
                .size(18.dp)
                .clickable(enabled = canGoNext && !isSaving) { onGoNext() },
        )
    }
}

@Composable
private fun PickerScanProgressCard(
    linesPicked: Int,
    totalLines: Int,
    locationCode: String,
    toteNumber: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "$linesPicked / ${maxOf(totalLines, 1)} Lines Picked",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1F2937),
        )
        if (locationCode.isNotBlank() && locationCode != "—") {
            Text(
                "Bin: $locationCode",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF64748B),
            )
        }
        val tote = toteNumber.trim()
        if (tote.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Inventory2, null, tint = WmsColors.Navy, modifier = Modifier.size(14.dp))
                Text(
                    tote,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = WmsColors.Navy,
                )
            }
        }
    }
}

@Composable
private fun PickerScanProductCard(
    productName: String,
    sku: String,
    gtin: String,
    batch: String,
    required: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "SCAN TO VERIFY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.6.sp,
            color = Color(0xFF334155),
        )
        Text(productName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
        Text("SKU: $sku", fontSize = 13.sp, color = Color(0xFF4B5563))
        Text("GTIN: ${gtin.ifBlank { "—" }}", fontSize = 13.sp, color = Color(0xFF4B5563))
        Text("Batch: ${batch.ifBlank { "—" }}", fontSize = 13.sp, color = Color(0xFF4B5563))
        Text("Required: $required", fontSize = 13.sp, color = Color(0xFF4B5563))
    }
}

@Composable
private fun PickerScanContent(
    line: PickSessionLineUi,
    isSaving: Boolean,
    onConfirmPick: (String, Int) -> Unit,
) {
    val gtin = line.wms.scanGtin
    val batch = line.wms.scanBatchNumber
    var scannedCode by remember(line.id) { mutableStateOf("") }
    var isScanning by remember(line.id) { mutableStateOf(true) }
    var productVerified by remember(line.id) { mutableStateOf(false) }
    var pickedQty by remember(line.id) { mutableIntStateOf(line.pickedCount.coerceIn(0, line.requiredCount)) }
    var qtyInput by remember(line.id) {
        mutableStateOf("1")
    }
    var showManualBatch by remember(line.id) { mutableStateOf(false) }
    var manualBatch by remember(line.id) { mutableStateOf("") }
    var scanError by remember(line.id) { mutableStateOf<String?>(null) }

    val remainingQty = maxOf(0, line.requiredCount - pickedQty)
    val resolvedPendingQty = run {
        val parsed = qtyInput.filter { it.isDigit() }.toIntOrNull() ?: 0
        if (remainingQty <= 0) 0 else minOf(remainingQty, maxOf(1, parsed))
    }

    fun markVerified() {
        scanError = null
        productVerified = true
        isScanning = false
        qtyInput = "1"
    }

    fun handleBarcodeScan(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || productVerified) return
        if (gtin.isBlank() || batch.isBlank()) {
            scanError = "GTIN or batch missing on this line."
            isScanning = true
            return
        }
        if (!productMatchesScan(trimmed, gtin, batch)) {
            scanError = "GTIN or batch does not match this product."
            scannedCode = ""
            isScanning = true
            return
        }
        markVerified()
    }

    fun submitManualBatch() {
        val trimmed = manualBatch.trim()
        if (trimmed.isEmpty()) return
        if (batch.isBlank()) {
            scanError = "Batch missing on this line."
            return
        }
        if (!batchMatchesScan(trimmed, batch)) {
            scanError = "Batch does not match."
            return
        }
        showManualBatch = false
        manualBatch = ""
        markVerified()
    }

    if (showManualBatch) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Enter Batch", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
            OutlinedTextField(
                value = manualBatch,
                onValueChange = { manualBatch = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type batch code", color = WmsColors.TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                ),
                shape = RoundedCornerShape(10.dp),
            )
            Button(
                onClick = ::submitManualBatch,
                enabled = manualBatch.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manualBatch.trim().isNotEmpty()) WmsColors.Navy else Color.Gray,
                ),
            ) {
                Text("Verify Batch", fontWeight = FontWeight.SemiBold)
            }
        }
    } else if (!productVerified) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, WmsColors.Border, RoundedCornerShape(14.dp)),
        ) {
            WarehouseMlKitScanner(
                enabled = isScanning && !isSaving,
                onBarcodeScanned = { handleBarcodeScan(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (productVerified) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "VERIFIED",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.2.sp,
                    color = WmsColors.Success,
                )
                Text(line.productName, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F2937), maxLines = 2)
                Text(
                    "GTIN $gtin · Batch $batch",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B),
                )
            }
            Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(28.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Enter pick quantity", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Default.RemoveCircle,
                    null,
                    tint = WmsColors.Navy,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = resolvedPendingQty > 1 && remainingQty > 0) {
                            val next = maxOf(1, resolvedPendingQty - 1)
                            qtyInput = next.toString()
                        },
                )
                OutlinedTextField(
                    value = qtyInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        qtyInput = digits
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
                Icon(
                    Icons.Default.AddCircle,
                    null,
                    tint = WmsColors.Navy,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = resolvedPendingQty < remainingQty && remainingQty > 0) {
                            val next = minOf(remainingQty, resolvedPendingQty + 1)
                            qtyInput = next.toString()
                        },
                )
            }
            Text("Remaining: $remainingQty", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary)
        }

        Button(
            onClick = {
                val add = resolvedPendingQty
                if (add > 0) {
                    onConfirmPick(line.id, minOf(line.requiredCount, pickedQty + add))
                }
            },
            enabled = remainingQty > 0 && !isSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (remainingQty > 0) WmsColors.Navy else Color.Gray),
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isSaving) "Saving…" else "Confirm Pick", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    if (!productVerified) {
        OutlinedButton(
            onClick = {
                if (showManualBatch) {
                    showManualBatch = false
                    manualBatch = ""
                    isScanning = true
                } else {
                    scanError = null
                    showManualBatch = true
                    isScanning = false
                }
            },
            enabled = !productVerified,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, WmsColors.Navy),
        ) {
            Icon(
                if (showManualBatch) Icons.Default.QrCodeScanner else Icons.Default.Keyboard,
                null,
                tint = Color(0xFF334155),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (showManualBatch) "Resume Scanning" else "Manual Entry",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155),
            )
        }
    }

    scanError?.let {
        Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF991B1B))
    }
}

// ── Staging screen ─────────────────────────────────────────────────────────────

@Composable
private fun PickerStagingScreen(
    task: PickerMyListTask,
    headerTitle: String,
    sessionLines: List<PickSessionLineUi>,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }

    var showStagingInput by remember { mutableStateOf(false) }
    var stagingLocation by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var stagingError by remember { mutableStateOf<String?>(null) }

    val completedCount = sessionLines.count { it.state == PickLineUiState.Completed }

    fun submitStaging(skipLocation: Boolean) {
        scope.launch {
            isSubmitting = true
            stagingError = null
            if (companyId <= 0) {
                stagingError = "Company is not set."
                isSubmitting = false
                return@launch
            }
            val trimmed = stagingLocation.trim()
            if (!skipLocation && showStagingInput && trimmed.length < 2) {
                stagingError = "Enter staging number or name."
                isSubmitting = false
                return@launch
            }
            runCatching {
                repo.stagePickList(
                    task.pickListId,
                    WmsStagePickListRequest(
                        companyId = companyId,
                        stagingLocationName = if (skipLocation) null else trimmed.takeIf { it.isNotEmpty() },
                    ),
                )
                onFinished()
            }.onFailure {
                stagingError = it.message ?: "Could not save staging."
            }
            isSubmitting = false
        }
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        PickerSessionHeader(
            title = headerTitle,
            waveLabel = task.waveLabel,
            toteNumber = "",
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(14.dp))
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "ALL ITEMS PICKED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Success,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Confirm staging area before finishing this pick list.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextPrimary,
                )
                Text(
                    "${sessionLines.size} lines · $completedCount/${sessionLines.size} complete",
                    fontSize = 13.sp,
                    color = WmsColors.TextSecondary,
                )
            }
            if (showStagingInput) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "STAGING AREA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.TextMuted,
                        letterSpacing = 0.6.sp,
                    )
                    OutlinedTextField(
                        value = stagingLocation,
                        onValueChange = { stagingLocation = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter staging number or name", color = WmsColors.TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
            stagingError?.let { WmsErrorBanner(it) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    if (showStagingInput) {
                        submitStaging(skipLocation = false)
                    } else {
                        showStagingInput = true
                    }
                },
                enabled = !isSubmitting && (!showStagingInput || stagingLocation.trim().length >= 2),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
            ) {
                Text("Confirm Staging Area", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = { submitStaging(skipLocation = true) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, WmsColors.Navy),
            ) {
                Text(
                    if (isSubmitting) "Saving…" else "Skip",
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Navy,
                )
            }
        }
    }
}

// ── UI helpers ────────────────────────────────────────────────────────────────

@Composable
private fun WmsGridBackground() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(WmsColors.PageBg))
        Canvas(Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            val gridColor = Color(0xFFE5E7EB).copy(alpha = 0.45f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 0.5f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 0.5f)
                y += step
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color, shape: RoundedCornerShape): Modifier = drawBehind {
    val stroke = Stroke(
        width = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
    )
    val corner = 10.dp.toPx()
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(corner, corner))
}
