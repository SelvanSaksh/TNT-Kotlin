package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.focus.onFocusChanged
import core.network.wms.WmsWarehouseLocation

object WmsColors {
    val Navy = Color(0xFF163C66)
    val NavyDeep = Color(0xFF0F2A47)
    val PageBg = Color(0xFFF5F6FA)
    val PageBgAlt = Color(0xFFF3F4F7)
    val ActiveBlue = Color(0xFF2563EB)
    val Success = Color(0xFF059669)
    val SuccessBg = Color(0xFFECFDF5)
    val SuccessBorder = Color(0xFFBBF7D0)
    val Warning = Color(0xFFD97706)
    val OrangeAccent = Color(0xFFF97316)
    val OrangeBadgeBg = Color(0xFFFFF7ED)
    val TabInactiveBg = Color(0xFFE8EEF5)
    val TextPrimary = Color(0xFF111827)
    val TextSecondary = Color(0xFF6B7280)
    val TextMuted = Color(0xFF9CA3AF)
    val Border = Color(0xFFE5E7EB)
    val ErrorBg = Color(0xFFFEF2F2)
    val ErrorFg = Color(0xFFB91C1C)
    val ErrorBorder = Color(0xFFFECACA)
}

fun Modifier.wmsRepeatClickable(
    enabled: Boolean = true,
    longPressDelayMillis: Long = 400,
    repeatIntervalMillis: Long = 70,
    onClick: () -> Unit,
): Modifier = composed {
    val onClickState = rememberUpdatedState(onClick)
    Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
            while (isActive) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!enabled) return@awaitPointerEventScope

                    onClickState.value()

                    val repeatJob = launch {
                        delay(longPressDelayMillis)
                        while (isActive) {
                            onClickState.value()
                            delay(repeatIntervalMillis)
                        }
                    }

                    waitForUpOrCancellation()
                    repeatJob.cancel()
                }
            }
        }
    }
}

@Composable
fun WmsProfileMenuButton(
    displayName: String,
    onLogout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"

    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WmsColors.Navy.copy(alpha = 0.12f))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
                fontSize = 16.sp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Text(
                text = displayName,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = WmsColors.TextPrimary,
            )
            HorizontalDivider(color = WmsColors.Border)
            DropdownMenuItem(
                text = {
                    Text(
                        "Logout",
                        color = WmsColors.ErrorFg,
                        fontWeight = FontWeight.Medium,
                    )
                },
                onClick = {
                    expanded = false
                    onLogout()
                },
            )
        }
    }
}

@Composable
fun WmsCircularBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 34.dp,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
    contentDescription: String = "Back",
) {
    Box(
        modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = contentDescription,
            tint = WmsColors.Navy,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun WmsBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WmsCircularBackButton(onClick = onClick, modifier = modifier)
}

@Composable
fun WmsLightHeader(
    title: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    subtitle: String? = null,
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (showBack) {
                WmsCircularBackButton(onClick = onBack)
            } else {
                Spacer(Modifier.size(34.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    fontSize = if (title.length > 18) 20.sp else 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                subtitle?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = WmsColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (!showBack && profileName != null && onLogout != null) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    WmsProfileMenuButton(displayName = profileName, onLogout = onLogout)
                }
            } else if (showBack) {
                Spacer(Modifier.size(34.dp).align(Alignment.CenterEnd))
            }
        }
        HorizontalDivider(color = Color.Black.copy(alpha = 0.04f))
    }
}

@Composable
fun WmsErrorBanner(message: String) {
    Text(
        message,
        modifier = Modifier
            .fillMaxWidth()
            .background(WmsColors.ErrorBg, RoundedCornerShape(8.dp))
            .padding(12.dp),
        color = WmsColors.ErrorFg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun WmsLineExceptionBanner(
    detail: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("⚠", fontSize = 10.sp, color = WmsColors.Warning)
            Text(
                text = if (detail.startsWith("Exception")) detail else "Exception: $detail",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WmsColors.Warning,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WmsColors.Warning.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("⚠", fontSize = 12.sp, color = WmsColors.Warning)
        Text(
            text = if (detail.startsWith("Exception")) detail else "Exception: $detail",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = WmsColors.Warning,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun WmsLoadingBlock(message: String) {
    WmsListSkeleton(count = 2)
}

@Composable
fun WmsStatChip(label: String, value: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(
                if (selected) WmsColors.Navy else Color.White,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .then(Modifier.fillMaxWidth()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else WmsColors.Navy,
        )
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) Color.White.copy(alpha = 0.85f) else WmsColors.TextMuted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WmsPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        content = content,
    )
}

@Composable
fun WmsProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(WmsColors.Border, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .background(WmsColors.Navy, CircleShape),
        )
    }
}

/** Rich bottom-sheet confirmation — used for pack/new-package and seal prompts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WmsRichConfirmSheet(
    visible: Boolean,
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.Inventory2,
    iconTint: Color = WmsColors.Navy,
    iconBackground: Color = Color(0xFFEFF6FF),
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String = "Cancel",
    onDismiss: () -> Unit,
    loading: Boolean = false,
    primaryColor: Color = WmsColors.Navy,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPrimary,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(primaryLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                        .clickable(enabled = !loading) { onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = secondaryLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WmsColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** Rich bottom-sheet with text input — used for tote switch and similar prompts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WmsRichInputSheet(
    visible: Boolean,
    title: String,
    message: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    fieldLabel: String? = null,
    hintContent: @Composable (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.Inventory2,
    iconTint: Color = WmsColors.Navy,
    iconBackground: Color = Color(0xFFEFF6FF),
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryLabel: String = "Cancel",
    onDismiss: () -> Unit,
    loading: Boolean = false,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            hintContent?.invoke()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fieldLabel?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.TextMuted,
                        letterSpacing = 0.6.sp,
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(placeholder, color = WmsColors.TextMuted) },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WmsColors.Navy,
                        unfocusedBorderColor = WmsColors.Border,
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                    ),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPrimary,
                    enabled = primaryEnabled && !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(primaryLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                        .clickable(enabled = !loading) { onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = secondaryLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WmsColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** Rich bottom-sheet error popup for WMS flows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WmsRichErrorSheet(
    visible: Boolean,
    title: String = "Something went wrong",
    message: String,
    onDismiss: () -> Unit,
) {
    if (!visible || message.isBlank()) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(WmsColors.ErrorBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = WmsColors.ErrorFg,
                    modifier = Modifier.size(30.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
            ) {
                Text("OK", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/** Staging location picker — loads warehouse locations and matches iOS staging UI. */
@Composable
fun PickerStagingLocationSection(
    stagingLocation: String,
    onStagingLocationChange: (String) -> Unit,
    locations: List<WmsWarehouseLocation>,
    isLoadingLocations: Boolean,
    listAlreadyStaged: Boolean,
    modifier: Modifier = Modifier,
) {
    var isFieldFocused by remember { mutableStateOf(false) }
    val filteredLocations = remember(stagingLocation, locations) {
        val query = stagingLocation.trim()
        if (query.isEmpty()) locations
        else locations.filter {
            it.displayLabel.contains(query, ignoreCase = true) ||
                it.locationId.contains(query, ignoreCase = true) ||
                it.locationCode?.contains(query, ignoreCase = true) == true ||
                it.locationName?.contains(query, ignoreCase = true) == true
        }
    }
    val showSuggestions = !listAlreadyStaged &&
        !isLoadingLocations &&
        filteredLocations.isNotEmpty() &&
        (isFieldFocused || stagingLocation.isNotBlank())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "STAGING LOCATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.TextMuted,
            letterSpacing = 0.6.sp,
        )

        if (listAlreadyStaged) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFECFDF5))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(18.dp))
                Text(
                    stagingLocation.ifBlank { "Tote staged" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextPrimary,
                )
            }
        } else {
            OutlinedTextField(
                value = stagingLocation,
                onValueChange = onStagingLocationChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFieldFocused = it.isFocused },
                placeholder = { Text("e.g. STAGE-A", color = WmsColors.TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                ),
                shape = RoundedCornerShape(10.dp),
            )

            if (isLoadingLocations) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = WmsColors.Navy,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (showSuggestions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(10.dp))
                        .background(Color.White),
                ) {
                    filteredLocations.take(6).forEachIndexed { index, location ->
                        if (index > 0) {
                            HorizontalDivider(color = WmsColors.Border)
                        }
                        Text(
                            location.displayLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStagingLocationChange(location.displayLabel)
                                    isFieldFocused = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = WmsColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

data class PickerStagingLineDisplay(
    val productName: String,
    val sku: String,
    val batch: String,
    val location: String,
    val pickedQty: Int,
    val requestedQty: Int,
    val isComplete: Boolean,
)

@Composable
fun PickerStagingHeroHeader(
    pickListId: String,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            WmsCircularBackButton(onClick = onBack)
            Text(
                pickListId,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                "STAGING",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WmsColors.SuccessBg)
                    .border(1.dp, WmsColors.SuccessBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Success,
                letterSpacing = 0.8.sp,
            )
        }
        HorizontalDivider(color = WmsColors.Border)
    }
}

@Composable
fun PickerStagingSummaryCard(
    toteNumber: String,
    pickedBatches: Int,
    totalBatches: Int,
    lineCount: Int,
    modifier: Modifier = Modifier,
) {
    val isComplete = totalBatches > 0 && pickedBatches >= totalBatches
    val progress = if (totalBatches > 0) pickedBatches.toFloat() / totalBatches else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isComplete) Color(0xFFECFDF5) else Color.White)
            .border(
                1.dp,
                if (isComplete) WmsColors.SuccessBorder else WmsColors.Border,
                RoundedCornerShape(16.dp),
            ),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (isComplete) WmsColors.Success else WmsColors.Navy),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(WmsColors.Success.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "ALL ITEMS PICKED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.Success,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        "Place tote at staging, then complete the pick list.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WmsColors.TextPrimary,
                        lineHeight = 20.sp,
                    )
                }
            }

            if (toteNumber.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(WmsColors.Navy.copy(alpha = 0.06f))
                        .border(1.dp, WmsColors.Navy.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Inventory2, null, tint = WmsColors.Navy, modifier = Modifier.size(16.dp))
                    Text("Tote", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextMuted)
                    Text(
                        toteNumber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = WmsColors.Navy,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PickerStagingStatChip("$pickedBatches/$totalBatches", "Batches", WmsColors.Navy, Modifier.weight(1f))
                PickerStagingStatChip("$lineCount", "Lines", WmsColors.ActiveBlue, Modifier.weight(1f))
                PickerStagingStatChip("${(progress * 100).toInt()}%", "Done", WmsColors.Success, Modifier.weight(1f))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(WmsColors.Border),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isComplete) WmsColors.Success else WmsColors.Navy),
                )
            }
        }
    }
}

@Composable
private fun RowScope.PickerStagingStatChip(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextMuted)
    }
}

@Composable
fun PickerStagingLineItemsCard(
    lines: List<PickerStagingLineDisplay>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, WmsColors.Border, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WmsColors.Navy.copy(alpha = 0.04f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "LINE ITEMS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.TextMuted,
                letterSpacing = 0.8.sp,
            )
            Text(
                "${lines.count { it.isComplete }}/${lines.size} picked",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
            )
        }

        if (lines.isEmpty()) {
            Text(
                "No line items to display.",
                modifier = Modifier.padding(16.dp),
                fontSize = 13.sp,
                color = WmsColors.TextSecondary,
            )
        } else {
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    HorizontalDivider(color = WmsColors.Border.copy(alpha = 0.6f))
                }
                PickerStagingLineRow(line)
            }
        }
    }
}

@Composable
private fun PickerStagingLineRow(line: PickerStagingLineDisplay) {
    val accent = if (line.isComplete) WmsColors.Success else WmsColors.Navy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent.copy(alpha = if (line.isComplete) 1f else 0.35f)),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.productName,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.isComplete) {
                    Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "SKU: ${line.sku.ifBlank { "—" }}",
                fontSize = 11.sp,
                color = WmsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.batch.isNotBlank()) {
                Text(
                    line.batch,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = WmsColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (line.location.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(WmsColors.Navy.copy(alpha = 0.06f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = WmsColors.Navy, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            line.location,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = WmsColors.Navy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "${line.pickedQty}/${line.requestedQty}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (line.isComplete) WmsColors.Success else WmsColors.ActiveBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            (if (line.isComplete) WmsColors.Success else WmsColors.ActiveBlue).copy(alpha = 0.1f),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
