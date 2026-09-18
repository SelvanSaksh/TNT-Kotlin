package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonObject
import resolver.cms.ResolverCmsComponent
import resolver.cms.cmsArray
import resolver.cms.cmsCurrency
import resolver.cms.cmsDict
import resolver.cms.cmsDisplayString
import resolver.cms.cmsDouble
import resolver.cms.cmsMakeURL
import resolver.cms.cmsRealValue
import resolver.cms.cmsResolve
import resolver.cms.cmsString
import resolver.cms.cmsStyle
import resolver.cms.cmsUnwrappedString

private val SectionInk = Color(0xFF0F2438)
private val SectionMuted = Color(0xFF6B7C8F)
private val SectionLine = Color(0xFFE2E8F0)
private val SectionGrid = Color(0xFFEDF1F6)
private val SectionNavy = Color(0xFF163E64)
private val SectionGreen = Color(0xFF22C55E)
private val SectionGreenDeep = Color(0xFF16A34A)
private val SectionRed = Color(0xFFDC2626)
private val SectionSlate = Color(0xFF8A9AAC)

/** Green section-title bar used by section-page CMS widgets. */
@Composable
internal fun CmsSectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier.padding(bottom = 11.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SectionGreen),
        )
        Text(
            text = text.uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SectionInk,
            letterSpacing = 0.7.sp,
        )
    }
}

/** Live authenticity hero driven by the scan runtime context. */
@Composable
internal fun CmsBanner(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val scan = context.scan
    val manufacturer = cmsRealValue(cmsResolve("product.companyName", context.root))
        ?: scan.companyLabel
        ?: cmsRealValue(cmsResolve("product.brandName", context.root))
        ?: scan.brandName
        ?: "the manufacturer"
    PharmaAuthBanner(
        verifying = context.isAuthLoading,
        genuine = context.isGenuine,
        hasVerdict = context.hasAuthVerdict,
        manufacturer = manufacturer,
        cmsTitle = cmsRealValue(component.prop("title", context.root)),
        cmsSubtitle = cmsRealValue(component.prop("subtitle", context.root)),
        badge = cmsRealValue(component.prop("badge", context.root)),
        modifier = modifier,
    )
}

/** Compact pack card for the top of section pages. */
@Composable
internal fun CmsPackCard(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val root = context.root
    val scan = context.scan
    val style = cmsStyle(component.styles)
    val pending = context.isAuthLoading || !context.hasAuthVerdict
    val suspect = !pending && !context.isGenuine
    var failed by remember { mutableStateOf(false) }
    val imageUrl = cmsMakeURL(
        cmsUnwrappedString(component.prop("image", root, listOf("images")))
            ?: cmsUnwrappedString(cmsResolve("product.images[0]", root)),
    )
    remember(imageUrl) { failed = false }

    val title = cmsRealValue(component.prop("title", root, listOf("productName", "name")))
        ?: cmsRealValue(cmsResolve("product.productName", root))
        ?: "Product"
    val brand = cmsRealValue(component.prop("brand", root, listOf("brandName")))
        ?: cmsRealValue(cmsResolve("product.brandName", root))
        ?: cmsRealValue(cmsResolve("product.companyName", root))
        ?: scan.brandName
        ?: ""
    val batch = scan.batchNumber?.trim()
        ?: cmsRealValue(component.prop("meta", root))
        ?: ""
    val meta = listOf(
        brand.takeIf { it.isNotBlank() }.orEmpty(),
        batch.takeIf { it.isNotBlank() }?.let { "Batch $it" }.orEmpty(),
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    val cmsBadge = cmsRealValue(component.prop("badge", root))
    val badge = when {
        context.isAuthLoading -> "Checking…"
        suspect -> "Suspect"
        !context.hasAuthVerdict -> "Unverified"
        else -> "✓ ${cmsBadge ?: "Verified"}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (style.radius > 0) style.radius.dp else 16.dp))
            .background(parseCmsColor(style.background) ?: Color.White)
            .border(
                1.dp,
                parseCmsColor(cmsString(component.styles["borderColor"])) ?: SectionLine,
                RoundedCornerShape(if (style.radius > 0) style.radius.dp else 16.dp),
            )
            .padding(if (style.padding > 0) style.padding.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SectionNavy.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null && !failed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    onError = { failed = true },
                )
            } else {
                Text(
                    text = title.take(1),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SectionNavy,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SectionInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = meta,
                    fontSize = 11.sp,
                    color = SectionMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = badge,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                pending -> SectionMuted
                suspect -> SectionRed
                else -> Color(0xFF15803D)
            },
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        pending -> Color(0xFFF1F5F9)
                        suspect -> Color(0xFFFEF2F2)
                        else -> Color(0xFFECFDF5)
                    },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Resolves a `field_grid` label to live scan/product data. */
private fun cmsFieldValueFor(
    label: String,
    root: JsonObject,
    scan: CmsScanContext,
): Pair<String?, Boolean> {
    val key = label.lowercase().replace(Regex("[^a-z]"), "")
    val product = cmsDict(root["product"])
    fun pick(vararg values: String?): String? {
        for (value in values) {
            val clean = value?.trim().orEmpty()
            if (clean.isNotEmpty() && clean != "0") return clean
        }
        return null
    }

    if (key.contains("gtin") || key == "ean" || key.contains("barcode")) {
        return pick(scan.gtin, cmsRealValue(product?.get("identifier"))) to true
    }
    if (key.contains("serial")) {
        return pick(scan.serialNumber) to true
    }
    if (key.contains("batch") || key.contains("lot")) {
        return pick(scan.batchNumber) to true
    }
    if (key.contains("mfg") || key.contains("manufacturingdate")) {
        return pick(scan.mfgDate) to false
    }
    if (key.contains("expiry") || key.contains("expdate") || key == "exp") {
        return pick(scan.expiryDate) to false
    }
    if (key.contains("manufacturer") || key.contains("company")) {
        return pick(
            cmsRealValue(product?.get("companyName")),
            scan.companyLabel,
            cmsRealValue(product?.get("brandName")),
            scan.brandName,
        ) to false
    }
    if (key.contains("brand")) {
        return pick(cmsRealValue(product?.get("brandName")), scan.brandName) to false
    }
    if (key.contains("hsn")) {
        return pick(cmsRealValue(product?.get("hsn"))) to true
    }
    if (key.contains("mrp") || key.contains("price")) {
        val mrp = cmsDouble(product?.get("mrp"))
        return if (mrp != null && mrp > 0) cmsCurrency(mrp) to false else null to false
    }
    if (key.contains("origin")) {
        return pick(cmsRealValue(product?.get("countryOfOrigin"))) to false
    }
    if (key.contains("product") || key.contains("name")) {
        return pick(cmsRealValue(product?.get("productName"))) to false
    }
    if (key.contains("location") || key.contains("scan")) {
        return pick(scan.locationAddress, scan.locationLabel) to false
    }
    return null to false
}

/** The only identifiers shown, in the order they are rendered. */
private val IDENTIFIER_FIELDS = listOf(
    "gtin" to "GTIN",
    "manufacturer" to "Manufacturer",
    "batch" to "Batch No.",
    "serial" to "Serial",
    "expiry" to "Expiry",
    "mrp" to "MRP",
)

/** Maps a CMS field label onto one of the whitelisted identifiers. */
private fun identifierKey(label: String): String? {
    val key = label.lowercase().replace(Regex("[^a-z]"), "")
    return when {
        key.contains("gtin") || key == "ean" || key.contains("barcode") -> "gtin"
        key.contains("manufacturer") || key.contains("company") -> "manufacturer"
        key.contains("batch") || key.contains("lot") -> "batch"
        key.contains("serial") -> "serial"
        key.contains("expiry") || key.contains("expdate") || key == "exp" -> "expiry"
        key.contains("mrp") || key.contains("price") -> "mrp"
        else -> null
    }
}

@Composable
internal fun CmsFieldGrid(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val root = context.root
    val scan = context.scan
    val style = cmsStyle(component.styles)
    val raw = component.prop("fields", root) ?: component.props["fields"]

    val entries = mutableListOf<Pair<String, String?>>()
    val rawString = cmsString(raw)
    when {
        rawString != null -> rawString.split(Regex("\\n+")).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val colon = trimmed.indexOf(':')
            if (colon > 0) {
                entries.add(trimmed.substring(0, colon).trim() to trimmed.substring(colon + 1).trim())
            } else {
                entries.add(trimmed to null)
            }
        }
        else -> (cmsArray(raw) ?: emptyList()).forEach { item ->
            val dict = cmsDict(item) ?: return@forEach
            val label = cmsString(dict["label"]) ?: cmsString(dict["key"]) ?: return@forEach
            entries.add(label to (cmsDisplayString(dict["value"] ?: dict["text"])).takeIf { it.isNotEmpty() })
        }
    }

    val authored = HashMap<String, String?>()
    entries.forEach { (label, value) ->
        val key = identifierKey(label)
        if (key != null && !authored.containsKey(key)) authored[key] = value
    }

    val rows = mutableListOf<Triple<String, String, Boolean>>()
    IDENTIFIER_FIELDS.forEach { (key, label) ->
        val live = cmsFieldValueFor(label, root, scan)
        val resolved = live.first ?: authored[key]
        if (!resolved.isNullOrBlank()) rows.add(Triple(label, resolved, live.second))
    }
    if (rows.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (style.radius > 0) style.radius.dp else 16.dp))
            .border(1.dp, SectionLine, RoundedCornerShape(if (style.radius > 0) style.radius.dp else 16.dp))
            .background(SectionGrid)
            .padding(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        rows.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                pair.forEach { (label, value, mono) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = label.uppercase(),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = SectionSlate,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = value,
                            fontSize = if (mono) 11.5.sp else 12.5.sp,
                            fontWeight = if (mono) FontWeight.SemiBold else FontWeight.ExtraBold,
                            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                            letterSpacing = if (mono) 0.2.sp else 0.sp,
                            color = if (mono) SectionNavy else SectionInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pair.size == 1) {
                    Box(modifier = Modifier.weight(1f).background(Color.White))
                }
            }
        }
    }
}

internal fun cmsChipsHaveLinks(component: ResolverCmsComponent, root: JsonObject): Boolean {
    val raw = component.prop("chips", root) ?: component.props["chips"]
    for (item in cmsArray(raw) ?: emptyList()) {
        val dict = cmsDict(item) ?: continue
        cmsString(dict["url"])?.trim()?.takeIf { it.isNotEmpty() }?.let { return true }
    }
    return false
}

/** Track & Trace stat row — chips without links render as stat cards. */
@Composable
internal fun CmsStatChipsRow(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val root = context.root
    val scan = context.scan
    val raw = component.prop("chips", root) ?: component.props["chips"]

    data class Stat(val value: String, val label: String, val alert: Boolean)
    val stats = mutableListOf<Stat>()
    for (item in cmsArray(raw) ?: emptyList()) {
        val dict = cmsDict(item)
        val label = dict?.let { cmsString(it["label"]) ?: cmsString(it["text"]) }
            ?: cmsString(item)
            ?: continue
        val clean = label.trim()
        if (clean.isEmpty()) continue
        val parts = clean.split(Regex("\\s+"))
        val head = parts.first()
        val caption = if (parts.size > 1) parts.drop(1).joinToString(" ") else clean
        val key = caption.lowercase()

        var value = if (parts.size > 1) head else clean
        var alert = Regex("✓|outside|fail|mismatch|alert", RegexOption.IGNORE_CASE).containsMatchIn(clean)
        when {
            key.contains("times scanned") || key.contains("scan count") -> {
                value = (scan.timesScanned ?: scan.scanEvents.size.toDouble()).toInt().toString()
            }
            key.contains("latest scan") || key.contains("scan location") -> {
                value = scan.locationLabel?.split(",")?.first()?.trim().orEmpty().ifBlank { "—" }
            }
            key.contains("region match") || key.contains("zone") -> {
                val matched = scan.locationMatched
                value = when (matched) {
                    true -> "✓"
                    false -> "✕"
                    null -> "—"
                }
                alert = matched == false
            }
        }
        stats.add(Stat(value, if (parts.size > 1) caption else "", alert))
    }
    if (stats.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.forEach { stat ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (stat.alert) Color(0xFFFEF2F2) else Color.White)
                    .border(
                        1.dp,
                        if (stat.alert) Color(0xFFFECACA) else SectionLine,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stat.value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = if (stat.alert) SectionRed else SectionNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stat.label.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stat.label.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        color = if (stat.alert) Color(0xFFB91C1C) else SectionMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Distribution path for scan-driven pages: invoice destination vs scanned location. */
@Composable
internal fun CmsDistributionPathCard(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val scan = context.scan
    val invoice = scan.expectedLocation?.trim().orEmpty()
    val scanned = scan.locationLabel?.trim().orEmpty()
    val address = scan.locationAddress?.trim().orEmpty()
    val matched = scan.locationMatched

    val steps = mutableListOf<DefaultChainStep>()
    if (invoice.isNotEmpty()) {
        steps.add(
            DefaultChainStep(
                label = "Invoice destination",
                subtitle = invoice,
                icon = DefaultChainIcon.DISTRIBUTOR,
            ),
        )
    }
    steps.add(
        DefaultChainStep(
            label = if (scanned.isNotEmpty()) "Scanned at $scanned" else "Scanned location",
            subtitle = address.ifEmpty { scanned.ifEmpty { "Awaiting device location" } },
            note = when (matched) {
                true -> "Within licensed zone"
                false -> "Outside licensed zone"
                null -> null
            },
            icon = DefaultChainIcon.RETAIL,
        ),
    )
    if (matched == false) {
        steps.add(
            DefaultChainStep(
                label = "⚠ Diversion Alert",
                subtitle = if (invoice.isNotEmpty()) {
                    "Scanned in ${scanned.ifEmpty { "another city" }} — outside licensed $invoice zone"
                } else {
                    "Scanned in ${scanned.ifEmpty { "another city" }} — outside the licensed zone"
                },
                icon = DefaultChainIcon.ALERT,
                flagged = true,
            ),
        )
    }

    DistributionChain(
        steps = steps,
        location = scanned,
        regionMatch = matched,
        regionMatchLoading = false,
        modifier = modifier,
    )
}

/** Track & Trace scan history as a tree branching off the authorised location. */
@Composable
internal fun CmsScanTreeCard(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val scan = context.scan
    val style = cmsStyle(component.styles)
    val invoice = scan.expectedLocation?.trim().orEmpty()
    val scanned = scan.locationLabel?.trim().orEmpty()
    val matched = scan.locationMatched

    data class ScanLeaf(val label: String, val flagged: Boolean)
    val leaves = mutableListOf<ScanLeaf>()
    scan.scanEvents.forEach { event ->
        val label = event.location ?: event.coords ?: return@forEach
        if (label.isEmpty()) return@forEach
        leaves.add(ScanLeaf(label, event.flagged))
    }
    if (leaves.isEmpty()) {
        val here = scanned.ifEmpty { scan.locationAddress?.trim().orEmpty() }
        if (here.isEmpty() && invoice.isEmpty()) {
            CmsTimeline(component, context, modifier)
            return
        }
        if (here.isNotEmpty()) leaves.add(ScanLeaf(here, matched == false))
    }

    val label = component.name?.trim() ?: "Scanned Locations"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (style.radius > 0) style.radius.dp else 14.dp))
            .background(Color.White)
            .border(
                2.dp,
                SectionGreenDeep,
                RoundedCornerShape(if (style.radius > 0) style.radius.dp else 14.dp),
            )
            .padding(if (style.padding > 0) style.padding.dp else 15.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.44.sp,
            color = SectionMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )

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
                        .background(SectionGreenDeep),
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
                    text = if (invoice.isNotEmpty()) "Authorised · $invoice" else "Authorised zone",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SectionInk,
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
            leaves.forEachIndexed { index, leaf ->
                val last = index == leaves.lastIndex
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
                                    .background(Color(0x40163E64)),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 6.5.dp, top = 1.dp)
                                .size(17.dp)
                                .clip(CircleShape)
                                .background(if (leaf.flagged) Color(0xFFF87171).copy(alpha = 0.25f) else Green.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(if (leaf.flagged) Color(0xFFF87171) else Green),
                            )
                        }
                    }
                    Text(
                        text = if (leaf.flagged) "⚠ ${leaf.label}" else leaf.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (leaf.flagged) SectionRed else SectionInk,
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