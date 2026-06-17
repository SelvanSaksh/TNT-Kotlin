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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import core.network.repository.WmsRepository
import core.network.wms.WmsAddPackingBoxItemRequest
import core.network.wms.WmsCreatedPackingBox
import core.network.wms.WmsPackingPickListItem
import core.network.wms.WmsPackingPickListLine
import core.network.wms.WmsUpdatePackingBoxStatusRequest
import core.network.wms.WmsUpdatePackingPickListStatusRequest
import core.network.wms.packingBoxTypeConfig
import core.storage.SessionManager
import core.storage.getLocalStorage
import features.app.downloadImage
import features.app.shareImage
import kotlinx.coroutines.launch

@Composable
fun PackingBoxSessionScreen(
    list: WmsPackingPickListItem,
    box: WmsCreatedPackingBox,
    packTypeLabel: String,
    onBack: () -> Unit,
    onAddQty: (WmsPackingPickListLine) -> Unit,
    onCompletePackage: () -> Unit,
    onCompleteAndNewPackage: (WmsPackingPickListItem, String) -> Unit,
    onRequestNewPackage: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val packerId = remember { WmsSession.userId(session) }

    var currentBox by remember(box) { mutableStateOf(box) }
    var completing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showIncompleteAlert by remember { mutableStateOf(false) }

    val isTertiaryPack = packingBoxTypeConfig(packTypeLabel).second == 3
    val sessionTitle = if (isTertiaryPack) "Pack Pallet" else "Pack Box"
    val packTypeDisplay = currentBox.packType?.takeIf { it.isNotBlank() }
        ?: packingBoxTypeConfig(packTypeLabel).first

    fun totalPackedQty(line: WmsPackingPickListLine): Int {
        return line.packedCount + currentBox.packedQtyForLine(line.apiPickLineId)
    }

    val isListFullyPacked = list.packLines.isNotEmpty() && list.packLines.all { line ->
        val packedTotal = totalPackedQty(line)
        line.isLineFullyPacked || packedTotal >= line.requiredCount
    }

    LaunchedEffect(currentBox.resolvedPackId) {
        val packId = currentBox.resolvedPackId
        if (packId.isNotBlank()) {
            runCatching { currentBox = repo.fetchPackingBox(packId, companyId) }
        }
    }

    suspend fun markListPackedIfNeeded() {
        if (!isListFullyPacked) return
        val pickListId = list.pickListId?.trim().orEmpty().ifBlank { list.id }
        runCatching {
            repo.updatePackingPickListStatus(pickListId, companyId, "PACKED", packerId)
        }
    }

    fun completePackage(andStartNew: Boolean) {
        scope.launch {
            completing = true
            error = null
            runCatching {
                if (!currentBox.hasPackageContents) {
                    error = "Add at least one item before completing the package."
                    return@runCatching
                }
                repo.updatePackingBoxStatus(
                    currentBox.resolvedPackId,
                    WmsUpdatePackingBoxStatusRequest(companyId, "COMPLETED", packerId),
                )
                markListPackedIfNeeded()
                if (andStartNew) {
                    onCompleteAndNewPackage(list, packTypeLabel)
                } else {
                    onCompletePackage()
                }
            }.onFailure { error = it.message }
            completing = false
        }
    }

    if (showIncompleteAlert) {
        AlertDialog(
            onDismissRequest = { showIncompleteAlert = false },
            title = { Text("Complete current package") },
            text = {
                Text("\"${currentBox.resolvedPackLabel}\" is still in progress. Complete it before creating a new package.")
            },
            confirmButton = {
                TextButton(onClick = { showIncompleteAlert = false }) { Text("OK") }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        WmsLightHeader(title = sessionTitle, showBack = true, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PackingBoxSummaryCard(
                list = list,
                box = currentBox,
                packTypeDisplay = packTypeDisplay,
                statusLabel = list.displayPackingStatusLabel.replace('_', ' '),
            )

            error?.let { WmsErrorBanner(it) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Line Items", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
                Text(
                    text = if (isTertiaryPack) "+ New Pallet" else "+ New Package",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Navy,
                    modifier = Modifier.clickable(enabled = !completing) {
                        if (currentBox.isPackageInProgress && currentBox.hasPackageContents) {
                            showIncompleteAlert = true
                        } else {
                            onRequestNewPackage()
                        }
                    },
                )
            }

            if (list.packLines.isEmpty()) {
                Text(
                    "No line items available.",
                    fontSize = 13.sp,
                    color = WmsColors.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                )
            } else {
                list.packLines.forEach { line ->
                    val packedTotal = totalPackedQty(line)
                    val target = line.requiredCount
                    val isDone = packedTotal >= target && target > 0
                    val statusLabel = line.linePackingStatusLabel(packedTotal)

                    PackingLineItemCard(
                        line = line,
                        toteNumber = list.toteNumber,
                        packedTotal = packedTotal,
                        target = target,
                        statusLabel = statusLabel,
                        isDone = isDone,
                        isTertiaryPack = isTertiaryPack,
                        enabled = !completing,
                        onAddQty = { onAddQty(line) },
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
            HorizontalDivider(color = WmsColors.Border)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { completePackage(andStartNew = false) },
                    enabled = !completing && currentBox.hasPackageContents,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentBox.hasPackageContents) WmsColors.Navy else Color(0xFF9CA3AF),
                    ),
                ) {
                    if (completing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (completing) "Completing…" else "Complete Package",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }

                if (isListFullyPacked) {
                    OutlinedButton(
                        onClick = { completePackage(andStartNew = true) },
                        enabled = !completing && currentBox.hasPackageContents,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Inventory2, null, tint = WmsColors.Navy, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isTertiaryPack) "Complete & New Pallet" else "Complete & New Package",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = WmsColors.Navy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackingBoxSummaryCard(
    list: WmsPackingPickListItem,
    box: WmsCreatedPackingBox,
    packTypeDisplay: String,
    statusLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(list.displayTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
                list.orderNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, color = WmsColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
            Text(
                text = statusLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
                modifier = Modifier
                    .background(WmsColors.Navy.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (box.resolvedBarcodeData.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(box.resolvedBarcodeType, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextSecondary)
                Text(
                    box.resolvedBarcodeData,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        box.resolvedBarcodeImageUrl?.let { imageUrl ->
            PackingBarcodeImageSection(imageUrl = imageUrl, label = box.resolvedPackLabel)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PackingSummaryChip("Items", "${box.resolvedItemCount}")
            PackingSummaryChip("Units", "${box.resolvedTotalPackedQty}")
            PackingSummaryChip("Type", packTypeDisplay)
        }
    }
}

@Composable
private fun PackingBarcodeImageSection(imageUrl: String, label: String) {
    var saveMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    downloadImage(imageUrl)
                    saveMessage = "Saved to Downloads"
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Download, null, tint = WmsColors.Navy, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download", fontWeight = FontWeight.Bold, color = WmsColors.Navy)
            }
            Button(
                onClick = { shareImage(imageUrl) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
            ) {
                Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", fontWeight = FontWeight.Bold)
            }
        }

        saveMessage?.let {
            Text(it, fontSize = 11.sp, color = WmsColors.Success, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PackingLineItemCard(
    line: WmsPackingPickListLine,
    toteNumber: String?,
    packedTotal: Int,
    target: Int,
    statusLabel: String,
    isDone: Boolean,
    isTertiaryPack: Boolean,
    enabled: Boolean,
    onAddQty: () -> Unit,
) {
    val statusColor = when (statusLabel.uppercase()) {
        "PACKED" -> WmsColors.Success
        "PARTIAL" -> WmsColors.TextSecondary
        else -> packingStatusColor(statusLabel)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isDone) WmsColors.SuccessBorder else WmsColors.Border,
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(line.productName.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
            Text("SKU ${line.productSku.orEmpty()}", fontSize = 11.sp, color = WmsColors.TextSecondary, fontWeight = FontWeight.Medium)
            toteNumber?.takeIf { it.isNotBlank() }?.let {
                Text("Tote $it", fontSize = 11.sp, color = WmsColors.Navy, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "$packedTotal/$target packed",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDone) WmsColors.Success else WmsColors.Navy,
            )
            Text(
                statusLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
        }

        Button(
            onClick = onAddQty,
            enabled = !isDone && enabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDone) Color(0xFF9CA3AF) else WmsColors.Navy,
            ),
        ) {
            Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (isTertiaryPack) "Add to Pallet" else "Add Qty",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RowScope.PackingSummaryChip(title: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
    }
}

private fun packingStatusColor(label: String): Color = when (label.uppercase().replace(' ', '_')) {
    "PACKED", "COMPLETED" -> WmsColors.Success
    "IN_PROGRESS", "IN PROGRESS" -> WmsColors.Navy
    "PENDING" -> WmsColors.Warning
    else -> WmsColors.TextSecondary
}

@Composable
fun PackingLineAddQtyScreen(
    list: WmsPackingPickListItem,
    line: WmsPackingPickListLine,
    box: WmsCreatedPackingBox,
    packTypeLabel: String,
    onBack: () -> Unit,
    onBoxUpdated: (WmsCreatedPackingBox) -> Unit,
    onRequestNewPackage: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val packerId = remember { WmsSession.userId(session) }

    var qtyInput by remember { mutableStateOf("1") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showIncompleteAlert by remember { mutableStateOf(false) }

    val isTertiaryPack = packingBoxTypeConfig(packTypeLabel).second == 3
    val packedQtyInBox = line.packedCount + box.packedQtyForLine(line.apiPickLineId)
    val remainingQty = maxOf(line.requiredCount - packedQtyInBox, 0)
    val resolvedQty = run {
        val parsed = qtyInput.filter { it.isDigit() }.toIntOrNull() ?: 0
        if (remainingQty <= 0) 0 else minOf(remainingQty, maxOf(1, parsed))
    }

    if (showIncompleteAlert) {
        AlertDialog(
            onDismissRequest = { showIncompleteAlert = false },
            title = { Text("Complete current package") },
            text = {
                Text("\"${box.resolvedPackLabel}\" is still in progress. Complete it before creating a new package.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showIncompleteAlert = false
                    onBack()
                }) { Text("Complete Package") }
            },
            dismissButton = {
                TextButton(onClick = { showIncompleteAlert = false }) { Text("Cancel") }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = WmsColors.Navy)
            }
            Column {
                list.toteNumber?.takeIf { it.isNotBlank() }?.let {
                    Text("Tote $it", fontSize = 12.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                }
                Text(
                    line.productName.orEmpty(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    maxLines = 2,
                )
            }
        }
        HorizontalDivider(color = WmsColors.Border)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "$packedQtyInBox/${line.requiredCount} packed",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("PRODUCT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextSecondary)
                Text(line.productName.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
                Text("SKU ${line.productSku.orEmpty()}", fontSize = 13.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Enter pack quantity", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.RemoveCircle,
                        contentDescription = null,
                        tint = if (resolvedQty > 1 && remainingQty > 0) Color(0xFF9CA3AF) else Color(0xFFD1D5DB),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = resolvedQty > 1 && remainingQty > 0) {
                                qtyInput = maxOf(1, resolvedQty - 1).toString()
                            },
                    )
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { raw -> qtyInput = raw.filter { it.isDigit() } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = WmsColors.Navy,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = resolvedQty < remainingQty && remainingQty > 0) {
                                qtyInput = minOf(remainingQty, resolvedQty + 1).toString()
                            },
                    )
                }
                Text("Remaining: $remainingQty", fontSize = 13.sp, color = WmsColors.TextSecondary, fontWeight = FontWeight.Medium)
            }

            error?.let { WmsErrorBanner(it) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HorizontalDivider(color = WmsColors.Border, modifier = Modifier.padding(bottom = 4.dp))
            Button(
                onClick = {
                    val productId = line.productId
                    if (productId == null) {
                        error = "Product id is missing on this line."
                        return@Button
                    }
                    scope.launch {
                        saving = true
                        error = null
                        runCatching {
                            val updated = repo.addItemToPackingBox(
                                box.resolvedPackId,
                                WmsAddPackingBoxItemRequest(
                                    companyId = companyId,
                                    productId = productId,
                                    quantity = resolvedQty,
                                    packedBy = packerId,
                                    pickLineId = line.apiPickLineId,
                                ),
                            )
                            onBoxUpdated(updated)
                        }.onFailure { error = it.message }
                        saving = false
                    }
                },
                enabled = remainingQty > 0 && !saving && resolvedQty > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (remainingQty > 0) WmsColors.Navy else Color(0xFF9CA3AF),
                ),
            ) {
                if (saving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (saving) "Saving…" else if (isTertiaryPack) "Add to Pallet" else "Add to Box",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }

            OutlinedButton(
                onClick = {
                    if (box.isPackageInProgress && box.hasPackageContents) {
                        showIncompleteAlert = true
                    } else {
                        onRequestNewPackage()
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Inventory2, null, tint = WmsColors.Navy, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isTertiaryPack) "New Pallet" else "New Package",
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Navy,
                )
            }
        }
    }
}
