package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.resolverComponents.ResolverPalette
import resolver.ResolverScreenState
import resolver.rememberResolverScreenState

/**
 * Full-screen resolver for a scanned `dl.ratifye.ai` link. Shows the brand's CMS
 * passport when one exists for the scanned GTIN, otherwise the default
 * Ratifye template.
 *
 * Dismissal is left to the host, which closes on the system back gesture.
 */
@Composable
fun DigitalLinkResolverScreen(
    url: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberResolverScreenState(url)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ResolverPalette.PageBackground),
    ) {
        ResolverContent(state)
    }
}

@Composable
private fun ResolverContent(state: ResolverScreenState) {
    // The CMS check decides which screen to render, so wait for it before drawing.
    if (state.isLoading ||
        (state.gtin != null && !state.cmsChecked) ||
        (!state.hasCmsPage && state.isProductDetailsLoading)
    ) {
        ResolverMessage {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = ResolverPalette.Blue,
                trackColor = ResolverPalette.Border,
            )
            Text(
                text = "Resolving Digital Link…",
                fontSize = 12.sp,
                color = ResolverPalette.TextFaint,
            )
        }
        return
    }

    state.error?.let { message ->
        ResolverMessage {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .background(ResolverPalette.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, ResolverPalette.Border, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = ResolverPalette.Red,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "Resolution Failed",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResolverPalette.TextStrong,
                )
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = ResolverPalette.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val page = state.cmsPage
    if (state.hasCmsPage && page?.content != null) {
        CmsPassportScreen(page = page, state = state)
        return
    }

    DefaultResolverScreen(state = state)
}

@Composable
private fun ResolverMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = { content() },
        )
    }
}
