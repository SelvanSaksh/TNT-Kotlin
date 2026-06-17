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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.session.WarehouseStaffRole
import features.app.warehouse.WarehouseMlKitScanner

enum class WmsScanInputMode { Scan, Manual }

@Composable
fun WmsStaffHomeHeader(
    displayName: String,
    role: WarehouseStaffRole?,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayName,
            modifier = Modifier.weight(1f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.Navy,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WmsProfileMenuButton(displayName = displayName, onLogout = onLogout)
            role?.let {
                Text(
                    text = it.displayName.uppercase(),
                    modifier = Modifier
                        .background(WmsColors.TabInactiveBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextSecondary,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
fun WmsStatsOverviewBar(
    todo: Int,
    active: Int,
    done: Int,
    units: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WmsStatCell(label = "To do", value = todo.toString(), valueColor = WmsColors.TextPrimary)
        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .width(1.dp)
                .height(36.dp)
                .background(WmsColors.Navy.copy(alpha = 0.2f)),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            WmsStatCell(label = "Active", value = active.toString(), valueColor = WmsColors.ActiveBlue)
            WmsStatCell(label = "Done", value = done.toString(), valueColor = WmsColors.Success)
            WmsStatCell(label = "Units", value = units.toString(), valueColor = WmsColors.TextPrimary)
        }
    }
}

@Composable
private fun WmsStatCell(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 12.sp, color = WmsColors.TextMuted)
    }
}

@Composable
fun WmsSectionHeading(
    title: String,
    color: Color = WmsColors.Navy,
) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

@Composable
fun WmsPriorityTaskCard(
    title: String,
    itemCount: Int,
    locationLine: String,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
                lineHeight = 26.sp,
            )
            Text(
                text = "$itemCount ITEMS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextMuted,
                letterSpacing = 0.5.sp,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = WmsColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(locationLine, fontSize = 14.sp, color = WmsColors.TextSecondary)
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("START PICKING", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun WmsPickedTaskCard(
    title: String,
    lineSummary: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WmsColors.SuccessBg)
            .border(1.dp, WmsColors.SuccessBorder, RoundedCornerShape(14.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .background(WmsColors.Success, RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = WmsColors.Navy)
            Text(
                "PICKED",
                modifier = Modifier
                    .background(WmsColors.SuccessBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Success,
            )
            Text(lineSummary, fontSize = 13.sp, color = WmsColors.Success)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(WmsColors.Success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun WmsDetailHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    onSkip: (() -> Unit)? = null,
    skipLabel: String = "Skip",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WmsBackButton(onClick = onBack)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WmsColors.Navy)
            subtitle?.let {
                Text(it, fontSize = 13.sp, color = WmsColors.TextSecondary, textAlign = TextAlign.Center)
            }
        }
        if (onSkip != null) {
            Text(
                text = skipLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(WmsColors.TabInactiveBg)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = WmsColors.Navy,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Spacer(Modifier.width(56.dp))
        }
    }
}

@Composable
fun WmsScanInputTabs(
    mode: WmsScanInputMode,
    onModeChange: (WmsScanInputMode) -> Unit,
    scanLabel: String = "Scan Tote",
    manualLabel: String = "Enter Manually",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WmsColors.TabInactiveBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WmsScanTab(
            label = scanLabel,
            icon = Icons.Default.QrCodeScanner,
            selected = mode == WmsScanInputMode.Scan,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(WmsScanInputMode.Scan) },
        )
        WmsScanTab(
            label = manualLabel,
            icon = Icons.Default.Keyboard,
            selected = mode == WmsScanInputMode.Manual,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(WmsScanInputMode.Manual) },
        )
    }
}

@Composable
private fun WmsScanTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) WmsColors.Navy else Color.Transparent
    val content = if (selected) Color.White else WmsColors.Navy
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Shared barcode camera preview used across Picking, Packing, and Receiving flows.
 */
@Composable
fun WmsBarcodeCameraPreview(
    instruction: String,
    enabled: Boolean,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        Text(
            text = instruction,
            fontSize = 14.sp,
            color = WmsColors.TextSecondary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.04f)),
        ) {
            WarehouseMlKitScanner(
                enabled = enabled,
                onBarcodeScanned = onBarcodeScanned,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun WmsManualEntryCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    confirmLabel: String,
    enabled: Boolean,
    loading: Boolean = false,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = WmsColors.TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WmsColors.Navy,
                unfocusedBorderColor = WmsColors.Border,
                focusedTextColor = WmsColors.TextPrimary,
                unfocusedTextColor = WmsColors.TextPrimary,
            ),
        )
        Button(
            onClick = onConfirm,
            enabled = enabled && !loading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) WmsColors.Navy else WmsColors.TextMuted,
                disabledContainerColor = WmsColors.Border,
            ),
        ) {
            Text(if (loading) "Starting…" else confirmLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WmsWalkToToteFooter(stepLabel: String = "② Walk to tote") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .background(WmsColors.TabInactiveBg, RoundedCornerShape(16.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stepLabel,
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = WmsColors.Navy,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WmsFlowNode(label = "SHELF", accent = WmsColors.TextMuted, borderColor = WmsColors.Border)
            Text("→", color = WmsColors.TextMuted, fontSize = 18.sp)
            WmsFlowNode(label = "TOTE", accent = WmsColors.Success, borderColor = WmsColors.SuccessBorder)
        }
    }
}

@Composable
private fun WmsFlowNode(label: String, accent: Color, borderColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("▣", fontSize = 22.sp, color = accent)
        }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = 0.5.sp)
    }
}
