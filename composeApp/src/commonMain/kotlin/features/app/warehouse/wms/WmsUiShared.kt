package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun WmsBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(40.dp),
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = "Back",
            tint = WmsColors.Navy,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun WmsLightHeader(
    title: String,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            WmsBackButton(onClick = onBack, modifier = Modifier.size(32.dp))
        } else {
            Spacer(Modifier.size(32.dp))
        }

        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = if (title.length > 12) 22.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.TextPrimary,
        )

        if (!showBack && profileName != null && onLogout != null) {
            WmsProfileMenuButton(displayName = profileName, onLogout = onLogout)
        } else {
            Spacer(Modifier.size(36.dp))
        }
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
