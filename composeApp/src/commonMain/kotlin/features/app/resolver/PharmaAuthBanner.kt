package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PharmaAuthNavy = Color(0xFF163E64)
private val PharmaAuthNavyMid = Color(0xFF1C5589)
private val PharmaAuthNavyDeep = Color(0xFF0D2740)
private val PharmaAuthFail = Color(0xFF9F1239)
private val PharmaGreen = Color(0xFF22C55E)
private val PharmaGreenDeep = Color(0xFF16A34A)
private val PharmaAmber = Color(0xFFFBBF24)
private val PharmaAmberDeep = Color(0xFFD97706)
private val PharmaRed = Color(0xFFEF4444)

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

/**
 * Canonical authenticity hero: pharma navy, fixed 78px icon, CMS copy when present.
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
    brandLine: String? = null,
    badge: String? = null,
) {
    val pending = verifying || !hasVerdict
    val failed = !pending && !genuine
    val top = if (failed) Color(0xFFBE123C) else PharmaAuthNavyMid
    val mid = if (failed) PharmaAuthFail else PharmaAuthNavy
    val deep = if (failed) Color(0xFF4C0519) else PharmaAuthNavyDeep
    val maker = manufacturer.trim().ifBlank { "the manufacturer" }

    val heading = when {
        verifying -> "Checking authenticity"
        failed -> "Non-Authentic Product"
        !hasVerdict -> "Verification unavailable"
        else -> customBannerText(cmsTitle, StockBannerTitles) ?: "Ratifye'd"
    }
    val statusLabel = when {
        verifying -> "Verifying…"
        failed -> "Not Verified"
        !hasVerdict -> "Not Verified Yet"
        else -> brandLine?.trim()?.takeIf { it.isNotEmpty() } ?: "Ratifye Verified"
    }
    val subtitle = when {
        verifying -> buildAnnotatedString {
            append("Matching this serial against the signed Digital Link.")
        }
        failed -> buildAnnotatedString {
            append("This serial did not match a genuine record. Do not consume this product.")
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
                    append(" by $maker using multi-layer Cryptographic encryption")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(top, mid, deep)))
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 30.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                failed -> PharmaRed
                                pending -> PharmaAmber
                                else -> PharmaGreen
                            },
                        ),
                )
                Text(
                    text = statusLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!badge.isNullOrBlank()) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            failed -> PharmaRed.copy(alpha = 0.22f)
                            pending -> PharmaAmber.copy(alpha = 0.22f)
                            else -> PharmaGreen.copy(alpha = 0.22f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                when {
                                    failed -> listOf(PharmaRed, Color(0xFFB91C1C))
                                    pending -> listOf(PharmaAmber, PharmaAmberDeep)
                                    else -> listOf(PharmaGreen, PharmaGreenDeep)
                                },
                            ),
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
                        tint = Color.White,
                        modifier = Modifier.size(if (pending) 24.dp else 26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = heading,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = Color(0xFFBCD3E8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
            )
        }
    }
}
