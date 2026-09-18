package features.app.resolver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonObject
import resolver.cms.CmsWidgetType
import resolver.cms.ResolvedCmsStyle
import resolver.cms.ResolverCmsComponent
import resolver.cms.ResolverCmsTheme
import resolver.cms.cmsCurrency
import resolver.cms.cmsDisplayString
import resolver.cms.cmsDouble
import resolver.cms.cmsLinkURL
import resolver.cms.cmsMakeURL
import resolver.cms.cmsProductDisplayName
import resolver.cms.cmsResolve
import resolver.cms.cmsSafeLink
import resolver.cms.cmsString
import resolver.cms.cmsStyle
import resolver.cms.cmsUnwrappedString
import resolver.cms.isBanner
import resolver.cms.isBrandLogo
import resolver.cms.isButton
import resolver.cms.isCarousel
import resolver.cms.isCoupon
import resolver.cms.isDescription
import resolver.cms.isDivider
import resolver.cms.isFeatureChips
import resolver.cms.isFieldGrid
import resolver.cms.isFooter
import resolver.cms.isImage
import resolver.cms.isLink
import resolver.cms.isOffer
import resolver.cms.isPackCard
import resolver.cms.isPdfViewer
import resolver.cms.isPrice
import resolver.cms.isRating
import resolver.cms.isRelatedProducts
import resolver.cms.isSocialLinks
import resolver.cms.isTimeline
import resolver.cms.isTitle
import resolver.cms.isYouTube
import resolver.cms.cmsRealValue
import utils.openUrl

internal val AUTH_RED = Color(0xFFDB3838)
internal val AUTH_GREEN = Color(0xFF2F9E68)
internal val ImagePlaceholder = Color(0xFFF1F5F9)
internal val InkStrong = Color(0xFF0F172A)
internal val InkTitle = Color(0xFF1A1F29)
internal val InkBody = Color(0xFF595F73)
internal val InkMuted = Color(0xFF737B8C)

/** Everything a widget needs to render, bundled so the tree stays readable. */
data class CmsRenderContext(
    val root: JsonObject,
    val theme: ResolverCmsTheme?,
    val accent: Color,
    val isGenuine: Boolean,
    val isAuthLoading: Boolean = false,
    val authQuality: String? = null,
    val scan: CmsScanContext = CmsScanContext(),
) {
    /** False when the auth call produced no verdict (error / empty response). */
    val hasAuthVerdict: Boolean
        get() = !authQuality.isNullOrBlank()
}

/** `#RGB`, `#RRGGBB` and CSS `#RRGGBBAA` into a Compose colour. */
fun parseCmsColor(raw: String?): Color? {
    val value = raw?.trim()?.removePrefix("#").orEmpty()
    if (value.isEmpty()) return null
    if (!value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val hex = when (value.length) {
        3 -> value.map { "$it$it" }.joinToString("")
        6, 8 -> value
        else -> return null
    }
    return when (hex.length) {
        6 -> Color(("ff$hex").lowercase().toLong(16))
        else -> Color((hex.substring(6, 8) + hex.substring(0, 6)).lowercase().toLong(16))
    }
}

private fun openCmsUrl(raw: String?) {
    cmsSafeLink(raw)?.let { openUrl(it) }
}

/** Padding, background and corner radius shared by every widget wrapper. */
private fun Modifier.cmsChrome(style: ResolvedCmsStyle): Modifier {
    var modifier = this
    if (style.margin > 0) {
        modifier = modifier.padding(vertical = (style.margin / 2).dp)
    }
    val background = parseCmsColor(style.background)
    if (background != null || style.radius > 0) {
        modifier = modifier.background(
            color = background ?: Color.Transparent,
            shape = RoundedCornerShape(style.radius.dp),
        )
    }
    if (style.padding > 0) {
        modifier = modifier.padding(style.padding.dp)
    }
    return modifier
}

private fun ResolvedCmsStyle.textAlignment(): TextAlign = when (textAlign) {
    "center" -> TextAlign.Center
    "right" -> TextAlign.End
    else -> TextAlign.Start
}

@Composable
fun CmsComponentView(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
    placement: String? = null,
) {
    val type = component.widgetType
    when {
        type.isImage -> CmsImage(
            component = component,
            context = context,
            hero = placement == "hero",
            modifier = modifier,
        )

        type.isBrandLogo -> CmsBrandLogo(component, context, modifier)
        type.isBanner -> CmsBanner(component, context, modifier)
        type.isPackCard -> CmsPackCard(component, context, modifier)
        type.isFieldGrid -> CmsFieldGrid(component, context, modifier)
        type.isTitle -> {
            if (placement == "section") {
                CmsSectionTitle(
                    text = cmsRealValue(component.prop("title", context.root))
                        ?: component.name?.trim().orEmpty(),
                    modifier = modifier,
                )
            } else {
                CmsTitle(component, context, modifier)
            }
        }
        type.isTimeline -> {
            if (placement == "section") {
                val label = listOf(
                    component.name.orEmpty(),
                    cmsDisplayString(component.props["title"]).orEmpty(),
                ).joinToString(" ")
                val scanLike = Regex("scan|trail|trace|location", RegexOption.IGNORE_CASE) in label
                val pathLike = Regex("supply|distribut|path|chain|journey", RegexOption.IGNORE_CASE) in label
                when {
                    scanLike -> CmsScanTreeCard(component, context, modifier)
                    pathLike -> CmsDistributionPathCard(component, context, modifier)
                    else -> CmsTimeline(component, context, modifier)
                }
            } else {
                CmsTimeline(component, context, modifier)
            }
        }
        type.isFeatureChips -> {
            if (placement == "section" && !cmsChipsHaveLinks(component, context.root)) {
                CmsStatChipsRow(component, context, modifier)
            } else {
                CmsFeatureChips(component, context, modifier)
            }
        }
        type.isPrice -> CmsPrice(component, context, modifier)
        type.isDescription -> CmsDescription(component, context, modifier)
        type.isLink -> CmsLink(component, context, modifier)
        type.isButton -> CmsButtons(component, context, modifier)
        type.isYouTube -> CmsVideoCard(component, context, modifier)
        type.isRelatedProducts -> CmsRelatedProducts(component, context, modifier)
        type.isCarousel -> CmsCarousel(component, context, modifier)
        type.isTimeline -> CmsTimeline(component, context, modifier)
        type.isFeatureChips -> CmsFeatureChips(component, context, modifier)
        type.isSocialLinks -> CmsSocialLinks(component, context, modifier)
        type.isOffer -> CmsOffer(component, context, modifier)
        type.isCoupon -> CmsCoupon(component, context, modifier)
        type.isRating -> CmsRating(component, context, modifier)
        type.isPdfViewer -> CmsPdfViewer(component, context, modifier)
        type.isFooter -> CmsFooter(component, context, modifier)

        type.isDivider -> Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(1.dp)
                .background(Color.Black.copy(alpha = 0.05f)),
        )

        else -> Box(modifier = modifier.fillMaxWidth().cmsChrome(cmsStyle(component.styles)))
    }
}

@Composable
fun CmsImage(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    thumb: Boolean = false,
    linkUrl: String? = null,
) {
    val style = cmsStyle(component.styles)
    val bound = cmsUnwrappedString(component.prop("image", context.root, listOf("imageUrl", "src")))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: cmsUnwrappedString(cmsResolve("product.images[0]", context.root))
    val url = cmsMakeURL(bound)
    val href = linkUrl ?: cmsLinkURL(null, context.root, component)

    val height = when {
        thumb -> 96.0
        style.height > 0 -> style.height
        hero -> 300.0
        else -> 220.0
    }
    val radius = when {
        thumb -> if (style.radius > 0) style.radius else 12.0
        hero -> 0.0
        else -> style.radius
    }

    var box = modifier
    box = if (thumb) box.size(96.dp) else box.fillMaxWidth().height(height.dp)
    box = box
        .clip(RoundedCornerShape(radius.dp))
        .background(ImagePlaceholder)
    if (href != null) box = box.clickable { openUrl(href) }

    Box(modifier = box, contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Product",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "📷", fontSize = if (thumb) 18.sp else 30.sp)
                if (!thumb) {
                    Text(
                        text = "Product image",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun CmsBrandLogo(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val raw = cmsString(component.prop("image", context.root, listOf("logo", "logoUrl", "src")))
        ?: cmsString(component.props["image"])
    val url = cmsMakeURL(raw) ?: return
    // A logo widget pointing at a page rather than an asset renders nothing.
    val looksLikeImage = IMAGE_EXTENSION.containsMatchIn(url) || url.contains("/logo", true)
    if (!looksLikeImage) return

    val padding = if (style.padding != 0.0) maxOf(0.0, style.padding) else 12.0
    val alignment = when (style.textAlign) {
        "left" -> Alignment.CenterStart
        "right" -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .padding(padding.dp),
        contentAlignment = alignment,
    ) {
        AsyncImage(
            model = url,
            contentDescription = cmsDisplayString(cmsResolve("product.brandName", context.root))
                .ifBlank { component.name ?: "Brand" },
            contentScale = ContentScale.Fit,
            modifier = Modifier.sizeIn(maxWidth = 180.dp, maxHeight = 48.dp),
        )
    }
}

@Composable
fun CmsTitle(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles, "#1a1f29")
    val title = cmsDisplayString(
        component.prop("title", context.root, listOf("productName", "product_name", "name", "text")),
    ).ifBlank { cmsDisplayString(component.props["title"]) }
        .ifBlank { cmsDisplayString(component.props["text"]) }
        .ifBlank { cmsProductDisplayName(context.root) }
    if (title.isBlank()) return

    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        fontSize = (if (style.fontSize > 0) minOf(style.fontSize, 18.0) else 17.0).sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight(if (style.fontWeight == 400) 700 else style.fontWeight),
        color = parseCmsColor(style.color) ?: InkTitle,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun CmsPrice(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val brand = cmsDisplayString(component.prop("brand", context.root, listOf("brandName")))
        .ifBlank { cmsDisplayString(cmsResolve("product.brandName", context.root)) }
    val price = cmsDouble(component.prop("price", context.root, listOf("mrp")))
        ?: cmsDouble(cmsResolve("product.mrp", context.root))
        ?: 0.0
    val currency = cmsString(component.prop("currency", context.root))
        ?: cmsString(component.props["currency"])
        ?: "₹"
    if (price == 0.0 && brand.isBlank()) return

    val background = parseCmsColor(style.background) ?: Color(0xFF5C4033)
    val textColor = parseCmsColor(style.color) ?: Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .background(background, RoundedCornerShape(style.radius.dp))
            .then(
                if (style.padding > 0) {
                    Modifier.padding(style.padding.dp)
                } else {
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = cmsCurrency(price, normalizeCurrencySymbol(currency)),
            fontSize = (if (style.fontSize > 0) style.fontSize else 22.0).sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (brand.isNotBlank()) {
            Text(
                text = brand,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CmsDescription(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "descChevron")
    val style = cmsStyle(component.styles, "#595f73")
    // Widget copy wins over `product.*` guesses; only an explicit binding overrides it.
    val text = component.bindings["text"]
        ?.let { cmsDisplayString(component.prop("text", context.root)) }
        .orEmpty()
        .ifBlank { cmsDisplayString(component.props["text"]) }
        .ifBlank { cmsDisplayString(cmsResolve("product.description", context.root)) }
    if (text.isBlank()) return

    val sectionTitle = when (component.widgetType) {
        CmsWidgetType.PRODUCT_DESCRIPTION, CmsWidgetType.DESCRIPTION -> "Product Description"
        else -> component.name?.trim()?.takeIf { it.isNotEmpty() }
    }
    val cardRadius = (if (style.radius > 0) style.radius else 16.0).dp
    val bodySize = (if (style.fontSize > 0) style.fontSize else 13.5).sp
    val bodyColor = parseCmsColor(style.color) ?: Color(0xFF475569)

    Column(
        modifier = modifier
            .padding(vertical = if (style.margin > 0) (style.margin / 2).dp else 0.dp)
            .clip(RoundedCornerShape(cardRadius))
            .background(parseCmsColor(style.background) ?: Color.White)
            .border(1.dp, Color(0xFFE6EBF2), RoundedCornerShape(cardRadius)),
    ) {
        sectionTitle?.let { title ->
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF134E4A))),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F2438),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!expanded) {
                            Text(
                                text = if (text.length > 60) "${text.take(60)}…" else text,
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { rotationZ = rotation }
                            .clip(CircleShape)
                            .background(if (expanded) Color(0x1A0F766E) else Color(0xFFF1F5F9))
                            .border(
                                1.dp,
                                if (expanded) Color(0x400F766E) else Color(0xFFE2E8F0),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = if (expanded) Color(0xFF0F766E) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEEF2F7)),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Text(
                text = text,
                fontSize = bodySize,
                lineHeight = bodySize * 1.7f,
                fontWeight = FontWeight(style.fontWeight),
                color = bodyColor,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 16.dp,
                ),
            )
        }
    }
}

@Composable
fun CmsLink(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val label = (cmsString(component.prop("label", context.root)) ?: "Learn more").trim()
    val url = cmsString(component.prop("url", context.root))
        ?: cmsString(component.props["url"])
        ?: ""
    val color = parseCmsColor(style.color) ?: context.accent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { openCmsUrl(url) }
            .padding(vertical = 4.dp)
            .then(if (style.padding > 0) Modifier.padding(style.padding.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = (if (style.fontSize > 0) style.fontSize else 15.0).sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textDecoration = TextDecoration.Underline,
        )
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun CmsButtons(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val label = (cmsString(component.prop("label", context.root)) ?: "Buy Now").trim()
    val url = cmsString(component.prop("url", context.root))
        ?: cmsString(component.props["url"])
        ?: ""

    val background = if (context.isGenuine) {
        parseCmsColor(style.background)
            ?: parseCmsColor(context.theme?.primaryColor)
            ?: Color(0xFF14161F)
    } else {
        AUTH_RED
    }
    val corner = (if (style.radius > 0) style.radius else 14.0).dp
    val verticalPadding = maxOf(14.0, if (style.padding > 0) style.padding + 8 else 16.0).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(corner))
                .background(background)
                .clickable { openCmsUrl(url) }
                .padding(horizontal = 18.dp, vertical = verticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (context.isGenuine) {
                    label.ifBlank { "Order now" }
                } else {
                    "Report this product"
                },
                fontSize = maxOf(style.fontSize, 15.0).sp,
                fontWeight = FontWeight.Bold,
                color = parseCmsColor(style.color) ?: Color.White,
            )
        }

        if (!context.isGenuine) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corner))
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(corner))
                    .clickable { openCmsUrl(url) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.ifBlank { "Buy the genuine product" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF263040),
                )
            }
        }
    }
}

@Composable
fun CmsFooter(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles, "#000000")
    val text = cmsDisplayString(component.prop("text", context.root))
        .ifBlank { cmsDisplayString(component.props["text"]) }
    val logoRaw = cmsString(
        component.prop("logo", context.root, listOf("logoUrl", "image", "brandLogo")),
    ) ?: cmsString(component.props["logo"])
        ?: cmsString(component.props["logoUrl"])
        ?: cmsString(component.props["image"])
        ?: "/images/logo.png"

    val lower = logoRaw.trim().lowercase()
    val usesBundled = lower == "logo.png" || lower.endsWith("/logo.png") ||
        lower == "images/logo.png"
    val remoteUrl = if (usesBundled) null else cmsMakeURL(logoRaw)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .background(
                parseCmsColor(style.background) ?: Color.Transparent,
                RoundedCornerShape(style.radius.dp),
            )
            .heightIn(min = maxOf(style.height, 36.0).dp)
            .padding((if (style.padding > 0) style.padding else 8.0).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (text.isNotBlank()) {
            Text(
                text = text,
                fontSize = (if (style.fontSize > 0) minOf(style.fontSize, 13.0) else 12.0).sp,
                fontWeight = FontWeight(if (style.fontWeight == 400) 500 else style.fontWeight),
                color = parseCmsColor(style.color) ?: Color(0xFF111111),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (remoteUrl != null) {
            AsyncImage(
                model = remoteUrl,
                contentDescription = "Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(20.dp).width(80.dp),
            )
        } else {
            Text(
                text = "RATIFYE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF163C66),
            )
        }
    }
}

internal fun normalizeCurrencySymbol(raw: String): String =
    if (raw.uppercase() == "INR" || raw.uppercase() == "RS") "₹" else raw

private val IMAGE_EXTENSION = Regex("""\.(png|jpe?g|gif|webp|avif|svg)($|\?|#)""", RegexOption.IGNORE_CASE)
