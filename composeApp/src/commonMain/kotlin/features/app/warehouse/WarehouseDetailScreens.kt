package features.app.warehouse

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PickingOrderDetailScreen(
    order: PickingAppOrder,
    onBack: () -> Unit,
    onOrderUpdated: (PickingAppOrder) -> Unit = {},
) {
    var items by remember(order) { mutableStateOf(order.items) }
    var scanStartIndex by remember { mutableStateOf<Int?>(null) }

    scanStartIndex?.let { startIdx ->
        val lines = items.map { item ->
            BatchScanLineItem(
                id = item.productId,
                name = item.name,
                sku = item.sku,
                gtin = item.gtin,
                batch = item.batch,
                maxQuantity = item.totalQty,
            )
        }
        WarehouseBatchScanScreen(
            mode = WarehouseScanMode.Picking,
            lines = lines,
            startIndex = startIdx,
            initialQuantities = items.associate { it.productId to it.pickedQty },
            onConfirm = { productId, qty ->
                items = items.map { item ->
                    if (item.productId == productId) item.copy(pickedQty = qty.coerceAtMost(item.totalQty))
                    else item
                }
                onOrderUpdated(order.copy(items = items))
            },
            onBack = { scanStartIndex = null },
        )
        return
    }

    val pickedTotal = items.sumOf { it.pickedQty }
    val totalQty = items.sumOf { it.totalQty }
    val nextPendingIndex = items.indexOfFirst { it.pickedQty < it.totalQty }.takeIf { it >= 0 }

    Column(Modifier.fillMaxSize().background(WarehouseColors.Background)) {
        DetailHeader(
            title = order.orderNo,
            subtitle = "PICKING • Zone ${order.zone}",
            onBack = onBack,
            statusBadge = "IN PROGRESS",
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummaryStrip("$pickedTotal/$totalQty ITEMS", pickedTotal, totalQty)
                Text(
                    "PICK ITEMS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = WarehouseColors.Muted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val progress = if (item.totalQty > 0) item.pickedQty.toFloat() / item.totalQty else 0f
                val isActive = index == nextPendingIndex
                DetailCard(
                    onClick = { scanStartIndex = index },
                    highlight = isActive,
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.name, fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.QrCodeScanner, null, tint = WarehouseColors.Accent, modifier = Modifier.size(22.dp))
                    }
                    Text("SKU ${item.sku} • BIN ${item.bin}", fontSize = 11.sp, color = WarehouseColors.Muted, fontFamily = FontFamily.Monospace)
                    Text("Batch ${item.batch}", fontSize = 11.sp, color = WarehouseColors.Muted)
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = WarehouseColors.Success,
                        trackColor = Color.Black.copy(alpha = 0.22f),
                    )
                    Text("${item.pickedQty}/${item.totalQty} units", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
                    if (isActive) {
                        Text("Tap to scan", fontSize = 11.sp, color = WarehouseColors.Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        ScanNextButton(
            enabled = nextPendingIndex != null,
            label = "SCAN NEXT ITEM",
            onClick = { nextPendingIndex?.let { scanStartIndex = it } },
        )
    }
}

@Composable
fun PackingDetailScreen(
    orderId: String,
    pickingOrderId: String,
    items: List<PackingItem>,
    onBack: () -> Unit,
    onItemsUpdated: (List<PackingItem>) -> Unit = {},
) {
    var lineItems by remember(items) { mutableStateOf(items) }
    var scanStartIndex by remember { mutableStateOf<Int?>(null) }
    var showMockAlert by remember { mutableStateOf(false) }

    scanStartIndex?.let { startIdx ->
        val lines = lineItems.map { item ->
            BatchScanLineItem(
                id = item.id,
                name = item.name,
                sku = item.sku ?: "",
                gtin = item.gtin ?: item.code,
                batch = item.batch ?: "",
                maxQuantity = item.total,
            )
        }
        WarehouseBatchScanScreen(
            mode = WarehouseScanMode.Packing,
            lines = lines,
            startIndex = startIdx,
            initialQuantities = lineItems.associate { it.id to it.packed },
            onConfirm = { id, qty ->
                lineItems = lineItems.map { item ->
                    if (item.id == id) {
                        val packed = qty.coerceAtMost(item.total)
                        item.copy(
                            packed = packed,
                            remaining = (item.total - packed).coerceAtLeast(0),
                            status = if (packed >= item.total) "packed" else "picked",
                        )
                    } else item
                }
                onItemsUpdated(lineItems)
            },
            onBack = { scanStartIndex = null },
        )
        return
    }

    val packId = remember(pickingOrderId) {
        val digits = pickingOrderId.filter { it.isDigit() }
        val n = digits.toIntOrNull() ?: kotlin.math.abs(pickingOrderId.hashCode() % 100_000)
        "PACK-%05d".format(n % 100_000)
    }
    val nextPendingIndex = lineItems.indexOfFirst { it.packed < it.total }.takeIf { it >= 0 }

    Column(Modifier.fillMaxSize().background(WarehouseColors.Background)) {
        DetailHeader(title = packId, subtitle = "ORDER #$orderId", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(lineItems, key = { _, item -> item.id }) { index, item ->
                DetailCard(onClick = { scanStartIndex = index }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.name, fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.QrCodeScanner, null, tint = WarehouseColors.Accent, modifier = Modifier.size(22.dp))
                    }
                    Text("SKU ${item.sku ?: "—"} • ${item.batch ?: "—"}", fontSize = 11.sp, color = WarehouseColors.Muted, fontFamily = FontFamily.Monospace)
                    Text("GTIN ${item.gtin ?: item.code}", fontSize = 11.sp, color = WarehouseColors.Muted)
                    LinearProgressIndicator(
                        progress = { if (item.total > 0) item.packed.toFloat() / item.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = WarehouseColors.Accent,
                        trackColor = Color.Black.copy(alpha = 0.22f),
                    )
                    Text("${item.packed}/${item.total} packed • ${item.remaining} remaining", fontSize = 12.sp, color = WarehouseColors.Muted, fontFamily = FontFamily.Monospace)
                    item.cartonSSCC?.let { sscc ->
                        Text("SSCC $sscc", fontSize = 11.sp, color = WarehouseColors.Accent, fontFamily = FontFamily.Monospace)
                    }
                    StatusBadge(item.status.uppercase(), if (item.status == "packed") WarehouseColors.Success else WarehouseColors.Warning)
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScanNextButton(
                enabled = nextPendingIndex != null,
                label = "SCAN TO PACK",
                onClick = { nextPendingIndex?.let { scanStartIndex = it } },
            )
            TextButton(
                onClick = { showMockAlert = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarehouseColors.Primary.copy(alpha = 0.5f)),
            ) {
                Text("Complete packing", fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }

    if (showMockAlert) {
        AlertDialog(
            onDismissRequest = { showMockAlert = false },
            title = { Text("Packing") },
            text = {
                Text("Mock mode: complete was not sent to the server. Turn off WarehouseFlowConfig.useMockPacking to use the live API.")
            },
            confirmButton = { TextButton(onClick = { showMockAlert = false }) { Text("OK") } },
        )
    }
}

@Composable
fun ReceivingDetailScreen(
    order: DispatchedOrder,
    onBack: () -> Unit,
    onOrderUpdated: (DispatchedOrder) -> Unit = {},
) {
    val qtyOverrides = remember { mutableStateMapOf<Int, Int>() }
    order.items.forEach { qtyOverrides[it.id] = it.receivedQuantity }

    var scanStartIndex by remember { mutableStateOf<Int?>(null) }
    var showMockAlert by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    scanStartIndex?.let { startIdx ->
        val lines = order.items.map { line ->
            BatchScanLineItem(
                id = line.id,
                name = line.productName,
                gtin = line.gtin,
                batch = line.batch,
                maxQuantity = line.quantity,
            )
        }
        WarehouseBatchScanScreen(
            mode = WarehouseScanMode.Receiving,
            lines = lines,
            startIndex = startIdx,
            initialQuantities = qtyOverrides.toMap(),
            onConfirm = { id, qty ->
                qtyOverrides[id] = qty.coerceAtMost(order.items.first { it.id == id }.quantity)
                val updatedItems = order.items.map { line ->
                    line.copy(receivedQuantity = qtyOverrides[line.id] ?: line.receivedQuantity)
                }
                onOrderUpdated(order.copy(items = updatedItems))
            },
            onBack = { scanStartIndex = null },
        )
        return
    }

    val receivedTotal = order.items.sumOf { qtyOverrides[it.id] ?: it.receivedQuantity }
    val nextPendingIndex = order.items.indexOfFirst {
        (qtyOverrides[it.id] ?: it.receivedQuantity) < it.quantity
    }.takeIf { it >= 0 }

    Column(Modifier.fillMaxSize().background(WarehouseColors.Background)) {
        DetailHeader(
            title = order.displayPurchaseOrderRef,
            subtitle = order.displayVendor,
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DetailCard {
                    Text("SSCC ${order.ssc}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Accent)
                    Text("Location ${order.receivingLocation ?: "RCV-DOCK-A"}", fontSize = 12.sp, color = WarehouseColors.Muted)
                    Text(
                        "$receivedTotal/${order.totalExpectedUnits} units received",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = WarehouseColors.Muted,
                    )
                }
            }
            itemsIndexed(order.items, key = { _, line -> line.id }) { index, line ->
                val received = qtyOverrides[line.id] ?: line.receivedQuantity
                DetailCard(onClick = { scanStartIndex = index }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(line.productName, fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.QrCodeScanner, null, tint = WarehouseColors.Accent, modifier = Modifier.size(22.dp))
                    }
                    Text("GTIN ${line.gtin} • ${line.batch}", fontSize = 11.sp, color = WarehouseColors.Muted, fontFamily = FontFamily.Monospace)
                    LinearProgressIndicator(
                        progress = { if (line.quantity > 0) received.toFloat() / line.quantity else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = WarehouseColors.Success,
                        trackColor = Color.Black.copy(alpha = 0.22f),
                    )
                    Text("$received/${line.quantity} received", fontSize = 12.sp, color = WarehouseColors.Muted, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScanNextButton(
                enabled = nextPendingIndex != null,
                label = "SCAN PACKAGE",
                onClick = { nextPendingIndex?.let { scanStartIndex = it } },
            )
            TextButton(
                onClick = {
                    if (WarehouseFlowConfig.useMockReceiving) showMockAlert = true else showSuccess = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarehouseColors.Success),
            ) {
                Text("Complete receipt", fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }

    if (showMockAlert) {
        AlertDialog(
            onDismissRequest = { showMockAlert = false },
            title = { Text("Receiving") },
            text = {
                Text("Mock mode: receipt was not sent to the server. Set WarehouseFlowConfig.useMockReceiving to false to use the live API.")
            },
            confirmButton = { TextButton(onClick = { showMockAlert = false }) { Text("OK") } },
        )
    }
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false; onBack() },
            title = { Text("Success") },
            text = { Text("Receiving completed successfully.") },
            confirmButton = { TextButton(onClick = { showSuccess = false; onBack() }) { Text("OK") } },
        )
    }
}

@Composable
private fun ScanNextButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) WarehouseColors.Accent
                else WarehouseColors.Accent.copy(alpha = 0.45f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
    }
}

@Composable
private fun SummaryStrip(label: String, picked: Int, total: Int) {
    val progress = if (total > 0) picked.toFloat() / total else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarehouseColors.Primary.copy(alpha = 0.38f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontWeight = FontWeight.Black, color = Color.White, fontFamily = FontFamily.Monospace)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = WarehouseColors.Success,
            trackColor = Color.Black.copy(alpha = 0.22f),
        )
    }
}

@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    statusBadge: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(WarehouseColors.Primary, WarehouseColors.Background)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = WarehouseColors.Muted)
            }
            Column(Modifier.weight(1f)) {
                Text("BACK", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(subtitle, fontSize = 12.sp, color = WarehouseColors.Muted, maxLines = 1)
            }
            statusBadge?.let { StatusBadge(it, WarehouseColors.Success) }
        }
    }
}

@Composable
private fun DetailCard(
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (highlight) WarehouseColors.Primary.copy(alpha = 0.48f)
                else WarehouseColors.Primary.copy(alpha = 0.38f),
            )
            .border(
                width = if (highlight) 1.dp else 1.dp,
                color = if (highlight) WarehouseColors.Accent.copy(alpha = 0.5f) else WarehouseColors.TextLight.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
