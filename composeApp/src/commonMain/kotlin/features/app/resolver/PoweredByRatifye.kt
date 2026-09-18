package features.app.resolver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val RATIFYE_LOGO_URL = "https://ratifye.ai/assets/logo.png"

/**
 * Page footer credit shown at the very bottom of every resolver screen,
 * mirroring the web resolver's `PoweredByRatifye`.
 */
@Composable
fun PoweredByRatifye(
    modifier: Modifier = Modifier,
    lines: List<String> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 26.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "POWERED BY",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                color = Color(0xFF93A3B4),
            )
            AsyncImage(
                model = RATIFYE_LOGO_URL,
                contentDescription = "Ratifye",
                modifier = Modifier.size(height = 17.dp, width = 60.dp),
            )
            Text(
                text = "\u00B7 Every serial is zero replication",
                fontSize = 10.sp,
                lineHeight = 16.sp,
                color = Color(0xFF6B7C8F),
            )
        }
        if (lines.isNotEmpty()) {
            Text(
                text = lines.joinToString("\n"),
                fontSize = 10.sp,
                lineHeight = 16.sp,
                color = Color(0xFF6B7C8F),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
