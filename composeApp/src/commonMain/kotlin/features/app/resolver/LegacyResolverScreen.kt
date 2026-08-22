package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.resolverComponents.AuthenticityBadge
import components.resolverComponents.DistributionPath
import components.resolverComponents.DiversionBox
import components.resolverComponents.InfoTile
import components.resolverComponents.ProductHighlights
import components.resolverComponents.RawParserOutput
import components.resolverComponents.ResolverPalette
import components.resolverComponents.ScanIntelligence
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import resolver.ResolverScreenState
import resolver.formatGs1Date
import resolver.isDateAi
import resolver.isExpired
import resolver.isExpiringSoon
import resolverModels.ApplicationIdentifier

/**
 * Config-driven fallback shown when the brand has no matching CMS page.
 * Mirrors the legacy layout of the web resolver.
 */
@Composable
fun LegacyResolverScreen(
    state: ResolverScreenState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isAuthLoading) {
            InlineStatusRow(
                text = "Verifying authenticity…",
                background = ResolverPalette.PageBackground,
                border = ResolverPalette.Border,
                textColor = ResolverPalette.TextMuted,
            )
        } else if (!state.authQuality.isNullOrBlank()) {
            AuthenticityBadge(quality = state.authQuality)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "GS1 DIGITAL LINK",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                color = ResolverPalette.TextFaint,
            )
            Text(
                text = if (state.isProductLoading) "Loading…" else state.productName,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ResolverPalette.TextStrong,
            )
            state.brandName?.let {
                Text(text = it, fontSize = 12.sp, color = ResolverPalette.TextMuted)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ResolverPalette.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, ResolverPalette.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "GTIN",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    color = ResolverPalette.TextFaint,
                )
                Text(
                    text = state.gtinValue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = ResolverPalette.Blue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy GTIN",
                tint = if (copied) ResolverPalette.Green else ResolverPalette.TextFaint,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(30.dp)
                    .clickable {
                        clipboard.setText(AnnotatedString(state.gtinValue))
                        copied = true
                    }
                    .padding(7.dp),
            )
        }

        val tiles = state.data?.identifiers?.filter { it.code != "01" }.orEmpty()
        if (tiles.isNotEmpty()) {
            IdentifierGrid(tiles)
        }

        if (state.isProductLoading) {
            InlineStatusRow(
                text = "Loading product data…",
                background = ResolverPalette.Surface,
                border = ResolverPalette.Border,
                textColor = ResolverPalette.TextFaint,
            )
        } else {
            state.productData?.let {
                ProductHighlights(
                    productData = it,
                    allergen = state.allergen,
                    sideEffects = state.sideEffects,
                )
            }
        }

        when {
            state.isLocationLoading -> InlineStatusRow(
                text = "Fetching location…",
                background = ResolverPalette.BlueSoft,
                border = ResolverPalette.BlueBorder,
                textColor = ResolverPalette.Blue,
            )

            state.locationError != null -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ResolverPalette.AmberSoft, RoundedCornerShape(12.dp))
                    .border(1.dp, ResolverPalette.AmberBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ResolverPalette.AmberBorder,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = state.locationError.orEmpty(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ResolverPalette.AmberText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "RETRY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResolverPalette.AmberText,
                    modifier = Modifier
                        .background(ResolverPalette.AmberChip, RoundedCornerShape(8.dp))
                        .clickable { scope.launch { state.requestLocation() } }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            state.currentLocation == null -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ResolverPalette.BlueSoft, RoundedCornerShape(12.dp))
                    .border(1.dp, ResolverPalette.BlueBorder, RoundedCornerShape(12.dp))
                    .clickable { scope.launch { state.requestLocation() } }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = ResolverPalette.BlueText,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Share Location for Diversion Check",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ResolverPalette.Blue,
                )
            }
        }

        DiversionBox(matched = state.isLocationMatched, isExpired = state.isExpiredProduct)

        DistributionPath(steps = state.distributionSteps)

        ScanIntelligence(payload = state.scanIntelligencePayload())

        val raw = state.data
        if (state.productData == null && !state.isProductLoading &&
            raw != null && raw.identifiers.isNotEmpty()
        ) {
            RawParserOutput(payload = raw.raw)
        }
    }
}

/** Two-column grid; Compose has no CSS grid, so rows are built in pairs. */
@Composable
private fun IdentifierGrid(identifiers: List<ApplicationIdentifier>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        identifiers.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { identifier ->
                    val expiryWarning = identifier.code == "17" &&
                        (isExpired(identifier.value) || isExpiringSoon(identifier.value))
                    InfoTile(
                        label = "${identifier.name} (${identifier.code})",
                        value = if (isDateAi(identifier.code)) {
                            formatGs1Date(identifier.value)
                        } else {
                            identifier.value
                        },
                        warn = expiryWarning,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InlineStatusRow(
    text: String,
    background: Color,
    border: Color,
    textColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = textColor,
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = textColor,
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }
}

private fun ResolverScreenState.scanIntelligencePayload(): JsonObject = buildJsonObject {
    val parsed = data
    put(
        "identifiers",
        parsed?.identifiers?.let { list ->
            buildJsonArray {
                list.forEach { identifier ->
                    add(
                        buildJsonObject {
                            put("code", identifier.code)
                            put("name", identifier.name)
                            put("value", identifier.value)
                            put("category", identifier.category)
                            put("source", identifier.source)
                        },
                    )
                }
            }
        } ?: JsonArray(emptyList()),
    )
    put(
        "specialIdentifiers",
        parsed?.specialIdentifiers?.let { list ->
            buildJsonArray {
                list.forEach { identifier ->
                    add(
                        buildJsonObject {
                            put("code", identifier.code)
                            put("name", identifier.name)
                            put("value", identifier.value)
                            put("source", identifier.source)
                        },
                    )
                }
            }
        } ?: JsonArray(emptyList()),
    )
    put(
        "authResult",
        authResult?.let { result ->
            buildJsonObject {
                put("barcodeData", result.barcodeData)
                put("encryptedText", result.encryptedText)
                put("quality", result.quality)
                put(
                    "gs1Data",
                    buildJsonObject {
                        result.gs1Data?.forEach { (code, entry) ->
                            put(
                                code,
                                buildJsonObject {
                                    put("name", entry["name"])
                                    put("value", entry["value"])
                                },
                            )
                        }
                    },
                )
            }
        } ?: JsonNull,
    )
    put("productData", productData ?: JsonNull)
    put("domain", parsed?.domain)
    put("timestamp", parsed?.timestamp)
}
