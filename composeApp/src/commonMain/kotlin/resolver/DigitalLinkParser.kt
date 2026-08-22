package resolver

import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import resolverModels.AiSource
import resolverModels.ApplicationIdentifier
import resolverModels.ParsedData
import resolverModels.SpecialIdentifier

/** Host that serves Ratifye GS1 Digital Links. */
const val RESOLVER_HOST = "dl.ratifye.ai"

/** AIs the resolver keeps out of the displayed grid; 97 and 98 drive authentication. */
private val SPECIAL_AIS = setOf("96", "97", "98", "99")

fun isRatifyeDigitalLink(raw: String): Boolean =
    raw.trim().contains(RESOLVER_HOST, ignoreCase = true)

data class AiPair(val ai: String, val value: String)

/** Everything a scanned Digital Link yields, ready for the resolver screen. */
data class DigitalLinkScan(
    val data: ParsedData,
    val gtin: String?,
    val ai97: String?,
    val ai98: String?,
    /** Link with the 97/98 authentication AIs removed, sent as `barcode_data`. */
    val cleanUrl: String,
)

data class CleanedDigitalLink(
    val barcodeData: String,
    val ai97: String?,
    val ai98: String?,
)

private data class UrlParts(val origin: String, val path: String, val query: String)

private val PAREN_AI = Regex("""\((\d{2,4})\)""")
private val EMBEDDED_SPLIT = Regex("""^(\w+)(\(\d{2,4}\).*)$""")
private val EMBEDDED_PART = Regex("""\((\d{2,4})\)([^(]*)""")
private val PAREN_AI_VALUE = Regex("""\((\d{2,4})\)([^()]*)""")
private val PAREN_AI_STRIP = Regex("""\(\d{2,4}\)[^()]+""")

/**
 * Splits a URL without depending on `java.net.URL`, which is JVM only.
 * A link with no path yields `"/"` so segment handling stays uniform.
 */
private fun splitUrl(raw: String): UrlParts {
    val noHash = raw.trim().substringBefore('#')
    val beforeQuery = noHash.substringBefore('?')
    val query = noHash.substringAfter('?', "")
    val schemeEnd = beforeQuery.indexOf("://")
    val authorityStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
    val slash = beforeQuery.indexOf('/', authorityStart)
    return if (slash >= 0) {
        UrlParts(beforeQuery.substring(0, slash), beforeQuery.substring(slash), query)
    } else {
        UrlParts(beforeQuery, "/", query)
    }
}

private fun hostOf(origin: String): String =
    origin.substringAfter("://", origin).substringBefore('/')

private fun parseQueryParams(query: String): List<Pair<String, String>> =
    query.split('&')
        .filter { it.isNotBlank() }
        .map { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            key.decodeURLQueryComponent(plusIsSpace = true) to
                value.decodeURLQueryComponent(plusIsSpace = true)
        }

/** Rewrites `(01)123` style element strings into `/01/123/` path form. */
fun normalizeParenthesesAI(path: String): String =
    PAREN_AI.replace(path) { "/${it.groupValues[1]}/" }

/** Reads a `/ai/value/ai/value` path into ordered pairs. */
fun parseAiPath(path: String): List<AiPair> {
    val segments = path.trimStart('/').split('/')
    val pairs = mutableListOf<AiPair>()
    var i = 0
    while (i + 1 < segments.size) {
        val ai = segments[i]
        val value = segments[i + 1]
        if (ai.isNotEmpty() && value.isNotEmpty()) pairs.add(AiPair(ai, value))
        i += 2
    }
    return pairs
}

private data class EmbeddedAis(
    val cleanedParams: List<Pair<String, String>>,
    val embedded: Map<String, String>,
)

/**
 * Some encoders append extra AIs onto the last query value, e.g.
 * `?10=BATCH(98)token(97)42`. Those are pulled out so the visible value stays clean.
 */
private fun extractEmbeddedAIs(params: List<Pair<String, String>>): EmbeddedAis {
    val cleaned = mutableListOf<Pair<String, String>>()
    val embedded = linkedMapOf<String, String>()
    params.forEach { (key, value) ->
        val match = EMBEDDED_SPLIT.matchEntire(value)
        if (match != null) {
            cleaned.add(key to match.groupValues[1])
            EMBEDDED_PART.findAll(match.groupValues[2]).forEach { part ->
                embedded[part.groupValues[1]] = part.groupValues[2]
            }
        } else {
            cleaned.add(key to value)
        }
    }
    return EmbeddedAis(cleaned, embedded)
}

/**
 * Strips the 97/98 authentication AIs from a link, wherever they appear, and
 * returns the remainder. The server signs that remainder, so it is the value
 * sent as `barcode_data`.
 */
fun removeAuthFromFullUrl(url: String): CleanedDigitalLink {
    val parts = splitUrl(url)
    var ai97: String? = null
    var ai98: String? = null
    val cleanedParams = mutableListOf<Pair<String, String>>()

    parseQueryParams(parts.query).forEach { (key, value) ->
        when (key) {
            "98" -> {
                val pieces = value.split('/')
                ai98 = pieces[0]
                pieces.getOrNull(1)?.let { extra ->
                    if (extra.substringBefore('=') == "97") ai97 = extra.substringAfter('=', "")
                }
            }

            "97" -> ai97 = value

            else -> {
                PAREN_AI_VALUE.findAll(value).forEach { match ->
                    when (match.groupValues[1]) {
                        "98" -> ai98 = match.groupValues[2]
                        "97" -> ai97 = match.groupValues[2]
                    }
                }
                cleanedParams.add(key to PAREN_AI_STRIP.replace(value, ""))
            }
        }
    }

    val segments = parts.path.split('/').filter { it.isNotEmpty() }
    val cleanedSegments = mutableListOf<String>()
    var i = 0
    while (i < segments.size) {
        val segment = segments[i]
        if (segment == "97" || segment == "98") {
            val next = segments.getOrNull(i + 1)
            if (segment == "97") ai97 = next
            if (segment == "98") ai98 = next
            i += 2
            continue
        }
        cleanedSegments.add(segment)
        i += 1
    }

    var barcodeData = parts.origin + "/" + cleanedSegments.joinToString("/")
    if (cleanedParams.isNotEmpty()) {
        barcodeData += "?" + cleanedParams.joinToString("&") { (key, value) ->
            key.encodeURLParameter(spaceToPlus = true) + "=" +
                value.encodeURLParameter(spaceToPlus = true)
        }
    }
    return CleanedDigitalLink(barcodeData, ai97, ai98)
}

/** Display-only: drop AIs 97 and 98 from a Digital Link or GS1 element string. */
fun stripAuthAis97And98(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw
    val withoutLinkAuth = if (
        trimmed.contains("://") || trimmed.contains(RESOLVER_HOST, ignoreCase = true)
    ) {
        removeAuthFromFullUrl(trimmed).barcodeData
    } else {
        trimmed
    }
    return withoutLinkAuth
        .replace(Regex("""\(97\)[^()]*"""), "")
        .replace(Regex("""\(98\)[^()]*"""), "")
}

/** Full parse of a scanned Digital Link into identifiers plus auth material. */
fun parseDigitalLink(fullUrl: String): DigitalLinkScan {
    val parts = splitUrl(fullUrl)
    val pathPairs = parseAiPath(normalizeParenthesesAI(parts.path))
    val gtin = pathPairs.firstOrNull { it.ai == "01" || it.ai == "02" }?.value

    val (cleanedParams, embedded) = extractEmbeddedAIs(parseQueryParams(parts.query))

    val specialIdentifiers = mutableListOf<SpecialIdentifier>()
    val regularQueryPairs = mutableListOf<AiPair>()
    var ai97: String? = null
    var ai98: String? = null

    embedded.forEach { (code, value) ->
        when (code) {
            "97" -> {
                ai97 = value
                specialIdentifiers.add(specialIdentifier(code, value, AiSource.QUERY))
            }

            "98" -> {
                ai98 = value
                specialIdentifiers.add(specialIdentifier(code, value, AiSource.QUERY))
            }
        }
    }

    cleanedParams.forEach { (key, value) ->
        when {
            key == "97" -> {
                ai97 = value
                specialIdentifiers.add(specialIdentifier(key, value, AiSource.QUERY))
            }

            key == "98" -> {
                val pieces = value.split('/')
                val head = pieces[0]
                ai98 = head
                specialIdentifiers.add(specialIdentifier(key, head, AiSource.QUERY))
                pieces.getOrNull(1)?.let { extra ->
                    if (extra.substringBefore('=') == "97") {
                        val nested = extra.substringAfter('=', "")
                        ai97 = nested
                        specialIdentifiers.add(specialIdentifier("97", nested, AiSource.QUERY))
                    }
                }
            }

            key == "96" || key == "99" ->
                specialIdentifiers.add(specialIdentifier(key, value, AiSource.QUERY))

            key.isNotEmpty() && key.all { it.isDigit() } ->
                regularQueryPairs.add(AiPair(key, value))
        }
    }

    val regularPathPairs = mutableListOf<AiPair>()
    pathPairs.forEach { pair ->
        if (pair.ai in SPECIAL_AIS) {
            specialIdentifiers.add(specialIdentifier(pair.ai, pair.value, AiSource.PATH))
            if (pair.ai == "97") ai97 = pair.value
            if (pair.ai == "98") ai98 = pair.value
        } else {
            regularPathPairs.add(pair)
        }
    }

    val identifiers = regularPathPairs.map { it.toIdentifier(AiSource.PATH) } +
        regularQueryPairs.map { it.toIdentifier(AiSource.QUERY) }

    val cleaned = removeAuthFromFullUrl(fullUrl)

    val data = ParsedData(
        domain = hostOf(parts.origin),
        path = parts.path,
        queryString = if (parts.query.isEmpty()) "" else "?${parts.query}",
        identifiers = identifiers,
        specialIdentifiers = specialIdentifiers,
        raw = buildRawDump(regularPathPairs, regularQueryPairs, specialIdentifiers, cleanedParams),
        timestamp = Clock.System.now().toString(),
    )

    return DigitalLinkScan(
        data = data,
        gtin = gtin
            ?: regularQueryPairs.firstOrNull { it.ai == "01" || it.ai == "02" }?.value
            ?: Regex("""/01/([^/?#]+)""").find(fullUrl.replace("\\/", "/"))?.groupValues?.getOrNull(1),
        // Path-carried 97/98 are only recovered by the cleaner, so fall back to it.
        ai97 = ai97 ?: cleaned.ai97,
        ai98 = ai98 ?: cleaned.ai98,
        cleanUrl = cleaned.barcodeData,
    )
}

private fun specialIdentifier(code: String, value: String, source: String) =
    SpecialIdentifier(code = code, name = aiName(code), value = value, source = source)

private fun AiPair.toIdentifier(source: String) = ApplicationIdentifier(
    code = ai,
    name = aiName(ai),
    value = value,
    category = aiCategory(ai),
    source = source,
)

private fun buildRawDump(
    pathPairs: List<AiPair>,
    queryPairs: List<AiPair>,
    special: List<SpecialIdentifier>,
    allQueryParams: List<Pair<String, String>>,
): JsonObject = buildJsonObject {
    putJsonArray("regularPathPairs") {
        pathPairs.forEach { add(buildJsonObject { put("ai", it.ai); put("value", it.value) }) }
    }
    putJsonArray("regularQueryPairs") {
        queryPairs.forEach { add(buildJsonObject { put("ai", it.ai); put("value", it.value) }) }
    }
    putJsonArray("specialIdentifiers") {
        special.forEach {
            add(
                buildJsonObject {
                    put("code", it.code)
                    put("name", it.name)
                    put("value", it.value)
                    put("source", it.source)
                },
            )
        }
    }
    putJsonObject("allQueryParams") {
        allQueryParams.forEach { (key, value) -> put(key, value) }
    }
}

// MARK: - Value formatting

private val MONTH_LABELS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** `YYMMDD` to `YYYY-MM-DD`, leaving anything else untouched. */
fun formatGs1Date(value: String): String {
    if (value.length != 6) return value
    return "20${value.substring(0, 2)}-${value.substring(2, 4)}-${value.substring(4, 6)}"
}

/** Renders an ISO timestamp as `18 Aug 2026`, matching the web resolver. */
fun formatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "N/A"
    val date = parseIsoDate(raw) ?: return raw
    return "${date.dayOfMonth} ${MONTH_LABELS[date.monthNumber - 1]} ${date.year}"
}

/** Accepts both `YYMMDD` GS1 values and ISO timestamps. */
fun formatScanDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    if (trimmed.length == 6 && trimmed.all { it.isDigit() }) return formatGs1Date(trimmed)
    return formatDate(trimmed)
}

private fun parseIsoDate(raw: String): LocalDate? = try {
    LocalDate.parse(raw.trim().take(10))
} catch (e: Exception) {
    null
}

private fun gs1LocalDate(value: String): LocalDate? {
    if (value.length != 6 || !value.all { it.isDigit() }) return null
    val year = 2000 + value.substring(0, 2).toInt()
    val month = value.substring(2, 4).toInt()
    val day = value.substring(4, 6).toInt()
    if (month !in 1..12) return null
    return try {
        // GS1 writes day 00 when only the month matters.
        if (day == 0) {
            LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        } else {
            LocalDate(year, month, day)
        }
    } catch (e: Exception) {
        null
    }
}

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun isExpired(value: String): Boolean {
    val expiry = gs1LocalDate(value) ?: return false
    return expiry < today()
}

fun isExpiringSoon(value: String): Boolean {
    val expiry = gs1LocalDate(value) ?: return false
    return expiry <= today().plus(6, DateTimeUnit.MONTH)
}

fun isNA(value: String?): Boolean {
    if (value.isNullOrBlank()) return true
    return value.trim().lowercase() == "n/a"
}

fun realValue(value: String?): String? = if (isNA(value)) null else value

private val CITY_ALIASES = mapOf(
    "new delhi" to "delhi",
    "delhi" to "delhi",
    "ncr" to "delhi",
    "delhi ncr" to "delhi",
    "south west delhi" to "delhi",
    "south delhi" to "delhi",
    "west delhi" to "delhi",
    "east delhi" to "delhi",
    "north delhi" to "delhi",
    "central delhi" to "delhi",
    "mumbai" to "mumbai",
    "bombay" to "mumbai",
    "chennai" to "chennai",
    "madras" to "chennai",
    "bengaluru" to "bangalore",
    "bangalore" to "bangalore",
    "kolkata" to "kolkata",
    "calcutta" to "kolkata",
)

private val REGION_ALIASES = mapOf(
    "dl" to "delhi",
    "delhi" to "delhi",
    "nct" to "delhi",
    "nct of delhi" to "delhi",
    "in" to "india",
    "ind" to "india",
    "india" to "india",
)

fun normalizeCity(city: String): String {
    if (city.isBlank()) return ""
    val key = city.lowercase().trim()
    return CITY_ALIASES[key] ?: key
}

fun normalizeRegion(value: String): String {
    if (value.isBlank()) return ""
    val key = value.lowercase().trim()
    return REGION_ALIASES[key] ?: normalizeCity(value)
}

/** Same metro / overlapping names count as a match (e.g. Delhi vs South West Delhi). */
fun citiesCompatible(a: String, b: String): Boolean {
    val na = normalizeCity(a)
    val nb = normalizeCity(b)
    if (na.isBlank() || nb.isBlank()) return false
    if (na == nb) return true
    return (na.length >= 4 && nb.contains(na)) || (nb.length >= 4 && na.contains(nb))
}

fun regionsCompatible(a: String, b: String): Boolean {
    val na = normalizeRegion(a)
    val nb = normalizeRegion(b)
    if (na.isBlank() || nb.isBlank()) return false
    return na == nb || citiesCompatible(a, b)
}

/**
 * Null when the expected place is unknown. True when the scan is in the same
 * city (and state/country when those are present).
 */
fun locationsMatch(
    userCity: String,
    userState: String,
    userCountry: String,
    expectedCity: String?,
    expectedState: String?,
    expectedCountry: String?,
): Boolean? {
    val city = expectedCity?.takeIf { it.isNotBlank() }
    val state = expectedState?.takeIf { it.isNotBlank() }
    val country = expectedCountry?.takeIf { it.isNotBlank() }
    if (city == null && state == null) return null
    val cityOk = city == null || citiesCompatible(userCity, city)
    val stateOk = state == null || regionsCompatible(userState, state) ||
        (city != null && citiesCompatible(userCity, city))
    val countryOk = country == null || regionsCompatible(userCountry, country)
    return cityOk && stateOk && countryOk
}

fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    fun toRad(deg: Double) = deg * kotlin.math.PI / 180.0
    val r = 6371.0
    val p1 = toRad(lat1)
    val p2 = toRad(lat2)
    val dp = toRad(lat2 - lat1)
    val dl = toRad(lon2 - lon1)
    val a = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) +
        kotlin.math.cos(p1) * kotlin.math.cos(p2) *
        kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
    return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

/** Date AIs are shown in `YYYY-MM-DD` form rather than raw `YYMMDD`. */
fun isDateAi(code: String): Boolean = code in setOf("11", "13", "15", "17")
