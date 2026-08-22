package components.resolverComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compares the scan location against the invoice address. Hidden entirely until
 * both are known, which is what a null [matched] means.
 */
@Composable
fun DiversionBox(
    matched: Boolean?,
    isExpired: Boolean,
    modifier: Modifier = Modifier,
) {
    if (matched == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (matched) ResolverPalette.GreenSoft else ResolverPalette.RedSoft,
                RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = if (matched) ResolverPalette.GreenBorder else ResolverPalette.RedBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (matched) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (matched) ResolverPalette.GreenText else ResolverPalette.RedText,
            modifier = Modifier.size(16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = when {
                    isExpired -> "EXPIRED PRODUCT"
                    matched -> "LOCATION VERIFIED"
                    else -> "DIVERSION DETECTED"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (matched) ResolverPalette.GreenText else ResolverPalette.RedText,
            )
            Text(
                text = when {
                    isExpired -> "Product is expired"
                    matched -> "Current location matches invoice location"
                    else -> "Current location does NOT match invoice location"
                },
                fontSize = 10.sp,
                color = if (matched) ResolverPalette.GreenText else ResolverPalette.RedText,
            )
        }
    }
}
