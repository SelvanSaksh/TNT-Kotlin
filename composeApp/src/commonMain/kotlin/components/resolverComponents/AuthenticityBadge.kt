package components.resolverComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import resolver.cms.isAuthenticQuality

@Composable
fun AuthenticityBadge(
    quality: String?,
    modifier: Modifier = Modifier,
) {
    if (quality.isNullOrBlank()) return
    val isReal = isAuthenticQuality(quality)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isReal) ResolverPalette.GreenSoft else ResolverPalette.RedSoft,
                RoundedCornerShape(12.dp),
            )
            .border(
                width = 2.dp,
                color = if (isReal) ResolverPalette.Green else ResolverPalette.Red,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isReal) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = null,
            tint = if (isReal) ResolverPalette.GreenStrong else ResolverPalette.RedStrong,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (isReal) "AUTHENTIC PRODUCT" else "NON-AUTHENTIC PRODUCT",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isReal) ResolverPalette.GreenText else ResolverPalette.RedText,
        )
    }
}
