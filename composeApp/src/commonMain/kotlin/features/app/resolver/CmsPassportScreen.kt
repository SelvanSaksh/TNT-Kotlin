package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonObject
import resolver.ResolverScreenState
import resolver.cms.ResolverCmsComponent
import resolver.cms.ResolverCmsPage
import resolver.cms.ResolverCmsTheme
import resolver.cms.cmsCurrency
import resolver.cms.cmsDisplayString
import resolver.cms.cmsDouble
import resolver.cms.cmsLinkURL
import resolver.cms.cmsMakeURL
import resolver.cms.cmsProductDisplayName
import resolver.cms.cmsResolve
import resolver.cms.cmsString
import resolver.cms.isAuthenticQuality
import resolver.cms.isBanner
import resolver.cms.isFooter
import resolver.cms.isImage
import resolver.cms.isPrice
import resolver.cms.isTitle
import resolver.cms.shouldShowComponent
import utils.openUrl

private val PassportSurface = Color(0xFFF5F6F8)
private val DEFAULT_ACCENT = Color(0xFF2E518F)

/**
 * Brand-authored product passport. Widget order comes straight from the CMS,
 * except that the first image, title and price are lifted into the header card.
 */
@Composable
fun CmsPassportScreen(
    page: ResolverCmsPage,
    state: ResolverScreenState,
    modifier: Modifier = Modifier,
) {
    val content = page.content ?: return
    val theme = content.theme
    val root = state.cmsRoot
    val accent = parseCmsColor(theme?.primaryColor) ?: DEFAULT_ACCENT
    val isGenuine = state.isReal == true
    val context = CmsRenderContext(root = root, theme = theme, accent = accent, isGenuine = isGenuine)

    val visible = content.components.filter { shouldShowComponent(it, isGenuine) }
    val imageItems = visible.filter { it.widgetType.isImage }
    val productCard = imageItems.firstOrNull()
    val titleItems = visible.filter { it.widgetType.isTitle }
        .ifEmpty { visible.filter { it.type == "text" }.take(1) }
    val priceItems = visible.filter { it.widgetType.isPrice }
    val footerItems = visible.filter { it.widgetType.isFooter }

    val headerConsumed = buildSet {
        productCard?.let { add(it.id) }
        titleItems.forEach { add(it.id) }
        priceItems.forEach { add(it.id) }
    }
    val cmsBanner = visible.firstOrNull { it.widgetType.isBanner }
    val bodyItems = visible.filter {
        it.id !in headerConsumed && !it.widgetType.isFooter && !it.widgetType.isBanner
    }

    val brandLabel = cmsDisplayString(cmsResolve("product.brandName", root))
        .ifBlank { state.brandName.orEmpty() }
        .ifBlank { page.name.orEmpty() }
        .ifBlank { "Brand" }

    val headerTitle = titleItems.firstOrNull()?.let { item ->
        cmsDisplayString(item.prop("title", root, listOf("productName", "product_name", "name", "text")))
            .ifBlank { cmsDisplayString(item.props["title"]) }
            .ifBlank { cmsDisplayString(item.props["text"]) }
    }?.takeIf { it.isNotBlank() }
        ?: productCard?.let { card ->
            cmsDisplayString(card.prop("title", root, listOf("productName", "product_name", "name", "text")))
                .ifBlank { cmsDisplayString(card.props["title"]) }
        }?.takeIf { it.isNotBlank() }
        ?: cmsProductDisplayName(root)
            .ifBlank { cmsDisplayString(cmsResolve("product.productName", root)) }
            .ifBlank { page.name.orEmpty() }

    val headerBrand = productCard?.let { card ->
        cmsDisplayString(card.prop("brand", root, listOf("brandName")))
            .ifBlank { cmsDisplayString(card.props["brand"]) }
    }?.takeIf { it.isNotBlank() }
        ?: cmsDisplayString(cmsResolve("product.brandName", root)).ifBlank { brandLabel }

    val manufacturer = cmsDisplayString(cmsResolve("product.companyName", root))
        .ifBlank { headerBrand }
        .ifBlank { brandLabel }
        .ifBlank { "the manufacturer" }
    val cmsBannerTitle = cmsBanner?.let {
        cmsDisplayString(it.prop("title", root)).ifBlank { cmsDisplayString(it.props["title"]) }
    }?.takeIf { it.isNotBlank() }
    val cmsBannerSubtitle = cmsBanner?.let {
        cmsDisplayString(it.prop("subtitle", root)).ifBlank { cmsDisplayString(it.props["subtitle"]) }
    }?.takeIf { it.isNotBlank() }
    val cmsBannerBrand = cmsBanner?.let {
        cmsDisplayString(it.prop("brand", root)).ifBlank { cmsDisplayString(it.props["brand"]) }
    }?.takeIf { it.isNotBlank() }
    val cmsBannerBadge = cmsBanner?.let {
        cmsDisplayString(it.prop("badge", root)).ifBlank { cmsDisplayString(it.props["badge"]) }
    }?.takeIf { it.isNotBlank() }

    val headerPriceSource = productCard ?: priceItems.firstOrNull()
    val headerCurrency = productCard?.let {
        cmsString(it.prop("currency", root)) ?: cmsString(it.props["currency"])
    } ?: priceItems.firstOrNull()?.let {
        cmsString(it.prop("currency", root)) ?: cmsString(it.props["currency"])
    }

    Box(modifier = modifier.fillMaxSize()) {
        ThemeBackground(theme = theme, accent = accent)

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    state.isAuthLoading -> PharmaAuthBanner(
                        verifying = true,
                        genuine = true,
                        hasVerdict = false,
                        manufacturer = manufacturer,
                        cmsTitle = cmsBannerTitle,
                        cmsSubtitle = cmsBannerSubtitle,
                        brandLine = cmsBannerBrand,
                        badge = cmsBannerBadge,
                    )
                    !state.authQuality.isNullOrBlank() -> AuthenticityBarcodeAccordion(
                        quality = state.authQuality.orEmpty(),
                        manufacturer = manufacturer,
                        cmsTitle = cmsBannerTitle,
                        cmsSubtitle = cmsBannerSubtitle,
                        brandLine = cmsBannerBrand,
                        badge = cmsBannerBadge,
                        gtin = state.gtin
                            ?: cmsDisplayString(cmsResolve("product.identifier", root))
                                .takeIf { it.isNotBlank() },
                        batchNumber = state.scanBatch,
                        serialNumber = state.scanSerial,
                        mfgDate = state.scanMfg,
                        expiryDate = state.scanExpiry,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    // Clears the accordion chevron, which hangs below the banner.
                    .padding(top = 18.dp, bottom = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isGenuine) Modifier else Modifier.blurredAndInert()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProductHeaderRow(
                            headerImage = productCard,
                            headerTitle = headerTitle,
                            headerBrand = headerBrand,
                            headerPriceSource = headerPriceSource,
                            headerCurrency = headerCurrency,
                            productLink = cmsLinkURL(null, root, productCard),
                            context = context,
                        )

                        if (bodyItems.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.Black.copy(alpha = 0.1f)),
                            )
                        }

                        bodyItems.forEach { component ->
                            CmsComponentView(component = component, context = context)
                        }
                    }

                    footerItems.forEach { component ->
                        CmsComponentView(
                            component = component,
                            context = context,
                            placement = "footer",
                        )
                    }
                }

                // Kept outside the blur so a failed product can still be reported.
                if (!isGenuine) {
                    SafetyCheckCard()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF7F0E0))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Why this matters",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2430),
                        )
                        Text(
                            text = "Counterfeit products may contain unsafe ingredients, " +
                                "incorrect dosage, or no active ingredients at all. Always buy " +
                                "from authorised sellers.",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Color(0xFF666E80),
                        )
                    }
                }
            }
        }
    }
}

/** Blurs and swallows taps on content that failed authentication. */
@Composable
private fun Modifier.blurredAndInert(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .blur(6.dp)
        .clickable(interactionSource = interactionSource, indication = null) {}
}

@Composable
private fun ThemeBackground(theme: ResolverCmsTheme?, accent: Color) {
    val background = theme?.background
    val value = background?.value?.trim().orEmpty()
    val asColor = parseCmsColor(value)

    when {
        background?.type == "image" && value.isNotEmpty() && asColor == null -> AsyncImage(
            model = cmsMakeURL(value),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        asColor != null -> Box(modifier = Modifier.fillMaxSize().background(asColor))

        else -> Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.18f),
                        Color(0xFFF2F2F7),
                        accent.copy(alpha = 0.08f),
                    ),
                ),
            ),
        )
    }
}

@Composable
private fun ProductHeaderRow(
    headerImage: ResolverCmsComponent?,
    headerTitle: String,
    headerBrand: String,
    headerPriceSource: ResolverCmsComponent?,
    headerCurrency: String?,
    productLink: String?,
    context: CmsRenderContext,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            if (headerImage != null) {
                CmsImage(
                    component = headerImage,
                    context = context,
                    thumb = true,
                    linkUrl = productLink,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF3F4F6))
                        .then(
                            if (productLink != null) {
                                Modifier.clickable { openUrl(productLink) }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "📷", fontSize = 18.sp)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = if (context.isGenuine) {
                            Color.Black.copy(alpha = 0.08f)
                        } else {
                            AUTH_RED.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (context.isGenuine) Icons.Default.Link else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (context.isGenuine) Color(0xFF263040) else AUTH_RED,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = if (context.isGenuine) "Official" else "Suspect",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (context.isGenuine) Color(0xFF263040) else AUTH_RED,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (productLink != null) {
                        Modifier.clickable { openUrl(productLink) }
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (headerTitle.isNotBlank()) {
                Text(
                    text = headerTitle,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkStrong,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (headerBrand.isNotBlank()) {
                Text(
                    text = headerBrand,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HeaderPrice(
                component = headerPriceSource,
                root = context.root,
                accent = context.accent,
                currencyOverride = headerCurrency,
            )
        }
    }
}

@Composable
private fun HeaderPrice(
    component: ResolverCmsComponent?,
    root: JsonObject,
    accent: Color,
    currencyOverride: String?,
) {
    val price = component?.propDouble("price", root, listOf("mrp"))
        ?: cmsDouble(cmsResolve("product.mrp", root))
        ?: 0.0
    if (price == 0.0) return

    val rawCurrency = currencyOverride
        ?: component?.let { cmsString(it.prop("currency", root)) ?: cmsString(it.props["currency"]) }
        ?: "₹"

    Text(
        text = cmsCurrency(price, normalizeCurrencySymbol(rawCurrency)),
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        color = accent,
    )
}

@Composable
private fun AuthenticityBarcodeAccordion(
    quality: String,
    manufacturer: String,
    cmsTitle: String?,
    cmsSubtitle: String?,
    brandLine: String?,
    badge: String?,
    gtin: String?,
    batchNumber: String?,
    serialNumber: String?,
    mfgDate: String?,
    expiryDate: String?,
) {
    var open by remember { mutableStateOf(false) }
    val isReal = isAuthenticQuality(quality)
    val accent = if (isReal) AUTH_GREEN else Color(0xFF9B4A3E)

    val fields = buildList {
        gtin?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { add(BarcodeField("GTIN", it, Icons.Default.LocalOffer, primary = true)) }
        batchNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { add(BarcodeField("Batch", it, Icons.Default.Tag)) }
        serialNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { add(BarcodeField("Serial", it, Icons.Default.Tag)) }
        mfgDate?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { add(BarcodeField("Mfg", it, Icons.Default.CalendarToday)) }
        expiryDate?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { add(BarcodeField("Expiry", it, Icons.Default.CalendarToday)) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PharmaAuthBanner(
                verifying = false,
                genuine = isReal,
                hasVerdict = true,
                manufacturer = manufacturer,
                cmsTitle = cmsTitle,
                cmsSubtitle = cmsSubtitle,
                brandLine = brandLine,
                badge = badge,
            )

            if (fields.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 12.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
                        .clickable { open = !open },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (open) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = if (open) {
                            "Hide barcode details"
                        } else {
                            "Show barcode details"
                        },
                        tint = accent,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        if (fields.isNotEmpty() && open) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                fields.forEach { field -> BarcodeChip(field) }
            }
        }
    }
}

private data class BarcodeField(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val primary: Boolean = false,
)

@Composable
private fun BarcodeChip(field: BarcodeField) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (field.primary) 1.5.dp else 1.dp,
                color = Color(0xFFCBD5E1).copy(alpha = 0.8f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = field.icon,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(if (field.primary) 12.dp else 10.dp),
        )
        Text(
            text = field.label.uppercase(),
            fontSize = if (field.primary) 10.sp else 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = Color(0xFF94A3B8),
        )
        Text(
            text = field.value,
            fontSize = if (field.primary) 11.sp else 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (field.primary) Color(0xFF0F172A) else Color(0xFF334155),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SafetyCheckCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFEF2F2))
            .border(1.dp, AUTH_RED.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFECACA))
                .border(1.dp, AUTH_RED.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AUTH_RED,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "SAFETY CHECK",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Color(0xFFB91C1C).copy(alpha = 0.8f),
            )
            Text(
                text = "Something look off with this product?",
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3F2A2A),
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(AUTH_RED)
                .clickable { openUrl("https://ratifye.ai/report") }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Report",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}
