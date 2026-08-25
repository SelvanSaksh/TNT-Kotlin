package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import resolver.ResolverScreenState
import utils.openGeneratedPdf
import utils.openUrl

private val Navy = Color(0xFF163E64)
private val NavyDeep = Color(0xFF0D2740)
private val Green = Color(0xFF22C55E)
private val Paper = Color(0xFFF4F6F9)
private val Card = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0F2438)
private val Muted = Color(0xFF6B7C8F)
private val Line = Color(0xFFE2E8F0)
private val AuthRed = Color(0xFFEF4444)

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
            brandLine = "Ratifye Verified",
            badge = "1 GS1 Digital Link",
        )

        PackCard(
            name = template.productName,
            meta = template.brandLine,
            imageUrl = template.imageUrl,
            firstScan = template.firstScan,
            genuine = genuine,
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            if (template.identifierRows.isNotEmpty()) {
                SectionTitle("Product Identifiers")
                IdentifierCard(rows = template.identifierRows)
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (template.chain.isNotEmpty()) {
                SectionTitle("Distribution Path")
                DistributionChain(steps = template.chain)
                Spacer(modifier = Modifier.height(20.dp))
            }

            SectionTitle("Track & Trace")
            StatChipsRow(
                timesScanned = template.timesScanned,
                location = location,
                regionMatch = if (genuine) template.regionMatch else false,
            )

            ScannedLocationsCard(
                authorisedLocation = template.authorisedLocation,
                timesScanned = template.timesScanned,
                scans = template.scans,
            )
        }

        if (template.regionMatch == false) {
            DiversionBand(body = template.regionBody)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "POWERED BY",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = Muted,
            )
            Spacer(modifier = Modifier.width(6.dp))
            AsyncImage(
                model = "https://ratifye.ai/assets/logo.png",
                contentDescription = "Ratifye",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(12.dp).width(48.dp),
            )
        }
    }
}

@Composable
private fun PackCard(
    name: String,
    meta: String,
    imageUrl: String?,
    firstScan: Boolean,
    genuine: Boolean,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .offset(y = (-16).dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Navy.copy(alpha = 0.12f)),
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Navy,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text(text = meta, fontSize = 11.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = if (genuine && firstScan) "✓ 1st scan" else if (genuine) "Verified" else "Suspect",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (genuine) Color(0xFF15803D) else AuthRed,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (genuine) Color(0xFFECFDF5) else Color(0xFFFEF2F2))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
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

@Composable
private fun IdentifierCard(rows: List<DefaultIdentifierRow>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(14.dp)),
    ) {
        val chunked = rows.chunked(2)
        chunked.forEach { rowPair ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowPair.forEachIndexed { index, row ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Card)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = row.label.uppercase(),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF8A9AAC),
                        )
                        Text(
                            text = row.value,
                            fontSize = if (row.mono) 11.5.sp else 12.5.sp,
                            fontWeight = if (row.mono) FontWeight.SemiBold else FontWeight.ExtraBold,
                            fontFamily = if (row.mono) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
                            letterSpacing = if (row.mono) 0.2.sp else 0.sp,
                            color = if (row.mono) Navy else Ink,
                            modifier = if (row.mono) Modifier else Modifier,
                        )
                    }
                    if (index == 0 && rowPair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (index < rowPair.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxWidth()
                                .background(Color(0xFFEDF1F6)),
                        )
                    }
                }
            }
            if (rowPair != chunked.lastOrNull()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEDF1F6)),
                )
            }
        }
    }
}

@Composable
private fun DistributionChain(steps: List<DefaultChainStep>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyDeep)
            .padding(15.dp),
    ) {
        steps.forEachIndexed { index, step ->
            val last = index == steps.lastIndex
            val iconVector = when (step.icon) {
                DefaultChainIcon.FACTORY -> Icons.Default.Warning
                DefaultChainIcon.WAREHOUSE -> Icons.Default.Warning
                DefaultChainIcon.DISTRIBUTOR -> Icons.Default.ShowChart
                DefaultChainIcon.RETAIL -> Icons.Default.ShoppingBag
                DefaultChainIcon.ALERT -> Icons.Default.Warning
            }

            Row(
                modifier = Modifier.padding(bottom = if (last) 0.dp else 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (step.flagged) AuthRed else Green),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Column {
                    Text(
                        text = step.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (step.flagged) Color(0xFFFECACA) else Color.White,
                    )
                    Text(
                        text = step.subtitle,
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        color = if (step.flagged) Color(0xFFFCA5A5) else Color(0xFF8DB1CF),
                    )
                    step.note?.let { note ->
                        val noteBg = if (note.startsWith("Within")) Color(0xFF22C55E).copy(alpha = 0.15f) else AuthRed.copy(alpha = 0.15f)
                        val noteColor = if (note.startsWith("Within")) Color(0xFF8EE6AC) else Color(0xFFFCA5A5)
                        Text(
                            text = note,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            color = noteColor,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(noteBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChipsRow(timesScanned: String, location: String, regionMatch: Boolean?) {
    val regionValue = when (regionMatch) {
        true -> "✓"
        false -> "✕"
        null -> "—"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatChip(
            value = timesScanned,
            label = "Times Scanned",
            modifier = Modifier.weight(1f),
        )
        StatChip(
            value = regionValue,
            label = "Region Match",
            modifier = Modifier.weight(1f),
            alert = regionMatch == false,
        )
        StatChip(
            value = location,
            label = "Latest Scan",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatChip(
    value: String,
    label: String,
    modifier: Modifier,
    alert: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (alert) Color(0xFFFEF2F2) else Card)
            .border(1.dp, if (alert) Color(0xFFFECACA) else Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = if (alert) Color(0xFFDC2626) else Navy,
            maxLines = 1,
        )
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (alert) Color(0xFFB91C1C) else Muted,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScannedLocationsCard(
    authorisedLocation: String,
    timesScanned: String,
    scans: List<DefaultTraceStep>,
) {
    if (scans.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NavyDeep)
            .padding(15.dp),
    ) {
        Text(
            text = "SCANNED LOCATIONS",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = Color(0xFF8DB1CF),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F7A9E)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
            Column {
                Text(
                    text = if (authorisedLocation.isNotBlank()) "Authorised · $authorisedLocation" else "Authorised zone",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = if (timesScanned == "0") "No scans recorded yet" else "$timesScanned scan${if (timesScanned == "1") "" else "s"} recorded",
                    fontSize = 9.5.sp,
                    color = Color(0xFF8DB1CF),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column {
            scans.forEachIndexed { index, scan ->
                Row(
                    modifier = Modifier.padding(bottom = if (index == scans.lastIndex) 0.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (scan.flagged) Color(0xFFF87171) else Green),
                    )
                    Column {
                        Text(
                            text = scan.title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scan.flagged) Color(0xFFFECACA) else Color.White,
                        )
                        Text(
                            text = scan.subtitle,
                            fontSize = 9.5.sp,
                            color = if (scan.flagged) Color(0xFFFCA5A5) else Color(0xFF8DB1CF),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionGrid(
    brandLabel: String,
    website: String?,
    onViewReport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionBtn(
                icon = Icons.Default.Add,
                iconBg = Color(0xFFEFF6FF),
                iconTint = Color(0xFF2563EB),
                label = "Nearby Genuine\nPharmacy",
                modifier = Modifier.weight(1f),
                onClick = { openUrl("https://www.google.com/maps/search/genuine+pharmacy+near+me") },
            )
            ActionBtn(
                icon = Icons.Default.Description,
                iconBg = Color(0xFFFEF2F2),
                iconTint = Color(0xFFDC2626),
                label = "View Report\n(PDF)",
                modifier = Modifier.weight(1f),
                onClick = onViewReport,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionBtn(
                icon = Icons.Default.ShoppingBag,
                iconBg = Color(0xFFFDF4FF),
                iconTint = Color(0xFFA21CAF),
                label = brandLabel,
                modifier = Modifier.weight(1f),
                onClick = { website?.let(::openUrl) },
            )
            ActionBtn(
                icon = Icons.Default.ShowChart,
                iconBg = Color(0xFFF0FDF4),
                iconTint = Color(0xFF16A34A),
                label = "View Supply\nChain Path",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionBtn(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
        }
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ink,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
        )
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
                imageVector = Icons.Default.Warning,
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

@Composable
private fun TraceCard(steps: List<DefaultTraceStep>) {
    Column(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NavyDeep)
            .padding(15.dp),
    ) {
        Text(
            text = "DISTRIBUTION SNAPSHOT",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = Color(0xFF8DB1CF),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        steps.forEach { step ->
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Green),
                )
                Column {
                    Text(text = step.title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = step.subtitle, fontSize = 9.5.sp, color = Color(0xFF8DB1CF))
                }
            }
        }
    }
}
