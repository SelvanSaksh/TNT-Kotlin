package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Locked pharma teal navy. Every scan gets this background regardless of verdict or CMS theme. */
private val PharmaAuthNavy = Color(0xFF0F766E)
private val PharmaRed = Color(0xFFEF4444)
private val PharmaAmber = Color(0xFFF59E0B)
private val PharmaGreen = Color(0xFF22C55E)
private val PharmaGreenDeep = Color(0xFF16A34A)
private val PharmaAmberDeep = Color(0xFFD97706)
private val PharmaFailBase = Color(0xFFB91C1C)
private val BannerSubtitle = Color(0xFFBCD3E8)

private val StockBannerTitles = setOf(
    "authentic product",
    "verified banner",
    "ratifye verified",
    "verified",
)
private val StockBannerSubtitles = setOf(
    "cryptographically verified",
    "cryptographically verified.",
)

private fun customBannerText(value: String?, stock: Set<String>): String? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (text.lowercase() in stock) null else text
}

private fun shadeChannel(channel: Float, amount: Int): Float {
    val target = if (amount > 0) 1f else 0f
    val ratio = abs(amount) / 100f
    return channel + (target - channel) * ratio
}

private fun shadeHex(color: Color, amount: Int): Color =
    Color(
        shadeChannel(color.red, amount),
        shadeChannel(color.green, amount),
        shadeChannel(color.blue, amount),
    )

/**
 * Canonical authenticity hero: locked teal navy, 52px icon ring, reserved
 * heading/subtitle heights, CMS copy when present — mirroring the web.
 */
@Composable
fun PharmaAuthBanner(
    verifying: Boolean,
    genuine: Boolean,
    hasVerdict: Boolean,
    manufacturer: String,
    modifier: Modifier = Modifier,
    cmsTitle: String? = null,
    cmsSubtitle: String? = null,
    badge: String? = null,
) {
    val pending = verifying || !hasVerdict
    val failed = !pending && !genuine
    val base = if (failed) PharmaFailBase else PharmaAuthNavy
    val maker = manufacturer.trim().ifBlank { "the manufacturer" }

    val heading = when {
        verifying -> "Checking authenticity"
        failed -> "Not Ratify'd"
        !hasVerdict -> "Verification unavailable"
        else -> customBannerText(cmsTitle, StockBannerTitles) ?: "Ratifye'd"
    }
    val subtitle = when {
        verifying -> buildAnnotatedString {
            append("Matching this serial against the signed Digital Link.")
        }
        failed -> buildAnnotatedString {
            append("Authentication failed — this product could not be verified as genuine.")
        }
        !hasVerdict -> buildAnnotatedString {
            append("We could not reach the authentication service for this scan.")
        }
        else -> {
            val cms = customBannerText(cmsSubtitle, StockBannerSubtitles)
            if (cms != null) {
                buildAnnotatedString { append(cms) }
            } else {
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append("Certified Genuine")
                    }
                    append(" by $maker using multi-layer cryptographic encryption")
                }
            }
        }
    }

    val density = LocalDensity.current
    val glowRadius = with(density) { 26.dp.toPx() }
    val ringCenter = Offset(glowRadius, glowRadius)
    val ringBrush = Brush.radialGradient(
        colorStops = arrayOf(
            0f to when {
                failed -> PharmaRed.copy(alpha = 0.22f)
                pending -> PharmaAmber.copy(alpha = 0.22f)
                else -> PharmaGreen.copy(alpha = 0.22f)
            },
            0.65f to when {
                failed -> PharmaRed.copy(alpha = 0.0f)
                pending -> PharmaAmber.copy(alpha = 0.0f)
                else -> PharmaGreen.copy(alpha = 0.0f)
            },
        ),
        center = ringCenter,
        radius = glowRadius,
    )

    Column(
    modifier = modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .drawPharmaBackground(base)
        .padding(
            start = 14.dp,
            end = 14.dp,
            top = 0.dp,
            bottom = 20.dp,
        ),
) {
        // Row is dropped entirely when there is no badge — no dead space up top.
        if (badge != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = badge,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA8C3DC),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(ringBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val innerIconColor = if (failed) PharmaFailBase else Color.White
                val innerShadow = when {
                    failed -> Color(0xFFEF4444).copy(alpha = 0.40f)
                    pending -> Color(0xFFF59E0B).copy(alpha = 0.35f)
                    else -> Color(0xFF22C55E).copy(alpha = 0.40f)
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(20.dp, CircleShape, spotColor = innerShadow)
                        .clip(CircleShape)
                        .background(
                            if (failed) {
                                SolidColor(Color.White)
                            } else {
                                Brush.linearGradient(
                                    if (pending) {
                                        listOf(Color(0xFFFBBF24), PharmaAmberDeep)
                                    } else {
                                        listOf(PharmaGreen, PharmaGreenDeep)
                                    },
                                )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            failed -> Icons.Default.Close
                            pending -> Icons.Default.Warning
                            else -> Icons.Default.Check
                        },
                        contentDescription = null,
                        tint = innerIconColor,
                        modifier = Modifier.size(if (pending) 17.dp else 18.dp),
                    )
                }
            }
            // Heights are reserved so the banner never resizes between scans.
            Spacer(modifier = Modifier.height(4.dp))
            Text(
    text = heading,
    fontSize = 24.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.24).sp,
    lineHeight = 29.sp,
    color = Color.White,
    textAlign = TextAlign.Center,
    softWrap = true,
    maxLines = 2,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp),
)
            Text(
    text = subtitle,
    fontSize = 13.5.sp,
    lineHeight = 19.sp,
    color = if (failed) Color.White else BannerSubtitle,
    textAlign = TextAlign.Center,
    softWrap = true,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp)
        .padding(top = 2.dp),
)
        }
    }
}

private fun Modifier.drawPharmaBackground(base: Color): Modifier = this.drawBehind {
    val brush = Brush.radialGradient(
        colorStops = arrayOf(
            0f to shadeHex(base, 18),
            0.45f to base,
            1f to shadeHex(base, -38),
        ),
        center = Offset(size.width * 0.5f, 0f),
        radius = size.height * 1.15f,
    )
    drawRect(brush)
}