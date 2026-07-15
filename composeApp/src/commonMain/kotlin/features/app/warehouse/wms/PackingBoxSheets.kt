package features.app.warehouse.wms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.wms.WmsPackingBoxSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingFinishChoiceSheet(
    visible: Boolean,
    sealedCartonCount: Int,
    showCreatePallet: Boolean,
    isCreatingPallet: Boolean,
    isCompleting: Boolean,
    onCreatePallet: () -> Unit,
    onCompletePackaging: () -> Unit,
    onLater: () -> Unit,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = { if (!isCreatingPallet && !isCompleting) onLater() },
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(WmsColors.Success.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Verified, null, tint = WmsColors.Success, modifier = Modifier.size(28.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("All cartons sealed", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
                Text(
                    "$sealedCartonCount carton${if (sealedCartonCount == 1) "" else "s"} ready",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                )
            }

            Text(
                "Choose how to finish this pick list. Nothing is completed until you confirm below.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            if (showCreatePallet) {
                Button(
                    onClick = onCreatePallet,
                    enabled = !isCreatingPallet && !isCompleting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCreatingPallet) Color(0xFF9CA3AF) else WmsColors.Success,
                    ),
                ) {
                    if (isCreatingPallet) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (isCreatingPallet) "Creating Pallet…" else "Create Pallet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }

            Button(
                onClick = onCompletePackaging,
                enabled = !isCreatingPallet && !isCompleting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleting) Color(0xFF9CA3AF) else WmsColors.Navy,
                ),
            ) {
                if (isCompleting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (isCompleting) "Completing…" else "Complete Packaging",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }

            TextButton(
                onClick = onLater,
                enabled = !isCreatingPallet && !isCompleting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Not now — stay on this screen", fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingBoxActionSheet(
    box: WmsPackingBoxSummary,
    isActiveBox: Boolean,
    isSealing: Boolean,
    onResume: () -> Unit,
    onSeal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val itemCount = box.itemCount?.takeIf { it > 0 }
        ?: box.resolvedTotalPackedQty.takeIf { it > 0 }
    val statusAccent = if (isActiveBox) WmsColors.Navy else Color(0xFFD97706)

    ModalBottomSheet(
        onDismissRequest = { if (!isSealing) onDismiss() },
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 20.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFD1D5DB)),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WmsColors.Navy.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = WmsColors.Navy,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        box.displayTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    if (box.displaySubtitle.isNotBlank()) {
                        Text(
                            box.displaySubtitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = WmsColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isActiveBox) "ACTIVE" else "OPEN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(statusAccent.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        itemCount?.let { count ->
                            Text(
                                "$count item${if (count == 1) "" else "s"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = WmsColors.TextSecondary,
                            )
                        }
                    }
                }

                Text(
                    if (isActiveBox) {
                        "Do you want to seal this box?"
                    } else {
                        "What would you like to do with this box?"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isActiveBox) {
                        PackingBoxSheetPrimaryButton(
                            label = if (isSealing) "Sealing…" else "Seal Box",
                            icon = Icons.Default.Lock,
                            isLoading = isSealing,
                            enabled = !isSealing,
                            onClick = onSeal,
                        )
                        PackingBoxSheetOutlinedButton(
                            label = "Keep Packing",
                            enabled = !isSealing,
                            onClick = onDismiss,
                        )
                    } else {
                        PackingBoxSheetPrimaryButton(
                            label = "Resume Packing",
                            icon = Icons.Default.ArrowForward,
                            enabled = !isSealing,
                            onClick = onResume,
                        )
                        PackingBoxSheetOutlinedButton(
                            label = if (isSealing) "Sealing…" else "Seal Box",
                            icon = Icons.Default.Lock,
                            isLoading = isSealing,
                            enabled = !isSealing,
                            onClick = onSeal,
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSealing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Cancel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WmsColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackingBoxSheetPrimaryButton(
    label: String,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WmsColors.Navy,
            disabledContainerColor = Color(0xFF9CA3AF),
        ),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        if (isLoading || icon != null) {
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun PackingBoxSheetOutlinedButton(
    label: String,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, WmsColors.Navy),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = WmsColors.Navy,
            disabledContentColor = Color(0xFF9CA3AF),
        ),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = WmsColors.Navy,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        if (isLoading || icon != null) {
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun CreatePalletPromptBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFECFDF5))
            .border(1.dp, Color(0xFF6EE7B7), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.LocalShipping, null, tint = WmsColors.Success, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("All secondary boxes sealed", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
            Text(
                "Create a pallet to group sealed cartons, or complete packaging to finish without a pallet.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
            )
        }
    }
}

@Composable
fun PalletSealProgressBanner(
    attachedCount: Int,
    totalCount: Int,
    allAttached: Boolean,
    remainingToLink: Int,
    onClick: () -> Unit,
) {
    val bg = if (allAttached) Color(0xFFECFDF5) else Color(0xFFEFF6FF)
    val border = if (allAttached) WmsColors.Success.copy(alpha = 0.45f) else WmsColors.Navy.copy(alpha = 0.25f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            CircularProgressIndicator(
                progress = { attachedCount.toFloat() / totalCount.coerceAtLeast(1).toFloat() },
                modifier = Modifier.size(48.dp),
                color = if (allAttached) WmsColors.Success else WmsColors.Navy,
                strokeWidth = 4.dp,
                trackColor = WmsColors.Border,
            )
            Text("$attachedCount/$totalCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (allAttached) "All sealed cartons linked" else "Linking sealed cartons",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextPrimary,
            )
            Text(
                if (allAttached) {
                    "Tap to seal pallet and generate barcode."
                } else {
                    "$remainingToLink sealed carton${if (remainingToLink == 1) "" else "s"} remaining to add."
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
            )
        }
        Icon(
            if (allAttached) Icons.Default.Verified else Icons.Default.LocalShipping,
            contentDescription = null,
            tint = if (allAttached) WmsColors.Success else WmsColors.Navy,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingPalletSealSheet(
    visible: Boolean,
    palletLabel: String,
    palletSscc: String,
    cartons: List<WmsPackingBoxSummary>,
    attachedCount: Int,
    totalCount: Int,
    allAttached: Boolean,
    isSealed: Boolean,
    isSealing: Boolean,
    isCompleting: Boolean,
    barcodeImageUrl: String?,
    barcodeData: String,
    onSeal: () -> Unit,
    onComplete: () -> Unit,
    onLater: () -> Unit,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = { if (!isSealing && !isCompleting) onLater() },
        containerColor = Color(0xFFF8FAFC),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isSealed) WmsColors.Success.copy(alpha = 0.14f) else WmsColors.Navy.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isSealed) Icons.Default.Verified else Icons.Default.LocalShipping,
                    null,
                    tint = if (isSealed) WmsColors.Success else WmsColors.Navy,
                    modifier = Modifier.size(30.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        isSealed -> "Pallet Sealed"
                        allAttached -> "Ready to Seal"
                        else -> "Add Cartons to Pallet"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                )
                Text(palletLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.Navy)
                if (palletSscc.isNotBlank()) {
                    Text(palletSscc, fontSize = 12.sp, color = WmsColors.TextSecondary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }

            PalletSealProgressBanner(
                attachedCount = attachedCount,
                totalCount = totalCount,
                allAttached = allAttached,
                remainingToLink = (totalCount - attachedCount).coerceAtLeast(0),
                onClick = {},
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Cartons on Pallet", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
                if (cartons.isEmpty()) {
                    Text("No cartons linked yet.", fontSize = 12.sp, color = WmsColors.TextSecondary)
                } else {
                    cartons.forEach { carton ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(carton.displayTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (isSealed && !barcodeImageUrl.isNullOrBlank()) {
                PackingBarcodeImageSection(imageUrl = barcodeImageUrl, label = palletLabel)
            } else if (!isSealed) {
                Text(
                    if (allAttached) {
                        "All cartons are linked. Seal the pallet to generate a dispatch barcode."
                    } else {
                        "Link all sealed cartons before sealing the pallet."
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            if (!isSealed && allAttached) {
                Button(
                    onClick = onSeal,
                    enabled = !isSealing && !isCompleting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSealing) Color(0xFF9CA3AF) else WmsColors.Success,
                    ),
                ) {
                    if (isSealing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSealing) "Sealing Pallet…" else "Seal Pallet", fontWeight = FontWeight.Bold)
                }
            }

            if (isSealed) {
                Button(
                    onClick = onComplete,
                    enabled = !isCompleting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                ) {
                    Text(if (isCompleting) "Completing…" else "Complete Packaging", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = onLater,
                enabled = !isSealing && !isCompleting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Not now", fontWeight = FontWeight.Bold, color = WmsColors.Navy)
            }
        }
    }
}

@Composable
fun PackingPalletCartonsSection(
    palletId: String,
    cartons: List<WmsPackingBoxSummary>,
    linkedCount: Int,
    totalSealedCount: Int,
    allLinked: Boolean,
    isLoading: Boolean,
    palletInProgress: Boolean,
    onSealPallet: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Cartons on Pallet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                )
                if (totalSealedCount > 0) {
                    Text(
                        "$linkedCount/$totalSealedCount sealed cartons linked",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (allLinked) WmsColors.Success else WmsColors.Navy,
                    )
                } else {
                    Text(
                        "Pallet id: $palletId",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.Navy,
                    )
                }
            }
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                palletInProgress && allLinked && onSealPallet != null -> {
                    Button(
                        onClick = onSealPallet,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Success),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Seal Pallet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (totalSealedCount > 0) {
            val progress = linkedCount.toFloat() / totalSealedCount.coerceAtLeast(1).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE5E7EB)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (allLinked) WmsColors.Success else WmsColors.Navy),
                )
            }
        }

        if (cartons.isEmpty()) {
            Text(
                if (isLoading) "Loading…" else "No cartons on this pallet yet. Add sealed cartons below.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            )
        } else {
            cartons.forEach { carton ->
                PackingPalletCartonRow(carton = carton, isLinked = true)
            }
        }
    }
}

@Composable
fun PackingSealedCartonsToLinkSection(
    cartons: List<WmsPackingBoxSummary>,
    linkingCartonId: String?,
    isBusy: Boolean,
    onLink: (WmsPackingBoxSummary) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Sealed Cartons — Add to Pallet",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.TextPrimary,
        )
        Text(
            "These cartons are already sealed. Adding links them to this pallet — no new carton is created.",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = WmsColors.TextSecondary,
        )
        cartons.forEach { carton ->
            PackingSealedCartonLinkCard(
                carton = carton,
                isLinking = linkingCartonId == carton.id,
                isBusy = isBusy,
                onLink = { onLink(carton) },
            )
        }
    }
}

@Composable
fun PackingPalletCartonRow(
    carton: WmsPackingBoxSummary,
    isLinked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WmsColors.Success.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = WmsColors.Success,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    carton.displayTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextPrimary,
                )
                Text(
                    "id: ${carton.id}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Navy,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
            if (carton.resolvedSscc.isNotBlank()) {
                Text(
                    carton.resolvedSscc,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
            Text(
                "${carton.resolvedItemCount} lines · ${carton.resolvedTotalPackedQty} units · ${carton.normalizedBoxStatusLabel}",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WmsColors.Navy.copy(alpha = 0.85f),
            )
        }

        if (isLinked) {
            Text(
                "ON PALLET",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Success,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(WmsColors.Success.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        } else if (carton.isCompleted) {
            Icon(
                Icons.Default.Verified,
                contentDescription = null,
                tint = WmsColors.Success,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PackingSealedCartonLinkCard(
    carton: WmsPackingBoxSummary,
    isLinking: Boolean,
    isBusy: Boolean,
    onLink: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PackingPalletCartonRow(carton = carton, isLinked = false)

        Button(
            onClick = onLink,
            enabled = !isLinking && !isBusy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLinking) Color(0xFF9CA3AF) else WmsColors.Success,
            ),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            if (isLinking) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            } else {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (isLinking) "Linking…" else "Add to Pallet",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
