package features.app.resolver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import components.InlineWebView
import components.inlineEmbedHtml
import components.resolverComponents.socialBrandFor
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import resolver.cms.CMS_ASSET_ORIGIN
import resolver.cms.CmsVideoKind
import resolver.cms.ResolverCmsComponent
import resolver.cms.cmsArray
import resolver.cms.cmsBool
import resolver.cms.cmsClassifyVideoURL
import resolver.cms.cmsCurrency
import resolver.cms.cmsDict
import resolver.cms.cmsDisplayString
import resolver.cms.cmsDouble
import resolver.cms.cmsMakeURL
import resolver.cms.cmsSafeLink
import resolver.cms.cmsString
import resolver.cms.cmsStyle
import utils.openUrl
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val Slate50 = Color(0xFFF8FAFC)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF64748B)
private val Slate700 = Color(0xFF334155)

/** Section heading shared by the card-style widgets. */
@Composable
private fun WidgetTitle(text: String) {
    if (text.isBlank()) return
    Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkStrong)
}

private fun ResolverCmsComponent.listProp(key: String, root: JsonObject, alt: String): JsonElement? =
    prop(key, root) ?: prop(alt, root)

private fun ResolverCmsComponent.text(key: String, root: JsonObject): String? =
    cmsString(prop(key, root))?.trim()?.takeIf { it.isNotEmpty() }

private fun ResolverCmsComponent.styleString(key: String): String? =
    cmsString(styles[key])?.trim()?.takeIf { it.isNotEmpty() }

// MARK: - Timeline

@Composable
fun CmsTimeline(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles, "#334155")
    val title = component.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: component.text("title", context.root)
        ?: "Timeline"

    val raw = component.listProp("steps", context.root, "items")
    val steps = mutableListOf<Pair<String, String?>>()
    when {
        raw is JsonArray -> raw.forEach { item ->
            val dict = cmsDict(item)
            if (dict != null) {
                val label = cmsString(dict["label"])
                    ?: cmsString(dict["title"])
                    ?: cmsString(dict["name"])
                    ?: cmsDisplayString(dict["text"])
                val detail = cmsString(dict["detail"])
                    ?: cmsString(dict["description"])
                    ?: cmsString(dict["sub"])
                if (!label.isNullOrBlank()) steps.add(label to detail?.takeIf { it.isNotBlank() })
            } else {
                cmsString(item)?.trim()?.takeIf { it.isNotEmpty() }?.let { steps.add(it to null) }
            }
        }

        else -> cmsString(raw)?.split('\n')?.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val colon = trimmed.indexOf(':')
            if (colon in 1..39) {
                steps.add(trimmed.take(colon).trim() to trimmed.substring(colon + 1).trim())
            } else {
                steps.add(trimmed to null)
            }
        }
    }
    if (steps.isEmpty()) return

    val labelColor = parseCmsColor(style.color) ?: Slate700

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .clip(RoundedCornerShape((if (style.radius > 0) style.radius else 12.0).dp))
            .background(parseCmsColor(style.background) ?: Slate50)
            .padding((if (style.padding > 0) style.padding else 16.0).dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WidgetTitle(title)
        steps.forEachIndexed { index, (label, detail) ->
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Column(
                    modifier = Modifier.width(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(labelColor),
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .width(1.dp)
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.1f)),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkStrong,
                    )
                    detail?.let {
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Slate500,
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Feature chips

@Composable
fun CmsFeatureChips(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val title = component.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: component.text("title", context.root)
    val subtitle = component.text("subtitle", context.root)

    val chips = mutableListOf<Pair<String, String?>>()
    (component.listProp("chips", context.root, "items") as? JsonArray)?.forEach { item ->
        val dict = cmsDict(item)
        if (dict != null) {
            val label = (
                cmsString(dict["label"])
                    ?: cmsString(dict["title"])
                    ?: cmsString(dict["name"])
                    ?: cmsDisplayString(dict["text"])
                )?.trim()
            if (label.isNullOrEmpty()) return@forEach
            chips.add(label to cmsMakeURL(cmsString(dict["url"]) ?: cmsString(dict["href"])))
        } else {
            cmsString(item)?.trim()?.takeIf { it.isNotEmpty() }?.let { chips.add(it to null) }
        }
    }

    val videoUrl = cmsMakeURL(
        cmsString(component.prop("videoUrl", context.root, listOf("url", "video"))),
    )
    if (chips.isEmpty() && videoUrl == null && title.isNullOrBlank() && subtitle.isNullOrBlank()) {
        return
    }

    val radius = (if (style.radius > 0) style.radius else 12.0).dp
    val chipBackground = parseCmsColor(style.background) ?: Slate100
    val chipBorder = parseCmsColor(component.styleString("borderColor")) ?: Slate200
    val chipTextColor = parseCmsColor(style.color) ?: InkStrong

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .padding((if (style.padding > 0) style.padding else 8.0).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        title?.let { WidgetTitle(it) }
        subtitle?.let {
            Text(
                text = it,
                fontSize = (if (style.fontSize > 0) min(style.fontSize, 22.0) else 15.0).sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = chipTextColor,
            )
        }

        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(
                    (if (style.gap > 0) style.gap else 8.0).dp,
                ),
            ) {
                chips.forEach { (label, url) ->
                    Text(
                        text = label,
                        fontSize = (if (style.fontSize > 0) min(style.fontSize, 14.0) else 12.0).sp,
                        fontWeight = FontWeight.Medium,
                        color = chipTextColor,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius))
                            .background(chipBackground)
                            .border(1.dp, chipBorder, RoundedCornerShape(radius))
                            .then(
                                if (url != null) {
                                    Modifier.clickable { openUrl(url) }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        videoUrl?.let {
            CmsInlineVideo(url = it, accent = context.accent, radius = radius)
        }
    }
}

// MARK: - Social links

@Composable
fun CmsSocialLinks(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val links = mutableListOf<SocialLink>()

    (component.listProp("links", context.root, "items") as? JsonArray)?.forEach { item ->
        val dict = cmsDict(item) ?: return@forEach
        val url = cmsMakeURL(cmsString(dict["url"]) ?: cmsString(dict["href"])) ?: return@forEach
        val platform = cmsString(dict["platform"])
        val label = (
            cmsString(dict["label"])
                ?: platform
                ?: cmsString(dict["name"])
                ?: "Link"
            ).trim().ifEmpty { "Link" }
        val brand = socialBrandFor(platform ?: label, url)
        links.add(
            SocialLink(
                url = url,
                label = label,
                color = parseCmsColor(cmsString(dict["color"]))
                    ?: brand?.color
                    ?: Color(0xFF4338CA),
                icon = brand?.icon,
                iconCdn = cmsMakeURL(cmsString(dict["iconCdn"])),
            ),
        )
    }

    if (links.isEmpty()) {
        cmsString(component.prop("urls", context.root))
            ?.split('\n', ',')
            ?.forEach { part ->
                cmsMakeURL(part.trim())?.let { url ->
                    val brand = socialBrandFor(null, url)
                    links.add(
                        SocialLink(
                            url = url,
                            label = "Link",
                            color = brand?.color ?: Color(0xFF4338CA),
                            icon = brand?.icon,
                            iconCdn = null,
                        ),
                    )
                }
            }
    }
    if (links.isEmpty()) return

    val iconSize = cmsString(component.props["iconSize"])?.lowercase()
    val iconBox = when (iconSize) {
        "sm" -> 32.dp
        "lg" -> 44.dp
        else -> 40.dp
    }
    val glyph = when (iconSize) {
        "sm" -> 16.dp
        "lg" -> 22.dp
        else -> 20.dp
    }
    val alignment = when (style.textAlign) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .padding(max(0.0, style.padding).dp),
        horizontalArrangement = Arrangement.spacedBy(
            (if (style.gap > 0) style.gap else 8.0).dp,
            alignment,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        links.forEach { link ->
            Box(
                modifier = Modifier
                    .size(iconBox)
                    .clip(CircleShape)
                    .background(link.color)
                    .clickable { openUrl(link.url) },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    // The bundled marks come first: the CMS serves SVG icons,
                    // which the image loader cannot decode.
                    link.icon != null -> Icon(
                        imageVector = link.icon,
                        contentDescription = link.label,
                        tint = Color.White,
                        modifier = Modifier.size(glyph),
                    )

                    link.iconCdn != null -> AsyncImage(
                        model = link.iconCdn,
                        contentDescription = link.label,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(glyph),
                    )

                    else -> Text(
                        text = link.label.take(1).uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private data class SocialLink(
    val url: String,
    val label: String,
    val color: Color,
    val icon: ImageVector?,
    val iconCdn: String?,
)

// MARK: - Related products

@Composable
fun CmsRelatedProducts(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val title = component.text("title", context.root) ?: "Related products"
    val horizontal = cmsString(component.prop("layout", context.root)) != "vertical"
    val gap = (cmsDouble(component.styles["gap"]) ?: 8.0).dp
    val cardRadius = (if (style.radius > 0) max(8.0, style.radius - 2) else 10.0).dp

    val products = (cmsArray(component.prop("products", context.root)) ?: JsonArray(emptyList()))
        .mapNotNull { cmsDict(it) }
    if (products.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .clip(RoundedCornerShape((if (style.radius > 0) style.radius else 12.0).dp))
            .background(parseCmsColor(style.background) ?: Color.White)
            .padding((if (style.padding > 0) style.padding else 12.0).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WidgetTitle(title)

        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                products.forEach { product ->
                    RelatedProductCard(product, context, cardRadius, horizontal = true)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                products.forEach { product ->
                    RelatedProductCard(product, context, cardRadius, horizontal = false)
                }
            }
        }
    }
}

@Composable
private fun RelatedProductCard(
    product: JsonObject,
    context: CmsRenderContext,
    radius: androidx.compose.ui.unit.Dp,
    horizontal: Boolean,
) {
    val name = cmsString(product["productName"]) ?: "Product"
    val brand = cmsString(product["brandName"])
    val description = cmsString(product["description"])
    val image = cmsMakeURL(cmsString(product["image"]))
    val mrp = cmsDouble(product["mrp"])
    val link = cmsSafeLink(
        cmsString(product["linkUrl"]) ?: cmsString(product["url"]) ?: cmsString(product["href"]),
    )

    val container = Modifier
        .clip(RoundedCornerShape(radius))
        .background(Slate50)
        .then(if (link != null) Modifier.clickable { openUrl(link) } else Modifier)

    @Composable
    fun Thumbnail(modifier: Modifier) {
        Box(modifier = modifier.background(Color(0xFFF3F4F6)), contentAlignment = Alignment.Center) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(text = "No image", fontSize = 11.sp, color = Slate400)
            }
        }
    }

    @Composable
    fun Details() {
        Text(
            text = name,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = InkStrong,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        brand?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = Slate500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        mrp?.let {
            Text(
                text = cmsCurrency(it, "₹"),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = context.accent,
            )
        }
        if (!horizontal && !description.isNullOrBlank()) {
            Text(
                text = description,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Slate500,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (horizontal) {
        Column(modifier = container.width(148.dp)) {
            Thumbnail(Modifier.fillMaxWidth().height(120.dp))
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) { Details() }
        }
    } else {
        Row(modifier = container.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(Modifier.size(88.dp))
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) { Details() }
        }
    }
}

// MARK: - Carousel

@Composable
fun CmsCarousel(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val title = component.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: component.text("title", context.root)

    val slides = mutableListOf<Triple<Boolean, String, String>>()
    (component.listProp("images", context.root, "items") as? JsonArray)?.forEach { item ->
        val dict = cmsDict(item)
        val raw = if (dict != null) {
            cmsString(dict["image"]) ?: cmsString(dict["url"]) ?: cmsString(dict["src"])
                ?: cmsString(dict["href"])
        } else {
            cmsString(item)
        }
        val url = cmsMakeURL(raw) ?: return@forEach
        val lower = url.lowercase()
        val isImage = IMAGE_SLIDE.containsMatchIn(lower) ||
            lower.contains("cdn/shop/files/") ||
            SLIDE_IMAGE_PATH.containsMatchIn(lower)
        val label = dict?.let {
            cmsString(it["label"]) ?: cmsString(it["title"]) ?: cmsString(it["name"])
        } ?: "View"
        slides.add(Triple(isImage, url, label))
    }
    if (slides.isEmpty()) return

    val radius = (if (style.radius > 0) style.radius else 12.0)
    val slideRadius = max(8.0, radius - 2).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(parseCmsColor(style.background) ?: Color.Transparent)
            .padding((if (style.padding > 0) style.padding else 8.0).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        title?.let { WidgetTitle(it) }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy((if (style.gap > 0) style.gap else 10.0).dp),
        ) {
            slides.forEach { (isImage, url, label) ->
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(RoundedCornerShape(slideRadius))
                        .background(if (isImage) Color.White.copy(alpha = 0.7f) else Color.White)
                        .then(
                            if (isImage) {
                                Modifier
                            } else {
                                Modifier.border(
                                    1.dp,
                                    Color.Black.copy(alpha = 0.05f),
                                    RoundedCornerShape(slideRadius),
                                )
                            },
                        )
                        .clickable { openUrl(url) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isImage) {
                        AsyncImage(
                            model = url,
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = Slate500,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = InkStrong,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Video and PDF

@Composable
fun CmsVideoCard(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val raw = cmsString(
        component.prop("url", context.root, listOf("videoUrl", "youtubeUrl")),
    )?.trim().orEmpty()
    if (raw.isEmpty()) return

    CmsInlineVideo(
        url = raw,
        accent = context.accent,
        radius = (if (style.radius > 0) min(style.radius, 12.0) else 10.0).dp,
        modifier = modifier.padding(vertical = (style.margin / 2).dp),
    )
}

/**
 * Plays YouTube and Instagram media in place through their embed players.
 * Anything else keeps the tappable card that hands off to an external app.
 */
@Composable
fun CmsInlineVideo(
    url: String,
    accent: Color,
    radius: Dp,
    modifier: Modifier = Modifier,
) {
    val classified = cmsClassifyVideoURL(url)
    val corner = RoundedCornerShape(radius.coerceIn(0.dp, 16.dp))

    when {
        classified.kind == CmsVideoKind.YOUTUBE && classified.youtubeId != null -> {
            val isShort = url.contains("/shorts/", ignoreCase = true)
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .then(if (isShort) Modifier.width(240.dp) else Modifier)
                    .aspectRatio(if (isShort) 9f / 16f else 16f / 9f)
                    .clip(corner)
                    .background(Color.Black),
            ) {
                InlineWebView(
                    html = inlineEmbedHtml(
                        "https://www.youtube.com/embed/${classified.youtubeId}" +
                            "?playsinline=1&rel=0&modestbranding=1&controls=1&fs=1" +
                            "&origin=$CMS_ASSET_ORIGIN",
                    ),
                    baseUrl = CMS_ASSET_ORIGIN,
                    modifier = Modifier.fillMaxSize(),
                )
                OpenExternallyPill(
                    url = classified.sourceUrl
                        ?: "https://www.youtube.com/watch?v=${classified.youtubeId}",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            }
        }

        classified.kind == CmsVideoKind.INSTAGRAM && classified.instagramEmbedUrl != null -> {
            val separator = if (classified.instagramEmbedUrl.contains('?')) "&" else "?"
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .aspectRatio(9f / 16f)
                        .clip(corner)
                        .background(Color.Black),
                ) {
                    InlineWebView(
                        html = inlineEmbedHtml(
                            classified.instagramEmbedUrl +
                                separator + "utm_source=ig_embed&hidecaption=1",
                        ),
                        baseUrl = CMS_ASSET_ORIGIN,
                        modifier = Modifier.fillMaxSize(),
                    )
                    classified.sourceUrl?.let {
                        OpenExternallyPill(
                            url = it,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        )
                    }
                }
            }
        }

        else -> MediaOpenCard(
            url = classified.sourceUrl ?: url,
            accent = accent,
            radius = radius,
            label = "Watch video",
            modifier = modifier,
        )
    }
}

@Composable
private fun OpenExternallyPill(url: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { openUrl(url) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircleFilled,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = "Open",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
private fun MediaOpenCard(
    url: String,
    accent: Color,
    radius: Dp,
    modifier: Modifier = Modifier,
    label: String = "Open link",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(accent)
            .clickable { openUrl(url) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircleFilled,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
fun CmsPdfViewer(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val title = component.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: component.text("title", context.root)
        ?: "Document"
    val url = cmsMakeURL(
        cmsString(component.prop("url", context.root, listOf("pdfUrl", "src", "href"))),
    ) ?: return

    val radius = (if (style.radius > 0) style.radius else 12.0).dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .clip(RoundedCornerShape(radius))
            .background(parseCmsColor(style.background) ?: Slate50)
            .clickable { openUrl(url) }
            .padding((if (style.padding > 0) style.padding else 12.0).dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = Slate500,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = InkStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Open",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = context.accent,
            )
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = context.accent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// MARK: - Rating

@Composable
fun CmsRating(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val ratingRaw = cmsDouble(component.prop("rating", context.root, listOf("value", "score"))) ?: 0.0
    val countRaw = cmsDouble(
        component.prop("count", context.root, listOf("reviews", "reviewCount")),
    )
    val rating = max(0.0, min(5.0, ratingRaw))
    val count = countRaw?.takeIf { it.isFinite() }?.let { max(0, it.toInt()) }
    if (rating <= 0.0 && count == null) return

    val starColor = parseCmsColor(style.color) ?: context.accent
    val alignment = when (style.textAlign) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val full = floor(rating + 1e-9).toInt()
    val fraction = rating - full
    val showHalf = fraction >= 0.25 && fraction < 0.75
    val bumpFull = if (fraction >= 0.75) 1 else 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .padding((if (style.padding != 0.0) max(0.0, style.padding) else 4.0).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { index ->
                val isFull = index < full + bumpFull
                val isHalf = bumpFull == 0 && showHalf && index == full
                Icon(
                    imageVector = if (isHalf) Icons.Default.StarHalf else Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isFull || isHalf) starColor else Slate200,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = formatOneDecimal(rating),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = parseCmsColor(style.color) ?: InkStrong,
        )
        count?.let {
            Text(
                text = "($it ${if (it == 1) "rating" else "ratings"})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Slate500,
            )
        }
    }
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10).toLong()
    return "${scaled / 10}.${scaled % 10}"
}

// MARK: - Coupon

@Composable
fun CmsCoupon(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    val code = component.text("code", context.root).orEmpty()
    val title = component.text("title", context.root) ?: "Coupon"
    val description = cmsDisplayString(component.prop("description", context.root))
    val ctaLabel = component.text("ctaLabel", context.root) ?: "Apply coupon"
    val ctaUrl = cmsMakeURL(cmsString(component.prop("ctaUrl", context.root)))
    val validUntil = component.text("validUntil", context.root)
    val badge = discountBadge(component, context.root)
    val copyEnabled = cmsBool(component.prop("copyEnabled", context.root)) ?: true

    val accentColor = parseCmsColor(cmsString(component.props["accentColor"]))
        ?: context.accent
    val accentText = parseCmsColor(cmsString(component.props["accentTextColor"])) ?: Color.White
    val mutedColor = parseCmsColor(cmsString(component.props["mutedColor"]))
        ?: parseCmsColor(style.color)
        ?: Color(0xFF6D28D9)
    val cardBackground = parseCmsColor(cmsString(component.props["backgroundColor"]))
        ?: parseCmsColor(style.background)
        ?: Color.White
    val borderColor = parseCmsColor(component.styleString("borderColor")) ?: Color(0xFFDDD6FE)
    val borderWidth = (cmsDouble(component.styles["borderWidth"]) ?: 1.0).dp
    val radius = (if (style.radius > 0) style.radius else 10.0).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .clip(RoundedCornerShape(radius))
            .background(cardBackground)
            .then(
                if (borderWidth.value > 0) {
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(radius))
                } else {
                    Modifier
                },
            )
            .padding((if (style.padding > 0) style.padding else 10.0).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = mutedColor,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = mutedColor.copy(alpha = 0.8f),
                    )
                }
            }
            badge?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        if (code.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = code,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = mutedColor,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.08f))
                        .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (copyEnabled) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor)
                            .clickable {
                                clipboard.setText(AnnotatedString(code))
                                copied = true
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (copied) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.ContentCopy
                            },
                            contentDescription = null,
                            tint = accentText,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentText,
                        )
                    }
                }
            }
        }

        validUntil?.let {
            Text(
                text = "Valid until $it",
                fontSize = 11.sp,
                color = mutedColor.copy(alpha = 0.6f),
            )
        }

        ctaUrl?.let { url ->
            Row(
                modifier = Modifier.clickable { openUrl(url) },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ctaLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

// MARK: - Offer

@Composable
fun CmsOffer(
    component: ResolverCmsComponent,
    context: CmsRenderContext,
    modifier: Modifier = Modifier,
) {
    val style = cmsStyle(component.styles)
    val title = component.text("title", context.root) ?: "Special offer"
    val description = cmsDisplayString(component.props["description"]).ifBlank {
        component.bindings["description"]
            ?.let { cmsDisplayString(component.prop("description", context.root)) }
            .orEmpty()
    }
    val ctaLabel = component.text("ctaLabel", context.root) ?: "Claim offer"
    val ctaUrl = cmsMakeURL(
        cmsString(component.props["ctaUrl"])
            ?: cmsString(
                component.prop("ctaUrl", context.root, listOf("url", "href", "linkUrl")),
            ),
    )
    val validUntil = component.text("validUntil", context.root)
    val image = cmsMakeURL(
        cmsString(component.props["image"])
            ?: cmsString(component.prop("image", context.root, listOf("imageUrl"))),
    )
    val badge = discountBadge(component, context.root)

    val radius = (if (style.radius > 0) style.radius else 10.0).dp
    val textColor = parseCmsColor(style.color) ?: Color(0xFF272220)
    val borderWidth = (cmsDouble(component.styles["borderWidth"]) ?: 1.0).dp
    val borderColor = parseCmsColor(component.styleString("borderColor")) ?: Color(0xFFFED7AA)
    val cardBackground = parseCmsColor(style.background)
        ?: borderColor.copy(alpha = 0.16f)
    val badgeBackground = parseCmsColor(
        component.styleString("badgeColor")
            ?: component.styleString("badgeBackground")
            ?: cmsString(component.props["badgeColor"]),
    ) ?: context.accent
    val badgeText = parseCmsColor(component.styleString("badgeTextColor")) ?: Color.White
    val ctaBackground = parseCmsColor(
        component.styleString("ctaBackground")
            ?: component.styleString("buttonBackground")
            ?: cmsString(component.props["ctaColor"]),
    ) ?: context.accent
    val ctaText = parseCmsColor(
        component.styleString("ctaTextColor") ?: component.styleString("buttonTextColor"),
    ) ?: Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = (style.margin / 2).dp)
            .clip(RoundedCornerShape(radius))
            .background(cardBackground)
            .then(
                if (borderWidth.value > 0) {
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(radius))
                } else {
                    Modifier
                },
            )
            .padding((if (style.padding > 0) style.padding else 10.0).dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        image?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                )
                badge?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBackground)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = textColor.copy(alpha = 0.8f),
                )
            }
            validUntil?.let {
                Text(
                    text = "Valid until $it",
                    fontSize = 11.sp,
                    color = textColor.copy(alpha = 0.6f),
                )
            }
            Text(
                text = ctaLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ctaText,
                modifier = Modifier
                    .clip(RoundedCornerShape(min(radius.value.toDouble(), 10.0).dp))
                    .background(ctaBackground.copy(alpha = if (ctaUrl != null) 1f else 0.85f))
                    .then(if (ctaUrl != null) Modifier.clickable { openUrl(ctaUrl) } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun discountBadge(component: ResolverCmsComponent, root: JsonObject): String? {
    val value = cmsDisplayString(component.props["discountValue"])
        .ifBlank { cmsString(component.prop("discountValue", root)).orEmpty() }
        .trim()
    if (value.isEmpty()) return null
    val type = (
        cmsString(component.props["discountType"])
            ?: cmsString(component.prop("discountType", root))
            ?: "percent"
        ).lowercase()
    return if (type == "amount" || type == "flat") "₹$value OFF" else "$value% OFF"
}

private val IMAGE_SLIDE = Regex("""\.(png|jpe?g|gif|webp|avif|svg)($|\?|#)""")
private val SLIDE_IMAGE_PATH = Regex("""/images?/""")
