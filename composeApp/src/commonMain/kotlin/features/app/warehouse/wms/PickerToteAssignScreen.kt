package features.app.warehouse.wms

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.PickerMyListTask
import core.network.wms.WmsPickListLine
import core.session.WarehouseAccess
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val Navy = Color(0xFF163C66)
private val SuccessGreen = Color(0xFF059669)
private val PageBg = Color(0xFFF5F6FA)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val Border = Color(0xFFE5E7EB)

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun PickerToteAssignScreen(
    task: PickerMyListTask,
    lines: List<WmsPickListLine> = emptyList(),
    onBack: () -> Unit,
    onContinue: (toteNumber: String, assignedToteId: Int?) -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val pickerId = remember { WmsSession.userId(session) }

    var inputMode by remember { mutableStateOf("manual") }
    var scannedCode by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var manualTote by remember { mutableStateOf("") }
    var confirmedTote by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showErrorPopup by remember { mutableStateOf(false) }
    var showAllLines by remember { mutableStateOf(false) }

    val preassignedTote = task.toteNumber?.trim()?.takeIf { it.length >= 2 }?.uppercase()
    val isAutoContinuing = preassignedTote != null && !loading

    fun showError(message: String) {
        error = message
        showErrorPopup = true
    }

    fun proceedToLineItems(toteNumber: String?) {
        scope.launch {
            loading = true
            error = null
            runCatching {
                if (task.pickListId.isBlank() || task.pickListId.any { !it.isDigit() }) {
                    error("Pick list id is invalid. Pull to refresh your lists and try again.")
                }
                val trimmedTote = toteNumber?.trim()?.takeIf { it.length >= 2 }
                val assignedId = if (trimmedTote != null) {
                    repo.assignTote(
                        pickListId = task.pickListId,
                        toteNumber = trimmedTote,
                        pickerId = pickerId,
                        markPreviousFilled = false,
                    ).resolvedToteId
                } else {
                    repo.startIndustryPickList(task.pickListId).resolvedToteId
                }
                onContinue(trimmedTote.orEmpty(), assignedId)
            }.onFailure {
                showError(it.message ?: "Could not assign tote.")
            }
            loading = false
        }
    }

    LaunchedEffect(preassignedTote) {
        preassignedTote?.let { proceedToLineItems(it) }
    }

    WmsRichErrorSheet(
        visible = showErrorPopup,
        title = "Could not continue",
        message = error.orEmpty(),
        onDismiss = { showErrorPopup = false },
    )

    Column(Modifier.fillMaxSize().background(PageBg)) {
        // ── Header ──
        PickerToteHeader(
            title = if (isAutoContinuing) "Start Picking" else "Assign Tote",
            subtitle = task.title,
            waveLabel = task.waveLabel,
            showSkip = !isAutoContinuing,
            skipLabel = if (loading) "Saving…" else "Skip",
            onBack = onBack,
            onSkip = { proceedToLineItems(null) },
            isAssigning = loading,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Error banner removed — shown via rich popup
            if (isAutoContinuing) {
                // Auto-continue view
                confirmedTote?.let { PickerVerifiedToteCard(tote = it) { confirmedTote = null } }
                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Navy, strokeWidth = 2.dp)
                        Text(
                            "Opening pick items…",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151),
                        )
                    }
                }
            } else if (confirmedTote != null) {
                // Verified tote
                PickerVerifiedToteCard(tote = confirmedTote!!) { confirmedTote = null }

                // Continue button
                Button(
                    onClick = { proceedToLineItems(confirmedTote) },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy),
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (loading) "Saving…" else "Continue to Pick Items",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            } else {
                // Mode picker
                PickerToteModePicker(
                    mode = inputMode,
                    onModeChange = { inputMode = it },
                )

                // Input section
                when (inputMode) {
                    "scan" -> {
                        Text(
                            "Point camera at tote / box barcode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                        )
                        PickerToteScanSection(
                            isScanning = isScanning,
                            onToggleScanning = { isScanning = it },
                            onCodeScanned = { code ->
                                val trimmed = code.trim()
                                if (trimmed.length >= 2) {
                                    confirmedTote = trimmed.uppercase()
                                    isScanning = false
                                } else {
                                    showError("Invalid tote barcode.")
                                }
                            },
                        )
                    }
                    "manual" -> {
                        PickerToteManualSection(
                            value = manualTote,
                            onValueChange = { manualTote = it.uppercase() },
                            onConfirm = {
                                val trimmed = manualTote.trim()
                                if (trimmed.length >= 2) {
                                    confirmedTote = trimmed.uppercase()
                                } else {
                                    showError("Enter at least 2 characters.")
                                }
                            },
                            enabled = manualTote.trim().length >= 2,
                        )
                    }
                }

                // Warehouse illustration
                PickerToteWarehouseIllustration()

                // Line items preview
                if (lines.isNotEmpty()) {
                    PickerToteLinePreview(
                        lines = lines,
                        isExpanded = showAllLines,
                        onToggleExpand = { showAllLines = !showAllLines },
                    )
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun PickerToteHeader(
    title: String,
    subtitle: String,
    waveLabel: String?,
    showSkip: Boolean,
    skipLabel: String,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    isAssigning: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        WmsCircularBackButton(onClick = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(
                subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            waveLabel?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF374151),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (showSkip) {
            Text(
                skipLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Navy,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE8EEF5))
                    .clickable(enabled = !isAssigning, onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    HorizontalDivider(color = Border)
}

// ── Mode picker ───────────────────────────────────────────────────────────────

@Composable
private fun PickerToteModePicker(
    mode: String,
    onModeChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE8EEF5))
            .padding(4.dp),
    ) {
        listOf(
            "scan" to "Scan Tote" to Icons.Default.QrCodeScanner,
            "manual" to "Enter Manually" to Icons.Default.Keyboard,
        ).forEach { (pair, icon) ->
            val (key, label) = pair
            val isSelected = mode == key
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Navy else Color.Transparent)
                    .clickable { onModeChange(key) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Navy,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Navy,
                )
            }
        }
    }
}

// ── Scan section ──────────────────────────────────────────────────────────────

@Composable
private fun PickerToteScanSection(
    isScanning: Boolean,
    onToggleScanning: (Boolean) -> Unit,
    onCodeScanned: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        WmsBarcodeCameraPreview(
            instruction = "Point camera at tote / box barcode",
            enabled = true,
            onBarcodeScanned = onCodeScanned,
        )
    }
}

// ── Manual section ────────────────────────────────────────────────────────────

@Composable
private fun PickerToteManualSection(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Tote number",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF374151),
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    "e.g. TOTE-00142",
                    color = TextMuted,
                    fontSize = 17.sp,
                )
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
            ),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                unfocusedIndicatorColor = Border,
                focusedIndicatorColor = Navy,
            ),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        )
        Button(
            onClick = onConfirm,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) Navy else Color.Gray,
            ),
        ) {
            Text(
                "Confirm Tote",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

// ── Verified tote card ────────────────────────────────────────────────────────

@Composable
private fun PickerVerifiedToteCard(
    tote: String,
    onChange: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SuccessGreen),
        )
        Spacer(Modifier.width(14.dp))
        Icon(
            Icons.Default.Inventory2,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "TOTE ASSIGNED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen,
                letterSpacing = 0.8.sp,
            )
            Text(
                tote,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
            )
        }
        if (onChange != null) {
            Text(
                "Change",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Navy,
                modifier = Modifier.clickable(onClick = onChange),
            )
        }
    }
}

// ── Warehouse illustration ────────────────────────────────────────────────────

@Composable
private fun PickerToteWarehouseIllustration() {
    val infiniteTransition = rememberInfiniteTransition(label = "warehouse_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val raw = phase

    // Phase progress
    val pickP = ease(clamp(raw, 0.00f, 0.22f) / 0.22f)
    val walkToP = ease(clamp(raw - 0.22f, 0.00f, 0.30f) / 0.30f)
    val placeP = ease(clamp(raw - 0.52f, 0.00f, 0.18f) / 0.18f)
    val walkBackP = ease(clamp(raw - 0.70f, 0.00f, 0.30f) / 0.30f)

    val personAtShelf = -0.32f
    val personAtTote = 0.32f
    val personX = when {
        raw < 0.22f -> personAtShelf
        raw < 0.52f -> lerp(personAtShelf, personAtTote, walkToP)
        raw < 0.70f -> personAtTote
        else -> lerp(personAtTote, personAtShelf, walkBackP)
    }

    val isWalkingRight = raw in 0.22f..0.52f
    val isWalkingBack = raw >= 0.70f

    val boxVisible = raw < 0.70f
    val boxX = when {
        raw < 0.22f -> personAtShelf
        raw < 0.52f -> lerp(personAtShelf, personAtTote, walkToP)
        else -> personAtTote
    }
    val boxY = when {
        raw < 0.22f -> lerp(0f, -0.04f, pickP)
        raw < 0.52f -> lerp(-0.04f, -0.05f, walkToP)
        else -> lerp(-0.05f, 0.03f, placeP)
    }
    val boxScale = if (raw < 0.22f) lerp(1f, 0.85f, pickP) else 0.85f

    val totePulse = if (raw in 0.52f..0.70f) lerp(1f, 1.08f, sin(placeP * 3.14159f)) else 1f
    val toteHasFill = raw >= 0.65f

    val stepText = when {
        raw < 0.22f -> "① Pick item from shelf"
        raw < 0.52f -> "② Walk to tote"
        raw < 0.70f -> "③ Place item in tote"
        else -> "④ Return for next item"
    }
    val stepColor = when {
        raw < 0.22f -> Color(0xFFD97706)
        raw < 0.52f -> Navy
        raw < 0.70f -> SuccessGreen
        else -> TextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFFEEF3FA), Color(0xFFF8FAFC)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Step label
        Text(
            stepText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = stepColor,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(stepColor.copy(alpha = 0.1f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(12.dp))

        // Scene illustration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            // Shelf (left)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("SHELF", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp, 36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.fillMaxWidth().height(1.5.dp).background(Border))
                        Box(Modifier.fillMaxWidth().height(1.5.dp).background(Border))
                        Box(Modifier.fillMaxWidth().height(1.5.dp).background(Border))
                    }
                }
            }

            // Tote (right)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .graphicsLayer { scaleX = totePulse; scaleY = totePulse },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("TOTE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SuccessGreen.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp, 36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (toteHasFill) Color(0xFFD1FAE5) else Color(0xFFF0FDF4))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = if (toteHasFill) SuccessGreen else Color(0xFF86EFAC),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Dashed path
            HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 70.dp),
                color = Border,
                thickness = 1.dp,
            )

            // Moving box
            if (boxVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = boxX * 200.dp.toPx()
                            translationY = boxY * 200.dp.toPx() - 10.dp.toPx()
                            scaleX = boxScale
                            scaleY = boxScale
                        },
                ) {
                    Text("📦", fontSize = 20.sp)
                }
            }

            // Person
            Text(
                "🧑",
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationX = personX * 200.dp.toPx()
                        translationY = 10.dp.toPx()
                        scaleX = if (isWalkingBack) -1f else 1f
                    },
            )
        }
    }
}

// ── Line preview ──────────────────────────────────────────────────────────────

@Composable
private fun PickerToteLinePreview(
    lines: List<WmsPickListLine>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val visibleLines = if (isExpanded) lines else lines.take(5)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Line items to pick",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        visibleLines.forEach { line ->
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
                        Text(name, fontSize = 12.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.displaySku, fontSize = 11.sp, color = TextMuted)
                        val batch = line.displayBatch
                        if (batch != "—") {
                            Text("· $batch", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
                Text(
                    "${line.resolvedPickedQty()}/${line.resolvedRequestedQty()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                )
            }
        }

        if (isExpanded && lines.size > 5) {
            Text(
                "Show less",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Navy,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(top = 2.dp),
            )
        } else if (!isExpanded && lines.size > 5) {
            Text(
                "Show all ${lines.size} lines",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Navy,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(top = 2.dp),
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun ease(t: Float): Float = t * t * (3 - 2 * t)
private fun clamp(value: Float, lo: Float, hi: Float): Float = minOf(hi, maxOf(lo, value))
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private val TextMuted = Color(0xFF9CA3AF)
