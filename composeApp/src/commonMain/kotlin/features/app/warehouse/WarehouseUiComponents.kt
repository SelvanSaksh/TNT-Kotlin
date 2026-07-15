package features.app.warehouse

import features.app.warehouse.wms.WmsCircularBackButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

object WarehouseColors {
    val Background = Color(0xFF0F2A47)
    val Primary = Color(0xFF163C66)
    val Accent = Color(0xFFE8855A)
    val Success = Color(0xFF059669)
    val Warning = Color(0xFFD97706)
    val Muted = Color(0xFFE8EEF5).copy(alpha = 0.68f)
    val TextLight = Color(0xFFE8EEF5)
}

@Composable
fun WarehouseModuleHeader(
    title: String,
    activeCount: Int,
    onBack: () -> Unit,
    accentBarWidth: Int? = 56,
    showBackNavigation: Boolean = true,
) {
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    Brush.linearGradient(
                        listOf(WarehouseColors.Primary, WarehouseColors.Background),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .offset(x = 280.dp, y = (-42).dp)
                    .background(Color.White.copy(alpha = 0.06f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .offset(x = 320.dp, y = 48.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBackNavigation) {
                    WmsCircularBackButton(onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(
                        "MODULE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        color = WarehouseColors.TextLight.copy(alpha = 0.65f),
                    )
                    Text(
                        title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .border(1.dp, WarehouseColors.Success.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .background(WarehouseColors.Success.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(WarehouseColors.Success, CircleShape),
                    )
                    Text(
                        "$activeCount ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = WarehouseColors.Success,
                    )
                }
            }
        }
        if (accentBarWidth != null) {
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                Box(
                    Modifier
                        .width(accentBarWidth.dp)
                        .height(3.dp)
                        .background(WarehouseColors.Accent),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .background(WarehouseColors.Accent),
            )
        }
    }
}

@Composable
fun WarehouseSearchRow(
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    filterOptions: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, WarehouseColors.TextLight.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .padding(horizontal = 13.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = WarehouseColors.Muted, modifier = Modifier.size(18.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder, color = WarehouseColors.Muted, fontSize = 14.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, WarehouseColors.TextLight.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            ) {
                Icon(Icons.Default.FilterList, null, tint = Color.White)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                filterOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onFilterSelected(option)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PickingModeTabs(
    selected: PickingListMode,
    onSelect: (PickingListMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PickingListMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) WarehouseColors.TextLight
                        else Color.White.copy(alpha = 0.06f),
                    )
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    mode.label.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSelected) WarehouseColors.Background else WarehouseColors.Muted,
                )
            }
        }
    }
}

@Composable
fun StatusChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { status ->
            val isSelected = status == selected
            Text(
                status.uppercase(),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) WarehouseColors.TextLight
                        else Color.White.copy(alpha = 0.06f),
                    )
                    .clickable { onSelect(status) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (isSelected) WarehouseColors.Background else WarehouseColors.Muted,
            )
        }
    }
}

@Composable
fun PickingOrderCard(order: PickingAppOrder, onClick: () -> Unit) {
    val totalQty = order.items.sumOf { maxOf(it.totalQty, 0) }
    val pickedQty = order.items.sumOf { maxOf(it.pickedQty, 0) }
    val progress = if (totalQty > 0 && pickedQty > 0) {
        (pickedQty.toFloat() / totalQty).coerceAtMost(1f)
    } else if (order.items.isNotEmpty()) 0.15f else 0f

    val pickType = when {
        totalQty >= 100 || order.items.size >= 12 -> "WAVE PICK"
        totalQty >= 40 || order.items.size >= 6 -> "BATCH PICK"
        else -> "ORDER PICK"
    }
    val priority = if (totalQty >= 100 || order.items.size >= 8 || order.isPending) "HIGH" else "MEDIUM"
    val statusLabel = when (order.status.lowercase()) {
        "", "pending" -> "READY"
        else -> order.status.uppercase()
    }
    val statusColor = when (order.status.lowercase()) {
        "pending", "ready" -> WarehouseColors.Warning
        "assigned", "picking" -> WarehouseColors.Success
        else -> WarehouseColors.TextLight
    }

    ModuleCard(onClick = onClick, accentBar = true) {
        Text(order.orderNo, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(
            "$pickType • PRIORITY: $priority",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = WarehouseColors.Muted,
        )
        if (progress > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = WarehouseColors.TextLight,
                trackColor = Color.Black.copy(alpha = 0.22f),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            val summary = if (pickedQty > 0) "$pickedQty/$totalQty items" else "$totalQty items"
            Text(summary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
            Spacer(Modifier.weight(1f))
            if (progress > 0) {
                Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
            } else {
                Text("Zone ${order.zone}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarehouseColors.Muted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            StatusBadge(statusLabel, statusColor)
        }
    }
}

@Composable
fun PickingPickListCard(orderNo: String, item: PickingAppItem, onClick: () -> Unit) {
    ModuleCard(onClick = onClick, accentBar = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(orderNo, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = WarehouseColors.Accent)
            Text(
                if (item.isComplete) "PICKED" else "READY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (item.isComplete) WarehouseColors.Success else WarehouseColors.Muted,
            )
        }
        Text(item.name, fontSize = 17.sp, fontWeight = FontWeight.Black, color = if (item.isComplete) WarehouseColors.Muted else Color.White, maxLines = 1)
        Text("SKU: ${item.sku} • BIN: ${item.bin}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted, maxLines = 1)
        val progressText = if (item.pickedQty > 0) "${item.pickedQty}/${item.totalQty} UNITS" else "${item.totalQty} UNITS"
        Text("BATCH: ${item.batch} • $progressText", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted, maxLines = 1)
    }
}

@Composable
fun PackingJobCard(
    packId: String,
    stationLabel: String,
    orderRef: String,
    itemCount: Int,
    boxCount: Int,
    isReadyToDispatch: Boolean,
    isDispatching: Boolean,
    onOpen: () -> Unit,
    onDispatch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ModuleCard(onClick = onOpen, accentBar = true) {
            Text(packId, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(
                "$stationLabel • ORDER #$orderRef",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = WarehouseColors.Muted,
            )
            Row(Modifier.fillMaxWidth()) {
                Text("$itemCount items", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
                Spacer(Modifier.weight(1f))
                Text("$boxCount boxes", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val label = if (isReadyToDispatch) "READY" else "PACKING"
                val color = if (isReadyToDispatch) WarehouseColors.Warning else WarehouseColors.Success
                StatusBadge(label, color)
            }
        }
        if (isReadyToDispatch) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarehouseColors.Success.copy(alpha = 0.9f))
                    .clickable(enabled = !isDispatching, onClick = onDispatch)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isDispatching) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Text("Dispatch order", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ReceivingDockBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarehouseColors.Primary.copy(alpha = 0.38f))
            .border(1.dp, WarehouseColors.TextLight.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .background(WarehouseColors.Success, RoundedCornerShape(3.dp)),
        )
        Icon(Icons.Default.CheckCircle, null, tint = WarehouseColors.Success, modifier = Modifier.size(22.dp))
        Text("Dock A ready for receiving", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WarehouseColors.Success)
    }
}

@Composable
fun ReceivingPoCard(order: DispatchedOrder, onClick: () -> Unit) {
    val kind = order.listStatusKind
    val statusColor = if (kind == ReceivingListStatus.Pending) WarehouseColors.Warning else WarehouseColors.Success
    val statusLabel = when (kind) {
        ReceivingListStatus.Pending -> "PENDING"
        ReceivingListStatus.Complete -> "DONE"
        ReceivingListStatus.Active -> "ACTIVE"
    }

    ModuleCard(
        onClick = onClick,
        accentBar = kind == ReceivingListStatus.Active,
        alpha = if (order.isReceivingComplete) 0.30f else if (kind == ReceivingListStatus.Active) 0.38f else 0.30f,
    ) {
        Text(order.displayPurchaseOrderRef, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(order.displayVendor, fontSize = 13.sp, color = WarehouseColors.Muted)
        when (kind) {
            ReceivingListStatus.Active -> {
                LinearProgressIndicator(
                    progress = { order.receivingProgress },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = WarehouseColors.Success,
                    trackColor = Color.Black.copy(alpha = 0.22f),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${order.totalReceivedUnits}/${order.totalExpectedUnits} units",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = WarehouseColors.Muted,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${(order.receivingProgress * 100).toInt()}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
                }
            }
            ReceivingListStatus.Pending -> {
                Row(Modifier.fillMaxWidth()) {
                    Text("${order.totalExpectedUnits} units", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = WarehouseColors.Muted)
                    Spacer(Modifier.weight(1f))
                    order.dueSummary?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, fontSize = 12.sp, color = WarehouseColors.Warning)
                    }
                }
            }
            else -> Unit
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            StatusBadge(statusLabel, statusColor)
        }
    }
}

@Composable
private fun ModuleCard(
    onClick: () -> Unit,
    accentBar: Boolean,
    alpha: Float = 0.38f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        if (accentBar) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(120.dp)
                    .background(WarehouseColors.Accent),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .background(WarehouseColors.Primary.copy(alpha = alpha))
                .border(1.dp, WarehouseColors.TextLight.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun StatusBadge(label: String, color: Color) {
    Text(
        label,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
}

@Composable
fun WarehouseEmptyState(icon: String, title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, fontSize = 44.sp)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(message, fontSize = 14.sp, color = WarehouseColors.Muted)
    }
}

@Composable
fun WarehouseLoadingState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(color = Color.White)
        Text(message, fontSize = 14.sp, color = WarehouseColors.Muted)
    }
}
