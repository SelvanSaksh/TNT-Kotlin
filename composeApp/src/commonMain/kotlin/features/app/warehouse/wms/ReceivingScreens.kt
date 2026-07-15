package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import core.network.epcis.EpcisFlowService
import core.network.repository.WmsRepository
import core.network.wms.WmsPackingReceiverCompleteRequest
import core.network.wms.WmsPackingReceiverLine
import core.network.wms.WmsPackingReceiverNode
import core.network.wms.WmsPackingReceiverReceivingStatus
import core.network.wms.WmsPackingReceiverVerifyLineUpdate
import core.network.wms.WmsPackingReceiverVerifyLinesRequest
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.min

private val NavyGradientEnd = Color(0xFF1E4A7A)
private val CartonBg = Color(0xFFFFFBEB)
private val PageFill = Color(0xFFF8FAFC)
private val ChipBg = Color(0xFFF3F4F6)
private val ChipFg = Color(0xFF374151)
private val DetailBlockBg = Color(0xFFF9FAFB)

// ── List screen cards ─────────────────────────────────────────────────────────

@Composable
fun ReceivingListStatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 12.sp, color = WmsColors.TextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WmsColors.Navy)
    }
}

@Composable
fun PackingReceiverOrderCard(
    order: WmsPackingReceiverNode,
    onTap: () -> Unit,
) {
    val isReceived = order.isReceivedForDisplay()
    val canReceive = !isReceived && (order.totalPackedQty ?: 0) > 0
    val statusLabel = order.displayReceivingStatusLabel
    val statusAccent = receivingStatusAccent(statusLabel)
    val pickLists = order.pickListNodes

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = isReceived || canReceive, onClick = onTap),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(120.dp)
                .background(statusAccent),
        )
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        order.orderNumber ?: "Order",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = WmsColors.TextPrimary,
                    )
                    order.customerName?.trim()?.takeIf { it.isNotEmpty() }?.let { customer ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = WmsColors.TextSecondary, modifier = Modifier.size(12.dp))
                            Text(customer, fontSize = 10.sp, color = WmsColors.TextSecondary)
                        }
                    }
                }
                Text(
                    statusLabel.replace('_', ' '),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = statusAccent,
                    modifier = Modifier
                        .background(statusAccent.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            pickLists.forEach { pickList -> ReceivingPickListDetailBlock(pickList) }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReceivingMetricChip(
                    Icons.Default.Inventory2,
                    order.boxProgressLabel
                        ?: "${order.completedBoxCount ?: 0}/${order.totalBoxCount ?: 0} boxes",
                )
                order.receivingProgressLabel?.let { ReceivingMetricChip(Icons.Default.LocalShipping, it) }
                order.packedLineCount?.takeIf { it > 0 }?.let { ReceivingMetricChip(Icons.Default.List, "$it lines") }
            }

            if (isReceived || canReceive) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isReceived) "View package tree" else "Start receiving",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isReceived) WmsColors.Success else WmsColors.Navy,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = if (isReceived) WmsColors.Success else WmsColors.TextMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivingPickListDetailBlock(pickList: WmsPackingReceiverNode) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DetailBlockBg, RoundedCornerShape(8.dp))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.List, null, tint = WmsColors.Navy, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                pickList.pickListNumber ?: pickList.displayTitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = WmsColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            pickList.packingStatus?.trim()?.takeIf { it.isNotEmpty() }?.let { status ->
                Text(
                    status.replace('_', ' '),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = WmsColors.TextMuted,
                )
            }
        }
        if (pickList.resolvedPackerName.isNotEmpty()) {
            ReceivingInfoLine(Icons.Default.Inventory2, pickList.resolvedPackerName, WmsColors.Navy)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pickList.resolvedToteNumber?.let { ReceivingInfoLine(Icons.Default.ShoppingBasket, it, WmsColors.Warning) }
            pickList.resolvedStagingLocationName?.let { ReceivingInfoLine(Icons.Default.LocationOn, it, WmsColors.ActiveBlue) }
        }
        pickList.packingNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { packing ->
            ReceivingInfoLine(Icons.Default.Tag, packing, WmsColors.TextSecondary)
        }
    }
}

@Composable
private fun ReceivingInfoLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 10.sp, color = WmsColors.TextSecondary, maxLines = 1)
    }
}

@Composable
private fun ReceivingMetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(ChipBg, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Icon(icon, null, tint = ChipFg, modifier = Modifier.size(11.dp))
        Text(text, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ChipFg, maxLines = 1)
    }
}

private fun receivingStatusAccent(label: String): Color = when (label.uppercase()) {
    "RECEIVED", "COMPLETED" -> WmsColors.Success
    "NOT STARTED", "PENDING" -> WmsColors.Warning
    "READY" -> WmsColors.ActiveBlue
    else -> WmsColors.Navy
}

// ── Order detail screen ───────────────────────────────────────────────────────

@Composable
fun ReceivingOrderDetailScreen(
    order: WmsPackingReceiverNode,
    readOnly: Boolean,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val receiverId = remember { WmsSession.userId(session) }

    var lines by remember { mutableStateOf<List<WmsPackingReceiverLine>>(emptyList()) }
    var receivingStatus by remember { mutableStateOf<WmsPackingReceiverReceivingStatus?>(null) }
    var orderTree by remember { mutableStateOf<WmsPackingReceiverNode?>(null) }
    val receivedQty = remember { mutableStateMapOf<String, Int>() }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCompleteConfirm by remember { mutableStateOf(false) }

    val orderId = order.resolvedAPIOrderId.orEmpty()
    val displayOrder = orderTree ?: order

    val totalPackedUnits = lines.sumOf { it.resolvedPackedQty }
    val totalEnteredReceived = lines.sumOf { line -> receivedQty[line.id] ?: line.resolvedReceivedQty }
    val progress = if (totalPackedUnits > 0) min(1f, totalEnteredReceived.toFloat() / totalPackedUnits) else 0f

    val isSessionComplete = readOnly
        || receivingStatus?.isComplete == true
        || order.isReceivedForDisplay()

    val allLinesFullyReceived = lines.isNotEmpty() && lines.all { line ->
        val received = receivedQty[line.id] ?: line.resolvedReceivedQty
        line.resolvedPackedQty > 0 && received >= line.resolvedPackedQty
    }

    val hasValidationErrors = lines.any { line ->
        val received = receivedQty[line.id] ?: line.resolvedReceivedQty
        received > line.resolvedPackedQty
    }

    LaunchedEffect(orderId) {
        if (orderId.isBlank()) {
            error = "Order id is missing."
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val linesDeferred = async { repo.fetchPackingReceiverOrderLines(orderId, companyId) }
                val statusDeferred = async {
                    runCatching { repo.fetchPackingReceiverReceivingStatus(orderId, companyId) }.getOrNull()
                }
                val orderDeferred = async {
                    runCatching { repo.fetchPackingReceiverOrder(orderId, companyId) }.getOrNull()
                }
                lines = linesDeferred.await()
                receivingStatus = statusDeferred.await()
                orderTree = orderDeferred.await() ?: order
                lines.forEach { line -> receivedQty[line.id] = line.resolvedReceivedQty }
            }
        }.onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        WmsLightHeader(title = "Receive Order", showBack = true, onBack = onBack)

        if (loading && lines.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WmsColors.Navy)
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ReceivingOrderSummaryCard(
                    order = displayOrder,
                    lineCount = lines.size,
                    totalPacked = totalPackedUnits,
                    totalReceived = totalEnteredReceived,
                    isSessionComplete = isSessionComplete,
                    allLinesFullyReceived = allLinesFullyReceived,
                )
                ReceivingProgressCard(progress = progress, totalReceived = totalEnteredReceived, totalPacked = totalPackedUnits)
                error?.let { WmsErrorBanner(it) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!isSessionComplete && lines.isNotEmpty()) {
                        Text(
                            "Accept All",
                            color = WmsColors.Navy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                lines.forEach { line -> receivedQty[line.id] = line.resolvedPackedQty }
                            },
                        )
                    }
                }

                if (lines.isEmpty()) {
                    Text(
                        "No line items returned for this order.",
                        color = WmsColors.TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    ReceivingInteractivePackageTree(
                        order = displayOrder,
                        lines = lines,
                        receivedQty = receivedQty,
                        isSessionComplete = isSessionComplete,
                        onAdjust = { line, delta ->
                            val current = receivedQty[line.id] ?: line.resolvedReceivedQty
                            receivedQty[line.id] = (current + delta).coerceIn(0, line.resolvedPackedQty)
                        },
                        onSetQty = { line, qty ->
                            receivedQty[line.id] = qty.coerceIn(0, line.resolvedPackedQty)
                        },
                        onFill = { line -> receivedQty[line.id] = line.resolvedPackedQty },
                    )
                }
                Spacer(Modifier.height(if (isSessionComplete) 28.dp else 100.dp))
            }
        }

        if (!isSessionComplete) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(WmsColors.PageBgAlt)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Button(
                    onClick = { showCompleteConfirm = true },
                    enabled = allLinesFullyReceived && !hasValidationErrors && !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WmsColors.Navy,
                        disabledContainerColor = WmsColors.TextMuted,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Inventory2, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Complete Receiving", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = false },
            title = { Text("Complete Receiving") },
            text = { Text("Confirm all packed quantities have been received for this order.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCompleteConfirm = false
                        scope.launch {
                            saving = true
                            error = null
                            runCatching {
                                val updates = lines.map { line ->
                                    WmsPackingReceiverVerifyLineUpdate(
                                        pickLineId = line.resolvedPickLineId,
                                        receivedQty = receivedQty[line.id] ?: line.resolvedReceivedQty,
                                    )
                                }
                                if (updates.isNotEmpty()) {
                                    repo.verifyPackingReceiverLines(
                                        orderId,
                                        WmsPackingReceiverVerifyLinesRequest(companyId, receiverId, updates),
                                    )
                                }
                                repo.completePackingReceiverOrder(
                                    orderId,
                                    WmsPackingReceiverCompleteRequest(companyId, receiverId),
                                )
                                displayOrder?.let { tree ->
                                    val epcUris = EpcisFlowService.epcUrisFromReceiverNode(tree)
                                    if (epcUris.isNotEmpty()) {
                                        EpcisFlowService.receiveGoods(
                                            session = session,
                                            epcUris = epcUris,
                                            bizTransactionId = tree.orderNumber ?: orderId,
                                            receivingConfirmed = true,
                                        )
                                    }
                                }
                                onCompleted()
                            }.onFailure { error = it.message }
                            saving = false
                        }
                    },
                ) { Text("Complete") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ReceivingOrderSummaryCard(
    order: WmsPackingReceiverNode,
    lineCount: Int,
    totalPacked: Int,
    totalReceived: Int,
    isSessionComplete: Boolean,
    allLinesFullyReceived: Boolean,
) {
    val statusLabel = when {
        isSessionComplete -> "RECEIVED"
        allLinesFullyReceived -> "READY"
        totalReceived > 0 -> "PARTIAL"
        else -> order.orderStatus ?: "PENDING"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(WmsColors.Navy, NavyGradientEnd)))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ORDER", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White.copy(0.7f))
                Text(order.orderNumber ?: "Order", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                order.customerName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(0.85f))
                }
            }
            Text(
                statusLabel.replace('_', ' '),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .background(Color.White.copy(0.2f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReceivingSummaryMetric("Lines", lineCount.toString(), Modifier.weight(1f))
            ReceivingSummaryMetric("Packed", totalPacked.toString(), Modifier.weight(1f))
            ReceivingSummaryMetric("Received", totalReceived.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReceivingSummaryMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White.copy(0.12f), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(title.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White.copy(0.65f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun ReceivingProgressCard(progress: Float, totalReceived: Int, totalPacked: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("Receiving Progress", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = WmsColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "${(progress * 100).toInt()}%",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = WmsColors.Navy,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(WmsColors.Border),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (progress >= 1f) WmsColors.Success else WmsColors.Navy),
            )
        }
    }
}

// ── Interactive package tree ──────────────────────────────────────────────────

private enum class ReceivingLineStyle { Standalone, OnPallet, InCarton }

@Composable
fun ReceivingInteractivePackageTree(
    order: WmsPackingReceiverNode,
    lines: List<WmsPackingReceiverLine>,
    receivedQty: Map<String, Int>,
    isSessionComplete: Boolean,
    onAdjust: (WmsPackingReceiverLine, Int) -> Unit,
    onSetQty: (WmsPackingReceiverLine, Int) -> Unit,
    onFill: (WmsPackingReceiverLine) -> Unit,
) {
    var expandedIds by remember(order.id) { mutableStateOf(setOf<String>()) }

    LaunchedEffect(order.id) {
        val initial = order.pickListNodes.map { it.id }.toMutableSet()
        order.pickListNodes.forEach { pickList ->
            initial.add(pickList.id)
            pickList.childBoxes.forEach { box ->
                initial.add(box.id)
                box.childBoxes.forEach { carton -> initial.add(carton.id) }
            }
        }
        expandedIds = initial
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Package Tree", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReceivingLegendChip(WmsColors.Navy, Icons.Default.List, "Pick List")
                ReceivingLegendChip(WmsColors.Navy, Icons.Default.LocalShipping, "Pallet")
                ReceivingLegendChip(WmsColors.Warning, Icons.Default.Inventory2, "Carton")
                ReceivingLegendChip(WmsColors.ActiveBlue, Icons.Default.Inventory2, "Line Item")
            }
        }

        if (order.pickListNodes.isEmpty()) {
            ReceivingStandaloneLinesCard(
                title = "Line Items",
                lines = lines,
                receivedQty = receivedQty,
                isSessionComplete = isSessionComplete,
                style = ReceivingLineStyle.Standalone,
                onAdjust = onAdjust,
                onSetQty = onSetQty,
                onFill = onFill,
            )
        } else {
            order.pickListNodes.forEach { pickList ->
                ReceivingPickListSection(
                    pickList = pickList,
                    lines = lines,
                    receivedQty = receivedQty,
                    isSessionComplete = isSessionComplete,
                    expandedIds = expandedIds,
                    onToggle = { id -> expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id },
                    onAdjust = onAdjust,
                    onSetQty = onSetQty,
                    onFill = onFill,
                )
            }
        }
    }
}

@Composable
private fun ReceivingLegendChip(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(0.08f), RoundedCornerShape(50))
            .border(1.dp, color.copy(0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
    }
}

@Composable
private fun ReceivingPickListSection(
    pickList: WmsPackingReceiverNode,
    lines: List<WmsPackingReceiverLine>,
    receivedQty: Map<String, Int>,
    isSessionComplete: Boolean,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    onAdjust: (WmsPackingReceiverLine, Int) -> Unit,
    onSetQty: (WmsPackingReceiverLine, Int) -> Unit,
    onFill: (WmsPackingReceiverLine) -> Unit,
) {
    val isExpanded = pickList.id in expandedIds
    val pickListLines = receivingLinesForPickList(lines, pickList)
    val boxes = pickList.childBoxes
    val hasPallet = boxes.any { it.isPalletBox }
    val cartonBoxes = boxes.filter { !it.isPalletBox }
    val received = receivingReceivedTotal(pickListLines, receivedQty)
    val packed = receivingPackedTotal(pickListLines)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, WmsColors.Border, RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(pickList.id) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReceivingExpandIcon(isExpanded, WmsColors.Navy)
            Box(
                Modifier
                    .size(38.dp)
                    .background(WmsColors.Navy.copy(0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.List, null, tint = WmsColors.Navy, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(pickList.displayTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WmsColors.TextPrimary, maxLines = 1)
                Text(
                    "$received/$packed units received · ${boxes.size} pkg",
                    fontSize = 11.sp,
                    color = WmsColors.TextSecondary,
                    maxLines = 1,
                )
            }
            ReceivingMiniProgress(received, packed)
        }

        if (isExpanded) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 0.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                boxes.filter { it.isPalletBox }.forEach { pallet ->
                    ReceivingPalletSection(
                        pallet = pallet,
                        pickListLines = pickListLines,
                        receivedQty = receivedQty,
                        isSessionComplete = isSessionComplete,
                        expandedIds = expandedIds,
                        onToggle = onToggle,
                        onAdjust = onAdjust,
                        onSetQty = onSetQty,
                        onFill = onFill,
                    )
                }
                if (cartonBoxes.isNotEmpty()) {
                    ReceivingSectionLabel("Cartons", cartonBoxes.size, WmsColors.Warning)
                    cartonBoxes.forEach { carton ->
                        ReceivingCartonSection(
                            carton = carton,
                            pickListLines = receivingLinesForCarton(carton, pickListLines, cartonBoxes),
                            receivedQty = receivedQty,
                            isSessionComplete = isSessionComplete,
                            expandedIds = expandedIds,
                            onToggle = onToggle,
                            onAdjust = onAdjust,
                            onSetQty = onSetQty,
                            onFill = onFill,
                        )
                    }
                }
                val standalone = receivingStandaloneLines(pickListLines, boxes, hasPallet)
                if (standalone.isNotEmpty()) {
                    ReceivingStandaloneLinesCard(
                        title = if (hasPallet) "Loose Line Items" else "Line Items",
                        lines = standalone,
                        receivedQty = receivedQty,
                        isSessionComplete = isSessionComplete,
                        style = ReceivingLineStyle.Standalone,
                        onAdjust = onAdjust,
                        onSetQty = onSetQty,
                        onFill = onFill,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivingPalletSection(
    pallet: WmsPackingReceiverNode,
    pickListLines: List<WmsPackingReceiverLine>,
    receivedQty: Map<String, Int>,
    isSessionComplete: Boolean,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    onAdjust: (WmsPackingReceiverLine, Int) -> Unit,
    onSetQty: (WmsPackingReceiverLine, Int) -> Unit,
    onFill: (WmsPackingReceiverLine) -> Unit,
) {
    val isExpanded = pallet.id in expandedIds
    val cartons = pallet.childBoxes.filter { it.isCartonBox }
    val directProducts = pallet.packItems

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WmsColors.Navy.copy(0.04f))
            .border(1.dp, WmsColors.Navy.copy(0.18f), RoundedCornerShape(12.dp)),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .background(WmsColors.Navy),
        )
        Column(Modifier.weight(1f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(pallet.id) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReceivingExpandIcon(isExpanded, WmsColors.Navy, size = 16.dp)
            Box(Modifier.size(34.dp).background(WmsColors.Navy.copy(0.12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocalShipping, null, tint = WmsColors.Navy, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("PALLET", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = WmsColors.TextSecondary)
                Text(pallet.displayTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                pallet.sscc?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = WmsColors.TextSecondary)
                }
            }
            if (cartons.isNotEmpty()) {
                Text("${cartons.size} carton${if (cartons.size == 1) "" else "s"}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.Warning)
            }
        }
        if (isExpanded) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cartons.isNotEmpty()) {
                    ReceivingSectionLabel("Cartons on Pallet", cartons.size, WmsColors.Warning)
                    cartons.forEach { carton ->
                        ReceivingCartonSection(
                            carton = carton,
                            pickListLines = receivingLinesForCarton(carton, pickListLines, cartons),
                            receivedQty = receivedQty,
                            isSessionComplete = isSessionComplete,
                            expandedIds = expandedIds,
                            onToggle = onToggle,
                            onAdjust = onAdjust,
                            onSetQty = onSetQty,
                            onFill = onFill,
                            nested = true,
                        )
                    }
                }
                val directLines = receivingDirectProductLines(directProducts, pickListLines)
                if (directLines.isNotEmpty()) {
                    ReceivingSectionLabel("On Pallet", directLines.size, WmsColors.ActiveBlue)
                    directLines.forEach { line ->
                        ReceivingLineItemCard(line, ReceivingLineStyle.OnPallet, receivedQty, isSessionComplete, onAdjust, onSetQty, onFill)
                    }
                }
                val palletOnly = receivingPalletLinesNotInCartons(pickListLines, cartons, directLines)
                if (palletOnly.isNotEmpty() && cartons.isNotEmpty()) {
                    ReceivingSectionLabel("Pallet Lines", palletOnly.size, WmsColors.ActiveBlue)
                    palletOnly.forEach { line ->
                        ReceivingLineItemCard(line, ReceivingLineStyle.InCarton, receivedQty, isSessionComplete, onAdjust, onSetQty, onFill)
                    }
                } else if (cartons.isEmpty() && pickListLines.isNotEmpty()) {
                    ReceivingSectionLabel("Line Items", pickListLines.size, WmsColors.ActiveBlue)
                    pickListLines.forEach { line ->
                        ReceivingLineItemCard(line, ReceivingLineStyle.OnPallet, receivedQty, isSessionComplete, onAdjust, onSetQty, onFill)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ReceivingCartonSection(
    carton: WmsPackingReceiverNode,
    pickListLines: List<WmsPackingReceiverLine>,
    receivedQty: Map<String, Int>,
    isSessionComplete: Boolean,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    onAdjust: (WmsPackingReceiverLine, Int) -> Unit,
    onSetQty: (WmsPackingReceiverLine, Int) -> Unit,
    onFill: (WmsPackingReceiverLine) -> Unit,
    nested: Boolean = false,
) {
    val isExpanded = carton.id in expandedIds
    val bg = if (nested) CartonBg.copy(0.55f) else CartonBg.copy(0.35f)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, WmsColors.Warning.copy(0.2f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(carton.id) }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReceivingExpandIcon(isExpanded, WmsColors.Warning, size = 15.dp)
            Box(Modifier.size(30.dp).background(WmsColors.Warning.copy(0.12f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Inventory2, null, tint = WmsColors.Warning, modifier = Modifier.size(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("CARTON", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = WmsColors.TextSecondary)
                Text(carton.displayTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                carton.sscc?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = WmsColors.TextSecondary, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                (carton.quantity ?: carton.packedQty)?.let {
                    Text("$it u", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WmsColors.Warning)
                }
                Text("${pickListLines.size} lines", fontSize = 10.sp, color = WmsColors.TextSecondary)
            }
        }
        if (isExpanded) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pickListLines.isEmpty()) {
                    Text("No line items linked to this carton", fontSize = 12.sp, color = WmsColors.TextSecondary)
                } else {
                    pickListLines.forEach { line ->
                        ReceivingLineItemCard(line, ReceivingLineStyle.InCarton, receivedQty, isSessionComplete, onAdjust, onSetQty, onFill)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivingStandaloneLinesCard(
    title: String,
    lines: List<WmsPackingReceiverLine>,
    receivedQty: Map<String, Int>,
    isSessionComplete: Boolean,
    style: ReceivingLineStyle,
    onAdjust: (WmsPackingReceiverLine, Int) -> Unit,
    onSetQty: (WmsPackingReceiverLine, Int) -> Unit,
    onFill: (WmsPackingReceiverLine) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WmsColors.ActiveBlue.copy(0.04f))
            .border(1.dp, WmsColors.ActiveBlue.copy(0.14f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReceivingSectionLabel(title, lines.size, WmsColors.ActiveBlue)
        lines.forEach { line ->
            ReceivingLineItemCard(line, style, receivedQty, isSessionComplete, onAdjust, onSetQty, onFill)
        }
    }
}

@Composable
private fun ReceivingLineItemCard(
    line: WmsPackingReceiverLine,
    style: ReceivingLineStyle,
    receivedQty: Map<String, Int>,
    isSessionComplete: Boolean,
    onAdjust: (WmsPackingReceiverLine, Int) -> Unit,
    onSetQty: (WmsPackingReceiverLine, Int) -> Unit,
    onFill: (WmsPackingReceiverLine) -> Unit,
) {
    val packed = line.resolvedPackedQty
    val received = receivedQty[line.id] ?: line.resolvedReceivedQty
    val isOver = received > packed
    val status = receivingLineStatus(line, received)
    val isComplete = packed > 0 && received >= packed
    val accent = when (style) {
        ReceivingLineStyle.Standalone -> WmsColors.ActiveBlue
        ReceivingLineStyle.OnPallet -> WmsColors.Navy
        ReceivingLineStyle.InCarton -> WmsColors.Warning
    }
    val background = when (style) {
        ReceivingLineStyle.Standalone -> Color.White
        ReceivingLineStyle.OnPallet -> PageFill
        ReceivingLineStyle.InCarton -> CartonBg.copy(0.55f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(10.dp))
            .border(1.dp, WmsColors.Border.copy(0.6f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).background(accent.copy(0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Inventory2, null, tint = accent, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(line.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = WmsColors.TextPrimary, maxLines = 2)
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SKU ${line.displaySku}", fontSize = 11.sp, color = WmsColors.TextSecondary)
                    ReceivingLineStatusBadge(status)
                    if (isOver && !isSessionComplete) {
                        Text("Cannot exceed $packed", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Packed $packed", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
                    if (isSessionComplete) {
                        Text(
                            "$received",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isComplete) WmsColors.Success else WmsColors.TextPrimary,
                        )
                    } else {
                        ReceivingQtyStepper(
                            packed = packed,
                            received = received,
                            isOver = isOver,
                            onAdjust = { onAdjust(line, it) },
                            onSet = { onSetQty(line, it) },
                        )
                        if (received < packed) {
                            Text(
                                "Accept $packed",
                                color = WmsColors.Navy,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onFill(line) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivingQtyStepper(
    packed: Int,
    received: Int,
    isOver: Boolean,
    onAdjust: (Int) -> Unit,
    onSet: (Int) -> Unit,
) {
    var draft by remember(received) { mutableStateOf(received.toString()) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 2.dp, vertical = 3.dp),
    ) {
        Icon(
            Icons.Default.RemoveCircle,
            contentDescription = "Decrease",
            tint = if (received > 0) WmsColors.Navy else WmsColors.Border,
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = received > 0) { onAdjust(-1) },
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c.isDigit() } },
            modifier = Modifier
                .width(44.dp)
                .onFocusChanged { focus ->
                    if (!focus.isFocused) {
                        val clamped = (draft.toIntOrNull() ?: 0).coerceIn(0, packed)
                        draft = clamped.toString()
                        if (clamped != received) onSet(clamped)
                    }
                },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (isOver) Color(0xFFDC2626) else WmsColors.TextPrimary,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Icon(
            Icons.Default.AddCircle,
            contentDescription = "Increase",
            tint = if (received < packed) WmsColors.Navy else WmsColors.Border,
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = received < packed) { onAdjust(1) },
        )
    }
}

@Composable
private fun ReceivingLineStatusBadge(status: String) {
    val color = when (status.uppercase()) {
        "RECEIVED" -> WmsColors.Success
        "PARTIAL" -> WmsColors.Warning
        else -> WmsColors.TextSecondary
    }
    Text(
        status.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = Modifier
            .background(color.copy(0.12f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun ReceivingSectionLabel(title: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(3.dp).height(14.dp).background(color))
        Text(
            "$title ($count)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = WmsColors.TextSecondary,
        )
    }
}

@Composable
private fun ReceivingExpandIcon(expanded: Boolean, color: Color, size: androidx.compose.ui.unit.Dp = 18.dp) {
    Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun ReceivingMiniProgress(received: Int, packed: Int) {
    val progress = if (packed > 0) min(1f, received.toFloat() / packed) else 0f
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = WmsColors.Navy,
            trackColor = WmsColors.Border,
            strokeWidth = 3.dp,
        )
        Text("${(progress * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextSecondary)
    }
}

// ── Line grouping helpers (iOS parity) ────────────────────────────────────────

private fun receivingLinesForPickList(lines: List<WmsPackingReceiverLine>, pickList: WmsPackingReceiverNode): List<WmsPackingReceiverLine> {
    val matched = lines.filter { it.matchesPickList(pickList) }
    return matched.ifEmpty { lines }
}

private fun receivingStandaloneLines(
    pickListLines: List<WmsPackingReceiverLine>,
    boxes: List<WmsPackingReceiverNode>,
    hasPallet: Boolean,
): List<WmsPackingReceiverLine> {
    if (boxes.isEmpty()) return pickListLines
    if (hasPallet) return emptyList()
    return pickListLines
}

private fun receivingLinesForCarton(
    carton: WmsPackingReceiverNode,
    allLines: List<WmsPackingReceiverLine>,
    siblingCartons: List<WmsPackingReceiverNode>,
): List<WmsPackingReceiverLine> {
    val pickLineIds = carton.packItems.mapNotNull { it.pickLineId?.trim()?.takeIf { id -> id.isNotEmpty() } }.toSet()
    if (pickLineIds.isNotEmpty()) {
        val matched = allLines.filter { pickLineIds.contains(it.resolvedPickLineId) }
        if (matched.isNotEmpty()) return matched
    }
    val productMatched = allLines.filter { it.matchesTreeNode(carton) }
    if (productMatched.isNotEmpty()) return productMatched
    if (siblingCartons.size == 1) return allLines
    val index = siblingCartons.indexOfFirst { it.id == carton.id }.coerceAtLeast(0)
    val chunk = maxOf(1, ceil(allLines.size.toDouble() / siblingCartons.size).toInt())
    val start = index * chunk
    val end = min(start + chunk, allLines.size)
    if (start >= allLines.size) return emptyList()
    return allLines.subList(start, end)
}

private fun receivingDirectProductLines(
    directProducts: List<WmsPackingReceiverNode>,
    pickListLines: List<WmsPackingReceiverLine>,
): List<WmsPackingReceiverLine> {
    if (directProducts.isEmpty()) return emptyList()
    val matched = mutableListOf<WmsPackingReceiverLine>()
    for (product in directProducts) {
        val pickLineId = product.pickLineId?.trim().orEmpty()
        val line = if (pickLineId.isNotEmpty()) {
            pickListLines.firstOrNull { it.resolvedPickLineId == pickLineId }
        } else {
            pickListLines.firstOrNull { it.matchesTreeNode(product) }
        }
        if (line != null && matched.none { it.id == line.id }) matched.add(line)
    }
    return matched
}

private fun receivingPalletLinesNotInCartons(
    pickListLines: List<WmsPackingReceiverLine>,
    cartons: List<WmsPackingReceiverNode>,
    directLines: List<WmsPackingReceiverLine>,
): List<WmsPackingReceiverLine> {
    val usedIds = directLines.map { it.id }.toSet()
    return pickListLines.filter { pickLine ->
        if (pickLine.id in usedIds) return@filter false
        if (cartons.size == 1) return@filter false
        !cartons.any { carton ->
            receivingLinesForCarton(carton, listOf(pickLine), cartons).any { it.id == pickLine.id }
        }
    }
}

private fun receivingReceivedTotal(lines: List<WmsPackingReceiverLine>, receivedQty: Map<String, Int>): Int =
    lines.sumOf { receivedQty[it.id] ?: it.resolvedReceivedQty }

private fun receivingPackedTotal(lines: List<WmsPackingReceiverLine>): Int =
    lines.sumOf { it.resolvedPackedQty }

private fun receivingLineStatus(line: WmsPackingReceiverLine, received: Int): String {
    val packed = line.resolvedPackedQty
    if (packed > 0 && received >= packed) return "RECEIVED"
    if (received > 0) return "PARTIAL"
    return line.normalizedReceivingStatus
}
