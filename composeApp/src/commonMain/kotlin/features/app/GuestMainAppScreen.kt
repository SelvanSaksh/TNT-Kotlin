package features.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.app.scans.Scans

private val BlueAccent = Color(0xFF163C66)
private val SegmentBg = Color(0xFFEEF2F7)
private val SegmentIdle = Color(0xFF64748B)

private enum class GuestTab(val label: String, val icon: ImageVector) {
    Scan("Scan", Icons.Outlined.QrCodeScanner),
    History("History", Icons.Outlined.History),
}

@Composable
fun GuestMainAppScreen() {
    var activeTab by remember { mutableStateOf(GuestTab.Scan) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (activeTab) {
                GuestTab.Scan -> Scans(
                    onNavigate = {},
                    isGuestMode = true,
                )
                GuestTab.History -> GuestScanHistory()
            }
        }

        GuestSegmentedTabs(
            selected = activeTab,
            onSelect = { activeTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun GuestSegmentedTabs(
    selected: GuestTab,
    onSelect: (GuestTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val segmentWidth = (maxWidth - 10.dp) / GuestTab.entries.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selected.ordinal,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "guestTabIndicator",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SegmentBg, RoundedCornerShape(30.dp))
                .padding(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .height(54.dp)
                    .align(Alignment.CenterStart)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(27.dp),
                        ambientColor = BlueAccent.copy(alpha = 0.40f),
                        spotColor = BlueAccent.copy(alpha = 0.40f),
                    )
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF163C66), Color(0xFF2E6FB0)),
                        ),
                        RoundedCornerShape(27.dp),
                    ),
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                GuestTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .clickable { onSelect(tab) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else SegmentIdle,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tab.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color.White else SegmentIdle,
                        )
                    }
                }
            }
        }
    }
}