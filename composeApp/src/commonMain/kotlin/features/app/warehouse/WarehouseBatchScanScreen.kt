package features.app.warehouse

import features.app.warehouse.wms.WmsCircularBackButton
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import features.app.warehouse.wms.WmsBarcodeCameraPreview
import features.app.warehouse.wms.WmsScanInputMode
import features.app.warehouse.wms.WmsScanInputTabs

enum class WarehouseScanMode {
    Picking,
    Packing,
    Receiving,
}

@Composable
fun WarehouseBatchScanScreen(
    mode: WarehouseScanMode,
    lines: List<BatchScanLineItem>,
    startIndex: Int = 0,
    initialQuantities: Map<Int, Int> = emptyMap(),
    onConfirm: (itemId: Int, quantity: Int) -> Unit,
    onBack: () -> Unit,
) {
    if (lines.isEmpty()) {
        onBack()
        return
    }

    val title = when (mode) {
        WarehouseScanMode.Picking -> "Scan Barcode"
        WarehouseScanMode.Packing -> "Pack Product"
        WarehouseScanMode.Receiving -> "Receive Product"
    }

    var currentIndex by remember {
        mutableIntStateOf(startIndex.coerceIn(0, lines.lastIndex))
    }
    var manualEntry by remember { mutableStateOf(false) }
    var scannerEnabled by remember { mutableStateOf(true) }
    var inputMode by remember {
        mutableStateOf(WmsScanInputMode.Scan)
    }
    var manualBatch by remember { mutableStateOf("") }
    var batchVerified by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) }
    val pickedMap = remember { mutableStateMapOf<Int, Int>().apply { putAll(initialQuantities) } }

    val current = lines[currentIndex]
    val maxQty = current.maxQuantity.coerceAtLeast(1)

    fun resetVerification(clearManual: Boolean = true) {
        batchVerified = false
        showError = false
        errorMessage = ""
        quantity = 1
        if (clearManual) manualBatch = ""
        scannerEnabled = inputMode == WmsScanInputMode.Scan
        manualEntry = inputMode == WmsScanInputMode.Manual
    }

    fun validatePayload(payload: String) {
        val (ok, candidates) = validateWarehouseScan(
            payload = payload,
            expectedBatch = current.batch,
            expectedGtin = current.gtin,
            expectedSku = current.sku,
        )
        if (ok) {
            batchVerified = true
            showError = false
            scannerEnabled = false
            if (quantity < maxQty) quantity += 1
        } else {
            batchVerified = false
            showError = true
            errorMessage = mismatchMessage(
                candidates,
                current.batch,
                current.gtin,
                current.sku,
            )
            if (!manualEntry) scannerEnabled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarehouseColors.Background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(WarehouseColors.Primary, WarehouseColors.Background)))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            WmsCircularBackButton(onClick = onBack)
                Column(Modifier.weight(1f)) {
                    Text("BACK", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                if (!manualEntry && scannerEnabled) {
                    StatusBadge("CAMERA ON", WarehouseColors.Success)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(WarehouseColors.Accent))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButtonLite("Prev", enabled = currentIndex > 0) {
                val existing = pickedMap[current.id] ?: 0
                pickedMap[current.id] = minOf(existing + quantity, maxQty)
                currentIndex -= 1
                resetVerification()
                quantity = pickedMap[lines[currentIndex].id] ?: 1
            }
            Text("${currentIndex + 1} / ${lines.size}", fontSize = 12.sp, color = WarehouseColors.Muted)
            TextButtonLite("Next", enabled = currentIndex < lines.lastIndex) {
                val existing = pickedMap[current.id] ?: 0
                pickedMap[current.id] = minOf(existing + quantity, maxQty)
                currentIndex += 1
                resetVerification()
                quantity = pickedMap[lines[currentIndex].id] ?: 1
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScanFrameCard(
                inputMode = inputMode,
                scannerEnabled = scannerEnabled,
                onBarcodeScanned = { validatePayload(it) },
            )

            WmsScanInputTabs(
                mode = inputMode,
                scanLabel = "SCAN",
                manualLabel = "MANUAL ENTRY",
                onModeChange = { mode ->
                    inputMode = mode
                    resetVerification()
                },
            )

            if (showError) {
                ErrorBanner(errorMessage)
            }

            if (manualEntry && !batchVerified) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter Batch Number", fontWeight = FontWeight.SemiBold, color = Color.White)
                    OutlinedTextField(
                        value = manualBatch,
                        onValueChange = { manualBatch = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Batch") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = WarehouseColors.Accent,
                            unfocusedBorderColor = WarehouseColors.Muted,
                        ),
                    )
                    Button(
                        onClick = { validatePayload(manualBatch) },
                        enabled = manualBatch.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = WarehouseColors.Accent),
                    ) {
                        Text("Verify Batch", fontWeight = FontWeight.Bold)
                    }
                }
            }

            ProductInfoCard(current)

            if (batchVerified) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { quantity = maxOf(1, quantity - 1) }) {
                        Icon(Icons.Default.Remove, null, tint = WarehouseColors.Accent, modifier = Modifier.size(32.dp))
                    }
                    Text("$quantity", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 24.dp))
                    IconButton(
                        onClick = { if (quantity < maxQty) quantity += 1 },
                        enabled = quantity < maxQty,
                    ) {
                        Icon(Icons.Default.Add, null, tint = WarehouseColors.Accent, modifier = Modifier.size(32.dp))
                    }
                }
                Button(
                    onClick = {
                        val total = minOf((pickedMap[current.id] ?: 0) + quantity, maxQty)
                        onConfirm(current.id, total)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WarehouseColors.Accent),
                ) {
                    Text("Confirm", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ScanFrameCard(
    inputMode: WmsScanInputMode,
    scannerEnabled: Boolean,
    onBarcodeScanned: (String) -> Unit,
) {
    if (inputMode == WmsScanInputMode.Scan) {
        WmsBarcodeCameraPreview(
            instruction = "Position barcode within frame",
            enabled = scannerEnabled,
            onBarcodeScanned = onBarcodeScanned,
            height = 280.dp,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(WarehouseColors.Primary.copy(alpha = 0.38f))
                .border(1.dp, WarehouseColors.TextLight.copy(alpha = 0.14f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Manual batch entry enabled", color = WarehouseColors.Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProductInfoCard(item: BatchScanLineItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarehouseColors.Primary.copy(alpha = 0.26f))
            .border(1.dp, WarehouseColors.TextLight.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(item.name, fontWeight = FontWeight.Black, color = Color.White, fontSize = 17.sp)
        if (item.sku.isNotBlank()) {
            Text("SKU: ${item.sku}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
        }
        if (item.gtin.isNotBlank()) {
            Text("GTIN: ${item.gtin}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
        }
        Text("Batch: ${item.batch}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
        Text("Required Qty: ${item.maxQuantity}", fontSize = 12.sp, color = WarehouseColors.Muted)
    }
}

@Composable
private fun ScanToggleButton(
    label: String,
    selected: Boolean,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        selected && primary -> WarehouseColors.Accent
        selected -> WarehouseColors.Primary.copy(alpha = 0.95f)
        else -> WarehouseColors.Primary.copy(alpha = 0.42f)
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun TextButtonLite(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (enabled) Color.White else WarehouseColors.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE24B4A).copy(alpha = 0.14f))
            .border(1.dp, Color(0xFFE24B4A).copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text("Product mismatch", fontWeight = FontWeight.Black, color = Color.White, fontSize = 14.sp)
            Text(message, fontSize = 12.sp, color = WarehouseColors.Muted)
        }
    }
}
