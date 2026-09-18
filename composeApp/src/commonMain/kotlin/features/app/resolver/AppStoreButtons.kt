package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import utils.openUrl

const val APP_STORE_URL = "https://apps.apple.com/in/app/ratifye/id6749010769"
const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.ratifye.app"

/** Apple logo path, matching the web resolver's App Store button. */
private val AppleIcon: ImageVector = ImageVector.Builder(
    name = "AppleIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.White)) {
        addPathNodes(
            "M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79" +
                "-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39" +
                "c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91" +
                ".65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04" +
                "-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.22-1.98 1.08-3.13-1.05.05-2.31.7" +
                "-3.06 1.58-.67.77-1.26 2.02-1.1 3.21 1.16.09 2.35-.59 3.08-1.66",
        )
    }
}.build()

/** Google Play 2022 brand mark (official geometry / colors). */
private val PlayStoreIcon: ImageVector = ImageVector.Builder(
    name = "PlayStoreIcon",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 29f,
    viewportHeight = 32f,
).apply {
    path(fill = SolidColor(Color(0xFFEA4335))) {
        addPathNodes("M13.54 15.28.12 29.34a3.66 3.66 0 0 0 5.33 2.16l15.1-8.6Z")
    }
    path(fill = SolidColor(Color(0xFFFBBC04))) {
        addPathNodes("m27.11 12.89-6.53-3.74-7.35 6.45 7.38 7.28 6.48-3.7a3.54 3.54 0 0 0 1.5-4.79 3.62 3.62 0 0 0-1.5-1.5z")
    }
    path(fill = SolidColor(Color(0xFF4285F4))) {
        addPathNodes("M.12 2.66a3.57 3.57 0 0 0-.12.92v24.84a3.57 3.57 0 0 0 .12.92L14 15.64Z")
    }
    path(fill = SolidColor(Color(0xFF34A853))) {
        addPathNodes("m13.64 16 6.94-6.85L5.5.51A3.73 3.73 0 0 0 3.63 0 3.64 3.64 0 0 0 .12 2.65Z")
    }
}.build()

/**
 * Official Ratifye mobile app store listings, mirroring the web resolver's
 * `AppStoreButtons` with the Apple and Google Play brand marks.
 */
@Composable
fun AppStoreButtons(
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF4338CA),
) {
    Row(
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoreBadge(icon = AppleIcon) { openUrl(APP_STORE_URL) }
        StoreBadge(icon = PlayStoreIcon) { openUrl(PLAY_STORE_URL) }
    }
}

@Composable
private fun StoreBadge(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(Color(0xFF111827))
            .size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}