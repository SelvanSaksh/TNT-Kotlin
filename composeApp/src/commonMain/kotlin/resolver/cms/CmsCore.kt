package resolver.cms

import io.ktor.http.encodeURLPath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import network.Config
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Public web host that serves relative CMS assets such as `/images/logo.png`.
 * Derived from the API host so QA and production stay in step.
 */
val CMS_ASSET_ORIGIN: String = when {
    Config.BASE_URL.contains("qa.api.ratifye.ai") -> "https://qa.ratifye.ai"
    Config.BASE_URL.contains("api.ratifye.ai") -> "https://ratifye.ai"
    else -> "https://qa.ratifye.ai"
}

// MARK: - JSON accessors

fun cmsDict(value: JsonElement?): JsonObject? = value as? JsonObject

fun cmsArray(value: JsonElement?): JsonArray? = value as? JsonArray

fun cmsString(value: JsonElement?): String? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content
}

fun cmsBool(value: JsonElement?): Boolean? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    if (!primitive.isString) {
        primitive.booleanOrNull?.let { return it }
        primitive.doubleOrNull?.let { return it != 0.0 }
        return null
    }
    return when (primitive.content.trim().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}

fun cmsDouble(value: JsonElement?): Double? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    if (!primitive.isString) return primitive.doubleOrNull
    return primitive.content.trim().toDoubleOrNull()
}

fun cmsInt(value: JsonElement?): Int? = cmsDouble(value)?.toInt()

/** Treats null, JSON null and blank strings as absent, like the web resolver. */
fun cmsIsMissing(value: JsonElement?): Boolean {
    if (value == null || value is JsonNull) return true
    val primitive = value as? JsonPrimitive ?: return false
    return primitive.isString && primitive.content.trim().isEmpty()
}

fun cmsDisplayString(value: JsonElement?): String = cmsUnwrappedString(value) ?: ""

/**
 * Strings, or `{ value | name | title | text | url }` objects the CMS sometimes stores.
 */
fun cmsUnwrappedString(value: JsonElement?): String? {
    cmsString(value)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val dict = cmsDict(value) ?: return null
    val keys = listOf(
        "value", "name", "title", "text", "label",
        "productName", "product_name", "url", "src", "href",
    )
    for (key in keys) {
        cmsString(dict[key])?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

/** Product title from the same `product` object that supplies `product.images[0]`. */
fun cmsProductDisplayName(root: JsonElement?): String {
    val product = cmsDict(cmsResolve("product", root)) ?: return ""
    val keys = listOf(
        "productName",
        "product_name",
        "name",
        "title",
        "productTitle",
        "displayName",
        "display_name",
        "Product Name",
    )
    for (key in keys) {
        cmsUnwrappedString(product[key])?.let { return it }
    }
    val details = cmsDict(product["details"])
        ?: cmsDict(product["productDetails"])
        ?: cmsDict(product["product_details"])
        ?: cmsDict(product["Product Details"])
    if (details != null) {
        for (key in keys) {
            cmsUnwrappedString(details[key])?.let { return it }
        }
    }
    val image0 = cmsArray(product["images"])?.firstOrNull()
    cmsDict(image0)?.let { image ->
        for (key in listOf("name", "title", "alt", "productName", "product_name")) {
            cmsUnwrappedString(image[key])
                ?.takeUnless { it.startsWith("http", ignoreCase = true) }
                ?.let { return it }
        }
    }
    return ""
}

/** Copies a resolved title onto `product.productName` so CMS bindings can read it. */
fun fillProductName(root: JsonObject): JsonObject {
    val product = cmsDict(root["product"]) ?: return root
    if (!cmsString(product["productName"]).isNullOrBlank()) return root
    val name = cmsProductDisplayName(root)
    if (name.isBlank()) return root
    return JsonObject(
        root + ("product" to JsonObject(product + ("productName" to JsonPrimitive(name)))),
    )
}

// MARK: - Binding paths

private sealed interface CmsToken {
    data class Key(val value: String) : CmsToken
    data class Index(val value: Int) : CmsToken
}

/** Splits a binding path such as `product.images[0]` into keys and indices. */
private fun cmsTokenize(path: String): List<CmsToken> {
    val tokens = mutableListOf<CmsToken>()
    var name = StringBuilder()
    var indexBuffer = StringBuilder()
    var inBracket = false

    for (char in path) {
        when {
            char == '[' -> {
                inBracket = true
                indexBuffer = StringBuilder()
                if (name.isNotEmpty()) {
                    tokens.add(CmsToken.Key(name.toString()))
                    name = StringBuilder()
                }
            }

            char == ']' -> {
                inBracket = false
                indexBuffer.toString().toIntOrNull()?.let { tokens.add(CmsToken.Index(it)) }
            }

            inBracket -> indexBuffer.append(char)

            char == '.' -> {
                if (name.isNotEmpty()) {
                    tokens.add(CmsToken.Key(name.toString()))
                    name = StringBuilder()
                }
            }

            else -> name.append(char)
        }
    }
    if (name.isNotEmpty()) tokens.add(CmsToken.Key(name.toString()))
    return tokens
}

/** Follows a binding path through the CMS bind root. */
fun cmsResolve(path: String, root: JsonElement?): JsonElement? {
    var current: JsonElement? = root
    for (token in cmsTokenize(path)) {
        when (token) {
            is CmsToken.Key -> {
                val dict = cmsDict(current) ?: return null
                val next = dict[token.value] ?: return null
                if (next is JsonNull) return null
                current = next
            }

            is CmsToken.Index -> {
                val array = cmsArray(current) ?: return null
                if (token.value < 0 || token.value >= array.size) return null
                val next = array[token.value]
                if (next is JsonNull) return null
                current = next
            }
        }
    }
    return if (cmsIsMissing(current)) null else current
}

/** Bindings resolve against `payload.data`, not the envelope. */
fun cmsBindRoot(payload: JsonElement?): JsonObject =
    cmsDict(cmsDict(payload)?.get("data")) ?: JsonObject(emptyMap())

// MARK: - Widget types

enum class CmsWidgetType(val wire: String) {
    PRODUCT_IMAGE("product_image"),
    IMAGE("image"),
    HERO("hero"),
    BRAND_LOGO("brand_logo"),
    BANNER("banner"),
    PACK_CARD("pack_card"),
    TITLE("title"),
    HEADING("heading"),
    PRICE_ROW("price_row"),
    PRICE("price"),
    PRODUCT_DESCRIPTION("product_description"),
    DESCRIPTION("description"),
    TEXT("text"),
    LINK("link"),
    BUTTONS("buttons"),
    BUTTON("button"),
    CTA("cta"),
    YOUTUBE("youtube"),
    YOUTUBE_PLAYER("youtube_player"),
    RELATED_PRODUCTS("related_products"),
    CAROUSEL("carousel"),
    TIMELINE("timeline"),
    FEATURE_CHIPS("feature_chips"),
    SOCIAL_LINKS("social_links"),
    OFFER("offer"),
    COUPON("coupon"),
    RATING("rating"),
    PDF_VIEWER("pdf_viewer"),
    FOOTER("footer"),
    DIVIDER("divider"),
    SPACER("spacer"),
    UNKNOWN("unknown"),
}

private val WIDGET_BY_WIRE: Map<String, CmsWidgetType> =
    CmsWidgetType.entries.filter { it != CmsWidgetType.UNKNOWN }.associateBy { it.wire }

fun parseWidgetType(raw: String): CmsWidgetType = WIDGET_BY_WIRE[raw] ?: CmsWidgetType.UNKNOWN

/** The CMS builder emits `hero` for the product card, so it counts as an image. */
val CmsWidgetType.isImage: Boolean
    get() = this == CmsWidgetType.PRODUCT_IMAGE ||
        this == CmsWidgetType.IMAGE ||
        this == CmsWidgetType.HERO

val CmsWidgetType.isBrandLogo: Boolean get() = this == CmsWidgetType.BRAND_LOGO

val CmsWidgetType.isBanner: Boolean get() = this == CmsWidgetType.BANNER

val CmsWidgetType.isTitle: Boolean
    get() = this == CmsWidgetType.TITLE || this == CmsWidgetType.HEADING

val CmsWidgetType.isPrice: Boolean
    get() = this == CmsWidgetType.PRICE_ROW || this == CmsWidgetType.PRICE

val CmsWidgetType.isDescription: Boolean
    get() = this == CmsWidgetType.PRODUCT_DESCRIPTION ||
        this == CmsWidgetType.DESCRIPTION ||
        this == CmsWidgetType.TEXT

val CmsWidgetType.isLink: Boolean get() = this == CmsWidgetType.LINK

val CmsWidgetType.isButton: Boolean
    get() = this == CmsWidgetType.BUTTONS ||
        this == CmsWidgetType.BUTTON ||
        this == CmsWidgetType.CTA

val CmsWidgetType.isYouTube: Boolean
    get() = this == CmsWidgetType.YOUTUBE || this == CmsWidgetType.YOUTUBE_PLAYER

val CmsWidgetType.isFooter: Boolean get() = this == CmsWidgetType.FOOTER

val CmsWidgetType.isRelatedProducts: Boolean get() = this == CmsWidgetType.RELATED_PRODUCTS

val CmsWidgetType.isCarousel: Boolean get() = this == CmsWidgetType.CAROUSEL

val CmsWidgetType.isTimeline: Boolean get() = this == CmsWidgetType.TIMELINE

val CmsWidgetType.isFeatureChips: Boolean get() = this == CmsWidgetType.FEATURE_CHIPS

val CmsWidgetType.isSocialLinks: Boolean get() = this == CmsWidgetType.SOCIAL_LINKS

val CmsWidgetType.isOffer: Boolean get() = this == CmsWidgetType.OFFER

val CmsWidgetType.isCoupon: Boolean get() = this == CmsWidgetType.COUPON

val CmsWidgetType.isRating: Boolean get() = this == CmsWidgetType.RATING

val CmsWidgetType.isPdfViewer: Boolean get() = this == CmsWidgetType.PDF_VIEWER

val CmsWidgetType.isDivider: Boolean
    get() = this == CmsWidgetType.DIVIDER || this == CmsWidgetType.SPACER

// MARK: - Response models

data class ResolverCmsBackground(val type: String, val value: String)

data class ResolverCmsTheme(
    val primaryColor: String?,
    val background: ResolverCmsBackground?,
    val chromeColorHex: String?,
)

data class ResolverCmsLayout(
    val container: String?,
    val breakpoints: Map<String, Int>,
)

data class ResolverCmsComponent(
    val id: String,
    val type: String,
    val name: String?,
    val props: JsonObject,
    val styles: JsonObject,
    val bindings: Map<String, String>,
    val visibility: JsonObject,
    val isEnabled: Boolean,
    val isVisible: Boolean,
    val children: List<ResolverCmsComponent>,
    val widgetType: CmsWidgetType,
) {
    /**
     * Reads a widget value, preferring an explicit binding, then any alias
     * binding or `product.<alias>`, then the literal prop.
     */
    fun prop(
        key: String,
        root: JsonElement?,
        aliases: List<String> = emptyList(),
    ): JsonElement? {
        bindings[key]?.let { binding ->
            val resolved = cmsResolve(binding, root)
            if (!cmsIsMissing(resolved)) return resolved
        }

        for (alias in aliases) {
            bindings[alias]?.let { binding ->
                val resolved = cmsResolve(binding, root)
                if (!cmsIsMissing(resolved)) return resolved
            }
            val fromProduct = cmsResolve("product.$alias", root)
            if (!cmsIsMissing(fromProduct)) return fromProduct
            val fromProps = props[alias]
            if (!cmsIsMissing(fromProps)) return fromProps
        }

        val fallback = props[key]
        return if (cmsIsMissing(fallback)) null else fallback
    }

    fun propString(key: String, root: JsonElement?, aliases: List<String> = emptyList()): String? =
        cmsString(prop(key, root, aliases))

    fun propDouble(key: String, root: JsonElement?, aliases: List<String> = emptyList()): Double? =
        cmsDouble(prop(key, root, aliases))
}

data class ResolverCmsContent(
    val theme: ResolverCmsTheme?,
    val layout: ResolverCmsLayout?,
    val components: List<ResolverCmsComponent>,
    val schemaVersion: String?,
)

data class ResolverCmsPage(
    val id: Int?,
    val name: String?,
    val companyId: String?,
    val gtin: String?,
    val content: ResolverCmsContent?,
)

data class ResolverCmsResponse(
    val exists: Boolean,
    val root: JsonObject,
    val page: ResolverCmsPage?,
    val hasPage: Boolean,
)

// MARK: - Parsing

private fun parseTheme(dict: JsonObject): ResolverCmsTheme {
    val primaryColor = cmsString(dict["primaryColor"])
    val background = cmsDict(dict["background"])?.let {
        ResolverCmsBackground(
            type = cmsString(it["type"]) ?: "color",
            value = cmsString(it["value"]) ?: "",
        )
    }
    val chromeColorHex =
        if (background != null && background.type == "color" && background.value.isNotEmpty()) {
            background.value
        } else {
            primaryColor
        }
    return ResolverCmsTheme(primaryColor, background, chromeColorHex)
}

private fun parseLayout(dict: JsonObject): ResolverCmsLayout {
    val breakpoints = mutableMapOf<String, Int>()
    cmsDict(dict["breakpoints"])?.forEach { (key, value) ->
        cmsInt(value)?.let { breakpoints[key] = it }
    }
    return ResolverCmsLayout(cmsString(dict["container"]), breakpoints)
}

private fun makeComponent(dict: JsonObject, fallbackId: String): ResolverCmsComponent {
    val bindings = mutableMapOf<String, String>()
    cmsDict(dict["bindings"])?.forEach { (key, value) ->
        cmsString(value)?.let { bindings[key] = it }
    }

    val children = (cmsArray(dict["children"]) ?: JsonArray(emptyList()))
        .mapIndexedNotNull { index, child ->
            cmsDict(child)?.let { makeComponent(it, "$fallbackId-$index") }
        }

    val type = cmsString(dict["type"]) ?: "unknown"
    return ResolverCmsComponent(
        id = cmsString(dict["id"]) ?: fallbackId,
        type = type,
        name = cmsString(dict["name"]),
        props = cmsDict(dict["props"]) ?: JsonObject(emptyMap()),
        styles = cmsDict(dict["styles"]) ?: JsonObject(emptyMap()),
        bindings = bindings,
        visibility = cmsDict(dict["visibility"]) ?: JsonObject(emptyMap()),
        isEnabled = cmsBool(dict["enabled"]) ?: true,
        isVisible = cmsBool(dict["visible"]) ?: true,
        children = children,
        widgetType = parseWidgetType(type),
    )
}

private fun parseContent(dict: JsonObject): ResolverCmsContent = ResolverCmsContent(
    theme = cmsDict(dict["theme"])?.let { parseTheme(it) },
    layout = cmsDict(dict["layout"])?.let { parseLayout(it) },
    components = (cmsArray(dict["components"]) ?: JsonArray(emptyList()))
        .mapIndexedNotNull { index, item ->
            cmsDict(item)?.let { makeComponent(it, "component-$index") }
        },
    schemaVersion = cmsString(dict["schemaVersion"]),
)

private fun parsePage(dict: JsonObject): ResolverCmsPage {
    val contentDict = cmsDict(dict["content"])
    return ResolverCmsPage(
        id = cmsInt(dict["id"]),
        name = cmsString(dict["name"]),
        companyId = cmsString(dict["company_id"]),
        gtin = cmsString(dict["gtin"])
            ?: contentDict?.let { cmsString(cmsDict(it["meta"])?.get("gtin")) },
        content = contentDict?.let { parseContent(it) },
    )
}

fun parseCmsResponse(payload: JsonElement?): ResolverCmsResponse {
    val root = fillProductName(cmsBindRoot(payload))
    val page = cmsDict(root["page"])?.let { parsePage(it) }
    return ResolverCmsResponse(
        exists = cmsBool(root["exists"]) ?: false,
        root = root,
        page = page,
        hasPage = (page?.content?.components?.size ?: 0) > 0,
    )
}

// MARK: - GTIN matching

/** Trims and drops leading zeros so 13 and 14 digit forms compare equal. */
fun cmsNormalizeGtin(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    return trimmed.trimStart('0').ifEmpty { "0" }
}

/**
 * True when the returned page belongs to the requested GTIN. The QA API
 * sometimes answers with another product's page while `data.product` is
 * correct, so callers check this before trusting the page.
 */
fun cmsPageMatchesGtin(page: ResolverCmsPage?, requestedGtin: String?): Boolean {
    if (page == null) return false
    val want = cmsNormalizeGtin(requestedGtin)
    if (want.isEmpty()) return false
    val got = cmsNormalizeGtin(page.gtin)
    if (got.isEmpty()) return true
    return got == want
}

fun cmsProductMatchesGtin(root: JsonObject, requestedGtin: String?): Boolean {
    val want = cmsNormalizeGtin(requestedGtin)
    if (want.isEmpty()) return false
    val product = cmsDict(root["product"]) ?: return false
    val got = cmsNormalizeGtin(cmsString(product["identifier"]))
    return got.isNotEmpty() && got == want
}

/** Minimal page used when the API returns a page for a different GTIN. */
fun cmsBuildProductFallbackPage(
    requestedGtin: String,
    product: JsonObject,
    theme: ResolverCmsTheme?,
): ResolverCmsPage {
    val name = cmsProductDisplayName(
        buildJsonObject { put("product", product) },
    ).ifBlank { cmsString(product["productName"]).orEmpty() }.ifBlank { "Product" }
    val description = cmsString(product["description"]) ?: ""

    val pageJson = buildJsonObject {
        put("name", name)
        put("gtin", requestedGtin)
        putJsonObject("content") {
            put("schemaVersion", "1.0")
            putJsonObject("theme") {
                put("primaryColor", theme?.primaryColor ?: "#4338CA")
                putJsonObject("background") {
                    put("type", theme?.background?.type ?: "color")
                    put("value", theme?.background?.value ?: "#ffffff")
                }
            }
            putJsonArray("components") {
                add(
                    buildJsonObject {
                        put("id", "fallback_product_card")
                        put("name", "Product Card")
                        put("type", "product_image")
                        put("enabled", true)
                        put("visible", true)
                        putJsonObject("props") {
                            put("title", name)
                            put("brand", cmsString(product["brandName"]) ?: "")
                            put("price", cmsDouble(product["mrp"]) ?: 0.0)
                            put("currency", "INR")
                            put("image", "")
                            put("badge", "Official")
                        }
                        putJsonObject("bindings") {
                            put("brand", "product.brandName")
                            put("image", "product.images[0]")
                            put("price", "product.mrp")
                            put("title", "product.productName")
                        }
                        putJsonObject("styles") {
                            put("gap", 12)
                            put("radius", 16)
                            put("padding", 12)
                            put("textAlign", "left")
                            put("background", "#ffffff")
                        }
                    },
                )
                if (description.isNotBlank()) {
                    add(
                        buildJsonObject {
                            put("id", "fallback_description")
                            put("name", "Product Description")
                            put("type", "product_description")
                            put("enabled", true)
                            put("visible", true)
                            putJsonObject("props") { put("text", description) }
                            putJsonObject("bindings") { put("text", "product.description") }
                            putJsonObject("styles") {
                                put("fontSize", 14)
                                put("textAlign", "left")
                                put("textColor", "#0f172a")
                                put("fontWeight", "400")
                            }
                        },
                    )
                }
            }
        }
    }

    return parsePage(pageJson)
}

// MARK: - URLs and media

private val DOUBLE_PROTOCOL = Regex("""^https?://https?://""", RegexOption.IGNORE_CASE)
private val LEADING_PROTOCOL = Regex("""^https?://""", RegexOption.IGNORE_CASE)
private val URL_SHAPE = Regex("""^(https?://[^/?#]+)(/[^?#]*)?(\?[^#]*)?(#.*)?$""", RegexOption.IGNORE_CASE)
private val VIDEO_FILE_EXT = Regex("""\.(mp4|webm|ogg|ogv|mov|m4v)($|\?|#)""", RegexOption.IGNORE_CASE)
private val YOUTUBE_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

/**
 * Builds an absolute, request-safe URL for a CMS asset. Odd characters in
 * object keys are preserved rather than normalised, since R2 and S3 keep those
 * bytes literally and rewriting them produces 404s.
 */
fun cmsMakeURL(raw: String?): String? {
    var value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null

    // CMS editors occasionally save `https://https://example.com/...`.
    while (DOUBLE_PROTOCOL.containsMatchIn(value)) {
        value = LEADING_PROTOCOL.replaceFirst(value, "")
    }

    if (!LEADING_PROTOCOL.containsMatchIn(value)) {
        if (value.startsWith("/") || !value.contains("://")) {
            val origin = CMS_ASSET_ORIGIN.trimEnd('/')
            value = origin + if (value.startsWith("/")) value else "/$value"
        }
    }

    val match = URL_SHAPE.matchEntire(value) ?: return value
    val origin = match.groupValues[1]
    val path = match.groupValues[2]
    val query = match.groupValues[3]
    val fragment = match.groupValues[4]
    // A '%' means the path is already percent-encoded; re-encoding would double it.
    val encodedPath = if (path.isEmpty() || path.contains('%')) path else path.encodeURLPath()
    return origin + encodedPath + query + fragment
}

private fun urlHost(url: String): String =
    url.substringAfter("://", url).substringBefore('/').substringBefore('?').lowercase()

private fun urlPathSegments(url: String): List<String> {
    val afterHost = url.substringAfter("://", url)
    val path = afterHost.substringBefore('#').substringBefore('?')
    val slash = path.indexOf('/')
    if (slash < 0) return emptyList()
    return path.substring(slash).split('/').filter { it.isNotEmpty() }
}

private fun urlQueryParam(url: String, key: String): String? {
    val query = url.substringBefore('#').substringAfter('?', "")
    if (query.isEmpty()) return null
    return query.split('&')
        .firstOrNull { it.substringBefore('=') == key }
        ?.substringAfter('=', "")
        ?.takeIf { it.isNotEmpty() }
}

fun cmsYouTubeVideoID(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (YOUTUBE_ID.matches(trimmed)) return trimmed

    val url = if (trimmed.startsWith("http", ignoreCase = true)) trimmed else "https://$trimmed"
    val host = urlHost(url)

    if (host.contains("youtu.be")) {
        return urlPathSegments(url).firstOrNull()
    }
    if (host.contains("youtube.com") || host.contains("youtube-nocookie.com")) {
        urlQueryParam(url, "v")?.let { return it }
        val segments = urlPathSegments(url)
        for (key in listOf("embed", "shorts", "live")) {
            val index = segments.indexOf(key)
            if (index >= 0) segments.getOrNull(index + 1)?.let { return it }
        }
    }
    return null
}

/** Official Instagram embed URL for a public post, reel or TV link. */
fun cmsInstagramEmbedURL(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    val url = if (trimmed.startsWith("http", ignoreCase = true)) trimmed else "https://$trimmed"
    if (!urlHost(url).contains("instagram.com")) return null

    val segments = urlPathSegments(url)
    var embedType: String? = null
    var typeIndex = -1
    for ((index, segment) in segments.withIndex()) {
        when (segment.lowercase()) {
            "reel", "reels" -> {
                embedType = "reel"
                typeIndex = index
            }

            "p" -> {
                embedType = "p"
                typeIndex = index
            }

            "tv" -> {
                embedType = "tv"
                typeIndex = index
            }
        }
        if (embedType != null) break
    }
    val id = if (typeIndex >= 0) segments.getOrNull(typeIndex + 1) else null
    if (embedType == null || id == null) return null
    return "https://www.instagram.com/$embedType/$id/embed"
}

enum class CmsVideoKind { YOUTUBE, INSTAGRAM, FILE, UNKNOWN }

data class CmsClassifiedVideo(
    val kind: CmsVideoKind,
    val sourceUrl: String?,
    val youtubeId: String?,
    val instagramEmbedUrl: String?,
    val fileUrl: String?,
)

fun cmsClassifyVideoURL(raw: String?): CmsClassifiedVideo {
    val empty = CmsClassifiedVideo(CmsVideoKind.UNKNOWN, null, null, null, null)
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return empty

    val sourceUrl = cmsMakeURL(trimmed) ?: trimmed

    cmsYouTubeVideoID(trimmed)?.let {
        return CmsClassifiedVideo(CmsVideoKind.YOUTUBE, sourceUrl, it, null, null)
    }
    cmsInstagramEmbedURL(trimmed)?.let {
        return CmsClassifiedVideo(CmsVideoKind.INSTAGRAM, sourceUrl, null, it, null)
    }
    if (VIDEO_FILE_EXT.containsMatchIn(sourceUrl)) {
        return CmsClassifiedVideo(CmsVideoKind.FILE, sourceUrl, null, null, sourceUrl)
    }
    return CmsClassifiedVideo(CmsVideoKind.UNKNOWN, sourceUrl, null, null, null)
}

/** Link for a widget, checking its own props before the caller's raw value. */
fun cmsLinkURL(
    raw: String?,
    root: JsonObject,
    component: ResolverCmsComponent?,
): String? {
    val candidate = component?.let {
        cmsString(it.prop("linkUrl", root, listOf("url", "href", "meta")))
            ?: cmsString(it.props["linkUrl"])
            ?: cmsString(it.props["url"])
            ?: cmsString(it.props["meta"])
    } ?: raw
    return cmsSafeLink(candidate)
}

/** Accepts only http and https links, matching the web resolver's guard. */
fun cmsSafeLink(raw: String?): String? {
    val url = cmsMakeURL(raw) ?: return null
    if (!LEADING_PROTOCOL.containsMatchIn(url)) return null
    return if (urlHost(url).isEmpty()) null else url
}

// MARK: - Formatting

/** Indian digit grouping, matching `Intl.NumberFormat("en-IN")`. */
fun cmsCurrency(value: Double, symbol: String? = null): String {
    val rounded = value.roundToLong()
    val digits = abs(rounded).toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val chunks = mutableListOf<String>()
        while (rest.length > 2) {
            chunks.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) chunks.add(0, rest)
        chunks.joinToString(",") + "," + last3
    }
    val sign = if (rounded < 0) "-" else ""
    return "${symbol ?: "₹"}$sign$grouped"
}

fun cmsIsLightColor(hex: String): Boolean {
    val cleaned = hex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    val rgb = when (cleaned.length) {
        3 -> cleaned.map { "$it$it" }
        6 -> listOf(cleaned.substring(0, 2), cleaned.substring(2, 4), cleaned.substring(4, 6))
        8 -> listOf(cleaned.substring(2, 4), cleaned.substring(4, 6), cleaned.substring(6, 8))
        else -> return true
    }
    val (r, g, b) = rgb.map { (it.toIntOrNull(16) ?: 0) / 255.0 }
    return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.62
}

data class ResolvedCmsStyle(
    val fontSize: Double,
    val fontWeight: Int,
    val color: String?,
    val textAlign: String,
    val padding: Double,
    val radius: Double,
    val background: String?,
    val margin: Double,
    val gap: Double,
    val width: Double,
    val height: Double,
)

private fun cmsWeight(raw: String?): Int = when (raw?.trim()) {
    "100", "200" -> 200
    "300" -> 300
    "400" -> 400
    "500" -> 500
    "600" -> 600
    "700" -> 700
    "800", "900" -> 800
    else -> 400
}

fun cmsStyle(styles: JsonObject, defaultColor: String? = null): ResolvedCmsStyle {
    val textAlignRaw = cmsString(styles["textAlign"])
    return ResolvedCmsStyle(
        fontSize = cmsDouble(styles["fontSize"]) ?: 14.0,
        fontWeight = cmsWeight(cmsString(styles["fontWeight"])),
        color = cmsString(styles["textColor"]) ?: defaultColor,
        textAlign = if (textAlignRaw == "center" || textAlignRaw == "right") textAlignRaw else "left",
        padding = cmsDouble(styles["padding"]) ?: 0.0,
        radius = cmsDouble(styles["radius"]) ?: 0.0,
        background = cmsString(styles["background"]),
        margin = cmsDouble(styles["margin"]) ?: 0.0,
        gap = cmsDouble(styles["gap"]) ?: 0.0,
        width = cmsDouble(styles["width"]) ?: 0.0,
        height = cmsDouble(styles["height"]) ?: 0.0,
    )
}

/** Widgets can be limited to authenticated scans through `visibility.field`. */
fun shouldShowComponent(component: ResolverCmsComponent, isAuthenticated: Boolean): Boolean {
    if (!component.isEnabled || !component.isVisible) return false
    return when (cmsString(component.visibility["field"])) {
        "authenticated" -> isAuthenticated
        else -> true
    }
}

fun isAuthenticQuality(quality: String?): Boolean {
    if (quality.isNullOrBlank()) return false
    return quality.trim().lowercase() in setOf("real", "original", "authentic")
}
