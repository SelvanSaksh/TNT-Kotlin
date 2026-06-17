package components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF163C66)
private val PageBackground = Color(0xFFF5F6FA)
private val TitleColor = Color(0xFF111827)
private val BodyColor = Color(0xFF6B7280)
private val HintColor = Color(0xFF374151)

@Composable
fun NoInternetConnectionView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
    }

    val illustrationAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "illustrationAlpha",
    )
    val illustrationScale by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 550),
        label = "illustrationScale",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 80),
        label = "textAlpha",
    )
    val hintsAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 160),
        label = "hintsAlpha",
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 220),
        label = "buttonAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .alpha(illustrationAlpha)
                    .scale(illustrationScale),
                contentAlignment = Alignment.Center,
            ) {
                NoInternetAnimatedIllustration()
            }

            Column(
                modifier = Modifier
                    .padding(top = 28.dp, start = 4.dp, end = 4.dp)
                    .alpha(textAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "No Internet Connection",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TitleColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Turn on Wi‑Fi or mobile data to continue using Ratifye.",
                    fontSize = 15.sp,
                    color = BodyColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            Column(
                modifier = Modifier
                    .padding(top = 22.dp, start = 12.dp, end = 12.dp)
                    .alpha(hintsAlpha),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConnectionHintRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Wifi,
                            contentDescription = null,
                            tint = Navy,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = "Check Wi‑Fi is enabled",
                )
                ConnectionHintRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = Navy,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = "Check mobile data is on",
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(buttonAlpha)
                    .padding(bottom = 36.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Navy,
                    contentColor = Color.White,
                ),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                )
                Text(
                    text = "Try Again",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ConnectionHintRow(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = HintColor,
        )
    }
}
