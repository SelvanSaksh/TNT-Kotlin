package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import resolver.ResolverScreenState

private val Navy = Color(0xFF163E64)
internal val Green = Color(0xFF22C55E)
private val Paper = Color(0xFFF4F6F9)
private val Card = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0F2438)
private val Muted = Color(0xFF6B7C8F)
private val Line = Color(0xFFE2E8F0)
private val AuthRed = Color(0xFFEF4444)
private val ChainGreen = Color(0xFF16A34A)

@Composable
fun DefaultResolverScreen(
    state: ResolverScreenState,
    modifier: Modifier = Modifier,
) {
    val template = state.defaultTemplate
    val genuine = state.isReal != false
    val gtin = state.gtin?.takeIf { it.isNotBlank() }
        ?: state.gtinValue.takeIf { it != "—" }
        ?: "—"
    val serial = state.scanSerial.orEmpty().ifBlank { "—" }
    val location = state.currentAddress?.city?.takeIf { it.isNotBlank() }
        ?: template.scanLocation
    val regionMatch = if (genuine) template.regionMatch else false
    val regionMatchLoading = !state.locationSettled

    val mrpRow = template.identifierRows.firstOrNull { it.label.equals("mrp", ignoreCase = true) }
    val mrp = mrpRow?.value.orEmpty()
    val detailsRows = template.identifierRows.filterNot { it.label.equals("mrp", ignoreCase = true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        PharmaAuthBanner(
            verifying = state.isAuthLoading,
            genuine = genuine,
            hasVerdict = true,
            manufacturer = template.manufacturer,
        )

        PackCard(
            name = template.productName,
            meta = template.brandLine,
            imageUrl = template.imageUrl,
            firstScan = template.firstScan,
            genuine = genuine,
            mrp = mrp,
            detailsRows = detailsRows,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .offset(y = (-16).dp),
        )

        if (genuine && template.chain.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                SectionTitle("Distribution Path")
                DistributionChain(
                    steps = template.chain,
                    location = location,
                    regionMatch = regionMatch,
                    regionMatchLoading = regionMatchLoading,
                )
            }
        }

        if (genuine) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                SectionTitle("Track & Trace")
                TrackAndTraceCard(
                    authorisedLocation = template.authorisedLocation,
                    scans = template.scans,
                )
            }
        }

        if (template.regionMatch == false) {
            DiversionBand(body = template.regionBody)
        }

        PoweredByRatifye(lines = listOf("GS1 Digital Link Standard, Schedule H aligned"))
    }
}

@Composable
private fun PackCard(
    name: String,
    meta: String,
    imageUrl: String?,
    firstScan: Boolean,
    genuine: Boolean,
    mrp: String,
    detailsRows: List<DefaultIdentifierRow>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF0D2740).copy(alpha = 0.18f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp)) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFF0F4F8)),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = name.take(1),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Navy,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Text(
                    text = when {
                        genuine && firstScan -> "✓ 1st scan"
                        genuine -> "✓ Verified"
                        else -> "✕ Suspect"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                    color = if (genuine) Color(0xFF15803D) else AuthRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (genuine) Color(0xFFECFDF5) else Color(0xFFFEF2F2))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 20.sp,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = meta,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mrp.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mrp,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Navy,
                        fontFamily = FontFamily.Default,
                    )
                }
            }
        }

        if (detailsRows.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEDF1F6))
                    .padding(vertical = 1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                detailsRows.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        pair.forEach { row ->
                            IdentifierCell(row = row, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) {
                            Box(modifier = Modifier.weight(1f).background(Card))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentifierCell(row: DefaultIdentifierRow, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Card)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = row.label.uppercase(),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = Color(0xFF8A9AAC),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = row.value,
            fontSize = if (row.mono) 11.5.sp else 12.5.sp,
            fontWeight = if (row.mono) FontWeight.SemiBold else FontWeight.ExtraBold,
            fontFamily = if (row.mono) FontFamily.Monospace else FontFamily.Default,
            letterSpacing = if (row.mono) 0.2.sp else 0.sp,
            color = if (row.mono) Navy else Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(bottom = 11.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Green),
        )
        Text(
            text = text.uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ink,
            letterSpacing = 0.8.sp,
        )
    }
}

/**
 * Shared by the default resolver and the CMS distribution-path widget. White card
 * with the chain steps on the left and the region-match / latest-scan tiles right.
 */
@Composable
internal fun DistributionChain(
    steps: List<DefaultChainStep>,
    location: String,
    regionMatch: Boolean?,
    regionMatchLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val regionValue = when (regionMatch) {
        true -> "✓"
        false -> "✕"
        null -> "—"
    }
    val regionAlert = !regionMatchLoading && regionMatch == false

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            steps.forEachIndexed { index, step ->
                val last = index == steps.lastIndex
                val scannedStepFailure =
                    !regionMatchLoading && regionMatch == false && step.icon == DefaultChainIcon.RETAIL
                val flagged = step.flagged || scannedStepFailure
                val iconVector = when (step.icon) {
                    DefaultChainIcon.FACTORY -> Icons.Filled.Factory
                    DefaultChainIcon.WAREHOUSE -> Icons.Filled.Warehouse
                    DefaultChainIcon.DISTRIBUTOR -> Icons.Filled.Inventory2
                    DefaultChainIcon.RETAIL -> Icons.Filled.Storefront
                    DefaultChainIcon.ALERT -> Icons.Filled.Warning
                }

                Box(modifier = Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 22.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(31.dp)
                                .clip(CircleShape)
                                .background(if (flagged) AuthRed.copy(alpha = 0.22f) else Green.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(23.dp)
                                    .clip(CircleShape)
                                    .background(if (flagged) AuthRed else Green),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                            Text(
                                text = step.label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (flagged) Color(0xFFDC2626) else Ink,
                            )
                            if (step.subtitle.isNotBlank()) {
                                Text(
                                    text = step.subtitle,
                                    fontSize = 9.5.sp,
                                    lineHeight = 14.sp,
                                    color = if (flagged) Color(0xFFB91C1C) else Muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            step.note?.let { note ->
                                val within = note.startsWith("Within")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = note,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp,
                                    color = if (within) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (within) Color(0xFF22C55E).copy(alpha = 0.15f)
                                            else Color(0xFFEF4444).copy(alpha = 0.15f),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (!last) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 10.75.dp, top = 23.dp)
                                .width(1.5.dp)
                                .height(22.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0x40163E64),
                                            Color(0x0D163E64),
                                        ),
                                    ),
                                ),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.width(112.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (regionAlert) Color(0xFFFEF2F2) else Card)
                    .border(1.dp, if (regionAlert) Color(0xFFFECACA) else Line, RoundedCornerShape(12.dp))
                    .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (regionMatchLoading) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Line),
                    )
                } else {
                    Text(
                        text = regionValue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 15.sp,
                        color = if (regionAlert) Color(0xFFDC2626) else Navy,
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Region Match",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    color = if (regionAlert) Color(0xFFB91C1C) else Muted,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Card)
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (regionMatchLoading) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Line),
                    )
                } else {
                    Text(
                        text = location,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 15.sp,
                        color = Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Latest Scan",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TrackAndTraceCard(
    authorisedLocation: String,
    scans: List<DefaultTraceStep>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(2.dp, ChainGreen, RoundedCornerShape(14.dp))
            .padding(15.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(31.dp)
                    .clip(CircleShape)
                    .background(Green.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(23.dp)
                        .clip(CircleShape)
                        .background(Green),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                Text(
                    text = if (authorisedLocation.isNotBlank()) {
                        "Authorised · $authorisedLocation"
                    } else {
                        "Authorised zone"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(max = 168.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            scans.forEachIndexed { index, scan ->
                val last = index == scans.lastIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (last) 0.dp else 12.dp),
                ) {
                    Box(modifier = Modifier.width(30.dp)) {
                        if (!last) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 14.25.dp, top = 1.dp)
                                    .width(1.5.dp)
                                    .height(28.dp)
                                    .background(Color(0x4D22C55E)),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 6.5.dp, top = 1.dp)
                                .size(17.dp)
                                .clip(CircleShape)
                                .background(if (scan.flagged) Color(0xFFF87171).copy(alpha = 0.25f) else Green.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(if (scan.flagged) Color(0xFFF87171) else Green),
                            )
                        }
                    }
                    Text(
                        text = scan.title,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (scan.flagged) Color(0xFFDC2626) else Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp, end = 4.dp)
                            .align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiversionBand(body: String) {
    Row(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFFF1F2), Color(0xFFFFF7ED))))
            .border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(15.dp),
            )
        }
        Column {
            Text(
                text = "⚠ Region Match Alert",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF9F1239),
            )
            Text(
                text = body,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = Color(0xFF9F1239).copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}