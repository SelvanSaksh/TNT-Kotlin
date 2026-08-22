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
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Default consumer template used when the CMS page API has no layout for the
 * scanned GTIN. Copy comes from `/productmaster/details/by-gtin`.
 */
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
            if (template.detailRows.isNotEmpty()) {
                SectionTitle("Product Details")
                DetailCard(rows = template.detailRows)
                Spacer(modifier = Modifier.height(12.dp))
            }
            ChipRow(
                timesScanned = template.timesScanned,
                location = location,
                regionMatch = if (genuine) template.regionMatch else false,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SectionTitle("Quick Actions")
            ActionGrid(
                brandLabel = template.brandActionLabel,
                website = template.website,
                onViewReport = {
                    val batch = state.scanBatch?.takeIf { it.isNotBlank() } ?: "scan"
                    openGeneratedPdf(
                        fileName = "Ratifye-Diversion-Report-$batch.pdf",
                        title = "Ratifye Diversion Report",
                        lines = listOf(
                            template.productName,
                            template.brandLine,
                            "GTIN: $gtin",
                            "Serial: $serial",
                            "Scan location: $location",
                            "Authorised location: ${state.expectedLocationLabel ?: "—"}",
                            template.regionBody,
                        ),
                    )
                },
            )
        }

        if (template.related.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 20.dp, top = 20.dp)) {
                SectionTitle("You May Also Need")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    template.related.forEach { product ->
                        RelatedCard(product)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
        }

        if (template.regionMatch == false) {
            DiversionBand(body = template.regionBody)
        }

        TraceCard(steps = template.trace)

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
private fun DetailCard(rows: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false),
                )
            }
            if (index != rows.lastIndex) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9)))
            }
        }
    }
}

@Composable
private fun ChipRow(timesScanned: String, location: String, regionMatch: Boolean?) {
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
            value = regionValue,
            label = "Region Match",
            modifier = Modifier.weight(1f),
            alert = regionMatch == false,
        )
        StatChip(timesScanned, "Times Scanned", Modifier.weight(1f))
        StatChip(location, "Scan Location", Modifier.weight(1f))
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
private fun RelatedCard(product: DefaultRelatedProduct) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(13.dp))
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Line, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (product.imageUrl != null) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = product.name.take(1),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Navy,
                )
            }
        }
        Text(
            text = product.name,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ink,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = product.subtitle, fontSize = 9.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
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
