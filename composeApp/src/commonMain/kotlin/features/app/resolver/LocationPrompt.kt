package features.app.resolver

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LocationNavy = Color(0xFF0F2438)
private val LocationText = Color(0xFF6B7C8F)
private val LocationAction = Color(0xFF163E64)
private val LocationAmber = Color(0xFFB45309)

/**
 * Bottom sheet telling the user location is off and offering a retry,
 * mirroring the web resolver's `LocationPrompt`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPrompt(
    denied: Boolean,
    busy: Boolean,
    message: String?,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Turn on location",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = LocationNavy,
                    )
                    Text(
                        text = if (denied) {
                            "Location is blocked for this app. Open its settings, allow Location, then tap Retry."
                        } else {
                            "We use your location to confirm this pack was scanned inside the authorised distribution zone."
                        },
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = LocationText,
                    )
                    if (message != null && !denied) {
                        Text(
                            text = message,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = LocationAmber,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (busy) "Checking\u2026" else if (denied) "Retry" else "Turn on location",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LocationAction)
                                .clickable(enabled = !busy, onClick = onEnable)
                                .padding(vertical = 12.dp),
                        )
                        Text(
                            text = "Not now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocationText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss location prompt",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(4.dp),
                )
            }
        }
    }
}
