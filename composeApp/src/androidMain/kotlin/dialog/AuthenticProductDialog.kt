package dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject


// ─────────────────────────────────────────────────────────────────────────────
// 1.  DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents one entry inside gs1_data.
 * e.g. AI = "01", name = "GTIN", value = "08906038070010"
 */
data class Gs1Field(
    val ai: String,       // key from JSON object, e.g. "01", "10", "21"
    val name: String,     // human-readable label from "name" field
    val value: String     // actual value from "value" field
)

/** Top-level scan result mapped from the SDK / API response */
data class ScanResult(
    val barcodeData: String,
    val gs1Fields: List<Gs1Field>,
    val encryptedText: String,
    val quality: String,
    /** Exact string read from the barcode (scanResult.first). */
    val rawBarcode: String = barcodeData,
    /** Human-readable symbology, e.g. "QR Code", "Code 128". */
    val barcodeType: String = "",
)

fun inferBarcodeTypeFromRaw(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Unknown"
    return when {
        trimmed.startsWith("http", ignoreCase = true) -> "QR Code"
        trimmed.contains("]Q3", ignoreCase = true) -> "QR Code"
        trimmed.contains("]d2", ignoreCase = true) -> "Data Matrix"
        trimmed.contains("]C1", ignoreCase = true) -> "Code 128"
        trimmed.contains('\u001D') -> "GS1-128"
        trimmed.contains("(01)") -> "GS1-128"
        trimmed.matches(Regex("""^\d{13}$""")) -> "EAN-13"
        trimmed.matches(Regex("""^\d{8}$""")) -> "EAN-8"
        trimmed.matches(Regex("""^\d{12}$""")) -> "UPC-A"
        else -> "Barcode"
    }
}

private fun extractBarcodeTypeFromJson(first: JSONObject): String {
    return first.optString("barcode_type")
        .ifBlank { first.optString("type") }
        .ifBlank { first.optString("symbology") }
        .ifBlank { first.optString("format") }
}

private fun ScanResult.enriched(raw: String, first: JSONObject? = null): ScanResult {
    val rawValue = raw.ifBlank { rawBarcode }.ifBlank { barcodeData }
    val type = barcodeType.ifBlank {
        first?.let { extractBarcodeTypeFromJson(it) }.orEmpty()
    }.ifBlank { inferBarcodeTypeFromRaw(rawValue) }
    return copy(
        rawBarcode = rawValue,
        barcodeData = barcodeData.ifBlank { rawValue },
        barcodeType = type,
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// 2.  JSON PARSER
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses the raw JSON string returned by the scanning API.
 *
 * The API returns a JSON *array*; we read the first element.
 * gs1_data is a dynamic JSON object – keys are AI numbers ("01","10", …).
 *
 * Returns null if parsing fails for any reason.
 *
 * Usage:
 *   val result: ScanResult? = parseScanResponse(jsonString)
 *   if (result != null) { /* show dialog */ }
 */
/**
 * Maps the scanner SDK response into a [ScanResult] for the dialog.
 *
 * Two shapes from the SDK are supported — we only deserialize, never
 * re-interpret. Anything the SDK didn't ship (e.g. `quality`,
 * `encrypted_text`) stays at its SDK-provided value.
 *
 *  Legacy:
 *    [{ "barcode_data": "...", "gs1_data": { "01": {...} }, "encrypted_text": "...", "quality": "Real" }]
 *
 *  Current (flat AI list):
 *    [{"ai":"01","description":"GTIN","value":"..."}, {"ai":"10","description":"Batch/Lot Number","value":"..."}, ...]
 *
 * [rawData] is the raw scanned string from `scanResult.first` and is only
 * used as the `barcode_data` for the flat-list shape (which doesn't carry
 * one of its own). No fields are derived from it.
 */
private fun normalizeSdkJsonPayload(jsonString: String, rawData: String?): JSONArray? {
    val trimmed = jsonString.trim()
    if (trimmed.isEmpty() || trimmed == "null") return null
    return try {
        when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONArray().put(JSONObject(trimmed))
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun parseGs1FieldsFromObject(gs1Object: JSONObject): List<Gs1Field> {
    val gs1Fields = mutableListOf<Gs1Field>()
    val keys = gs1Object.keys()
    while (keys.hasNext()) {
        val ai = keys.next()
        val fieldObj = gs1Object.optJSONObject(ai) ?: continue
        gs1Fields.add(
            Gs1Field(
                ai = ai,
                name = fieldObj.optString("name")
                    .ifBlank { fieldObj.optString("description", ai) },
                value = fieldObj.optString("value", "—"),
            )
        )
    }
    return gs1Fields
}

private fun parseFlatAiArray(array: JSONArray): List<Gs1Field> {
    val gs1Fields = mutableListOf<Gs1Field>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        if (!item.has("ai") || !item.has("value")) continue
        val ai = item.optString("ai")
        if (ai.isBlank()) continue
        gs1Fields.add(
            Gs1Field(
                ai = ai,
                name = item.optString("description")
                    .ifBlank { item.optString("name", ai) },
                value = item.optString("value", "—"),
            )
        )
    }
    return gs1Fields
}

/** GS1 path segments from a digital link, e.g. `/01/gtin/10/batch/21/serial`. */
private fun parseGs1FieldsFromDigitalLink(raw: String): List<Gs1Field> {
    if (raw.isBlank()) return emptyList()
    val fields = mutableListOf<Gs1Field>()
    Regex("""/01/([^/?#]+)""").find(raw)?.groupValues?.getOrNull(1)?.let {
        fields.add(Gs1Field("01", "GTIN", it))
    }
    Regex("""/10/([^/?#]+)""").find(raw)?.groupValues?.getOrNull(1)?.let {
        fields.add(Gs1Field("10", "Batch/Lot Number", it))
    }
    Regex("""/21/([^/?#]+)""").find(raw)?.groupValues?.getOrNull(1)?.let {
        fields.add(Gs1Field("21", "Serial Number", it))
    }
    Regex("""\(01\)([^()]+)""").find(raw)?.groupValues?.getOrNull(1)?.trim()?.let {
        if (fields.none { f -> f.ai == "01" }) fields.add(Gs1Field("01", "GTIN", it))
    }
    Regex("""\(10\)([^()]+)""").find(raw)?.groupValues?.getOrNull(1)?.trim()?.let {
        if (fields.none { f -> f.ai == "10" }) fields.add(Gs1Field("10", "Batch/Lot Number", it))
    }
    Regex("""\(21\)([^()]+)""").find(raw)?.groupValues?.getOrNull(1)?.trim()?.let {
        if (fields.none { f -> f.ai == "21" }) fields.add(Gs1Field("21", "Serial Number", it))
    }
    return fields
}

fun parseScanResponse(jsonString: String, rawData: String? = null): ScanResult? {
    val raw = rawData?.trim().orEmpty()
    return try {
        val array = normalizeSdkJsonPayload(jsonString, raw) ?: return fallbackScanResult(raw)

        if (array.length() == 0) return fallbackScanResult(raw)

        val first = array.optJSONObject(0) ?: return fallbackScanResult(raw)
        val isFlatAiList = first.has("ai") && first.has("value")

        if (isFlatAiList) {
            var gs1Fields = parseFlatAiArray(array)
            val barcodeData = raw.ifBlank {
                first.optString("barcode_data")
                    .ifBlank { first.optString("data") }
            }
            if (gs1Fields.isEmpty()) {
                gs1Fields = parseGs1FieldsFromDigitalLink(barcodeData)
            }
            return ScanResult(
                barcodeData = barcodeData,
                gs1Fields = gs1Fields,
                encryptedText = "",
                quality = "",
                rawBarcode = raw.ifBlank { barcodeData },
            ).enriched(raw, first)
        }

        // Legacy / auth API: { barcode_data, gs1_data, encrypted_text, quality }
        val barcodeData = first.optString("barcode_data", "")
            .ifBlank { raw }
        val encryptedText = first.optString("encrypted_text", "")
        val quality = first.optString("quality", "")
        val barcodeType = extractBarcodeTypeFromJson(first)

        var gs1Fields = parseGs1FieldsFromObject(
            first.optJSONObject("gs1_data") ?: JSONObject()
        )
        if (gs1Fields.isEmpty()) {
            gs1Fields = parseFlatAiArray(array)
        }
        if (gs1Fields.isEmpty()) {
            gs1Fields = parseGs1FieldsFromDigitalLink(barcodeData.ifBlank { raw })
        }

        return ScanResult(
            barcodeData = barcodeData.ifBlank { raw },
            gs1Fields = gs1Fields,
            encryptedText = encryptedText,
            quality = quality,
            rawBarcode = raw.ifBlank { barcodeData },
            barcodeType = barcodeType,
        ).enriched(raw, first)
    } catch (e: Exception) {
        e.printStackTrace()
        fallbackScanResult(raw)
    }
}

private fun fallbackScanResult(raw: String): ScanResult? {
    if (raw.isBlank()) return null
    val gs1Fields = parseGs1FieldsFromDigitalLink(raw)
    return ScanResult(
        barcodeData = raw,
        gs1Fields = gs1Fields,
        encryptedText = "",
        quality = "",
        rawBarcode = raw,
    ).enriched(raw)
}


// ─────────────────────────────────────────────────────────────────────────────
// 3.  COLORS
// ─────────────────────────────────────────────────────────────────────────────

private val GreenPrimary  = Color(0xFF2E7D32)
private val GreenLight    = Color(0xFFE8F5E9)
private val GreenBadge    = Color(0xFF4CAF50)
private val RedPrimary    = Color(0xFFC62828)
private val RedLight      = Color(0xFFFFEBEE)
private val BluePrimary   = Color(0xFF1A6BF0)
private val BlueLinkBg    = Color(0xFFE8F0FE)
private val BlueLinkText  = Color(0xFF1A6BF0)
private val LabelGray     = Color(0xFF9E9E9E)
private val ChipBlue      = Color(0xFF1565C0)
private val ChipBlueBg    = Color(0xFFE3F2FD)
private val DividerColor  = Color(0xFFEEEEEE)
private val CardBg        = Color(0xFFF7F8FA)

private val HTTP_URL = Regex(
    """https?://[^\s"'<>\\]+""",
    RegexOption.IGNORE_CASE,
)

/** First http(s) URL in a scanned value, used to open the browser from the dialog. */
private fun findHttpUrl(vararg candidates: String): String? =
    candidates.firstNotNullOfOrNull { candidate ->
        HTTP_URL.find(candidate.trim())?.value?.trim()?.trimEnd(',', '.', ')', ']')
    }?.takeIf { it.isNotEmpty() }


// ─────────────────────────────────────────────────────────────────────────────
// 4.  DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuthenticProductDialog(
    raw: String,
    result: ScanResult,
    onDismiss: () -> Unit = {},
    onContinue: () -> Unit = {},
    onLinkClick: (String) -> Unit = {}
) {
    val hasQuality = result.quality.isNotBlank()
    val isReal = result.quality.equals("Real", ignoreCase = true)
    val isFake = result.quality.equals("Fake", ignoreCase = true)

    val headerColor = when {
        !hasQuality -> BluePrimary
        isReal -> GreenPrimary
        else -> RedPrimary
    }
    val headerBg = when {
        !hasQuality -> BlueLinkBg
        isReal -> GreenLight
        else -> RedLight
    }
    val badgeDotColor = when {
        !hasQuality -> BluePrimary
        isReal -> GreenBadge
        else -> RedPrimary
    }
    val badgeLabel = when {
        !hasQuality -> "Scanned"
        isReal -> "Verified"
        isFake -> "Not Verified"
        else -> result.quality
    }
    val titleText = when {
        !hasQuality -> "Scan Result"
        isReal -> "Ratifye'd"
        isFake -> "Counterfeit Detected"
        else -> "Verification Result"
    }
    val subtitleText = when {
        !hasQuality -> "Parsed barcode data from scan."
        isReal -> "Cryptographically verified."
        isFake -> "This product may be fake."
        else -> result.quality
    }

    val rawDataText = raw.ifBlank { result.rawBarcode }.ifBlank { result.barcodeData }
    val httpUrl = findHttpUrl(result.barcodeData, result.rawBarcode, raw, rawDataText)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(550.dp)
                .padding(horizontal = 16.dp)
        ) {
            Surface(
                shape         = RoundedCornerShape(24.dp),
                color         = Color.White,
                shadowElevation = 16.dp,
                modifier      = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())   // scroll when many gs1 fields
                ) {

                    // ── Drag handle ──────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDDDDDD))
                        )
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {

                        // ── Header row ───────────────────────────────────────
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(headerBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        !hasQuality || isReal -> Icons.Filled.CheckCircle
                                        else -> Icons.Filled.Warning
                                    },
                                    contentDescription = badgeLabel,
                                    tint            = headerColor,
                                    modifier        = Modifier.size(30.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text       = titleText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 14.sp,
                                    color      = headerColor
                                )
                                Text(
                                    text     = subtitleText,
                                    fontSize = 12.sp,
                                    color    = headerColor.copy(alpha = 0.75f)
                                )
                            }

                            // Status badge
                            Surface(
                                shape  = RoundedCornerShape(20.dp),
                                color  = headerBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, badgeDotColor.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(badgeDotColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text       = badgeLabel,
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = headerColor
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color    = DividerColor,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )

                        ScanDetailsSection(
                            rawData = rawDataText,
                            barcodeType = result.barcodeType,
                            linkUrl = httpUrl,
                            onLinkClick = onLinkClick,
                        )

                        if (result.gs1Fields.isNotEmpty()) {
                            HorizontalDivider(
                                color = DividerColor,
                                modifier = Modifier.padding(vertical = 18.dp),
                            )
                            Text(
                                text = "PARSED GS1 VALUES",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                                color = LabelGray,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            DynamicGs1Grid(fields = result.gs1Fields)
                        }

                        if (httpUrl != null) {
                            HorizontalDivider(
                                color = DividerColor,
                                modifier = Modifier.padding(vertical = 18.dp),
                            )
                            Text(
                                text = "LINK",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                                color = LabelGray,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BlueLinkBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLinkClick(httpUrl) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(BlueLinkText.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(text = "🔗", fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = httpUrl,
                                        color = BlueLinkText,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.OpenInNew,
                                        contentDescription = "Open link",
                                        tint = BlueLinkText,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── Continue button ───────────────────────────────────
                        Button(
                            onClick  = onContinue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape  = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    !hasQuality -> BluePrimary
                                    isReal -> BluePrimary
                                    else -> RedPrimary
                                }
                            )
                        ) {
                            Text(
                                text       = "Continue",
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}


@Composable
private fun ScanDetailsSection(
    rawData: String,
    barcodeType: String,
    linkUrl: String? = null,
    onLinkClick: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SCAN DATA",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = LabelGray,
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ChipBlueBg,
        ) {
            Text(
                text = barcodeType.ifBlank { "Unknown" },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ChipBlue,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Raw data",
                fontSize = 12.sp,
                color = LabelGray,
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (linkUrl != null) Modifier.clickable { onLinkClick(linkUrl) }
                        else Modifier,
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = rawData.ifBlank { "—" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (linkUrl != null) BlueLinkText else Color(0xFF1A1A1A),
                        textDecoration = if (linkUrl != null) {
                            TextDecoration.Underline
                        } else {
                            TextDecoration.None
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (linkUrl != null) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = "Open link",
                            tint = BlueLinkText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5.  DYNAMIC GRID  –  pairs fields into rows of 2
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders gs1 fields in a 2-column grid.
 *
 * Logic:
 *  - Chunk the list into groups of 2.
 *  - Every full pair → Row with two equal-weight cards.
 *  - If the total count is odd, the last card spans half the width (left-aligned).
 */
@Composable
private fun DynamicGs1Grid(fields: List<Gs1Field>) {
    if (fields.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fields.chunked(2).forEach { row ->
            if (row.size == 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoCard(field = row[0], modifier = Modifier.weight(1f))
                    InfoCard(field = row[1], modifier = Modifier.weight(1f))
                }
            } else {
                // Single remaining card – half width, left-aligned
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoCard(
                        field    = row[0],
                        modifier = Modifier.fillMaxWidth(0.48f)
                    )
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// 6.  INFO CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(
    field: Gs1Field,
    modifier: Modifier = Modifier
) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = CardBg,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // AI chip
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ChipBlueBg
            ) {
                Text(
                    text       = "AI ${field.ai}",
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color      = ChipBlue,
                    modifier   = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text     = field.name,
                fontSize = 12.sp,
                color    = LabelGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text       = field.value,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF1A1A1A),
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// 7.  SCREEN WRAPPER  –  plug-in point for real API data
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProductVerificationScreen(rawJson: String) {
    var showDialog by remember { mutableStateOf(true) }

    // Parse once; re-parse only if rawJson changes
    val result = remember(rawJson) { parseScanResponse(rawJson) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        when {
            !showDialog  -> { /* dialog dismissed */ }
            result == null -> {
                Text("Failed to parse response", color = Color.White)
            }
            else -> {
                AuthenticProductDialog(
                    raw = "",
                    result     = result,
                    onDismiss  = { showDialog = false },
                    onContinue = { showDialog = false },
                    onLinkClick = { url ->
                        // Open URL:
                        // val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        // context.startActivity(intent)
                    }
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// 8.  PREVIEW
// ─────────────────────────────────────────────────────────────────────────────

private val SAMPLE_JSON = """
[
  {
    "barcode_data": "https://dl.ratifye.ai/01/08906038070010/10/AMI432/21/123121?11=240531&17=260430",
    "gs1_data": {
      "01": { "name": "GTIN",            "value": "08906038070010" },
      "10": { "name": "Batch/Lot",       "value": "AMI432"        },
      "21": { "name": "Serial",          "value": "123121"        },
      "11": { "name": "Production Date", "value": "240531"        },
      "17": { "name": "Expiry",          "value": "260430"        }
    },
    "encrypted_text": "ZCMFHWO6C2WS4Q====",
    "quality": "Real"
  }
]
""".trimIndent()

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
fun AuthenticProductDialogPreview() {
    MaterialTheme {
        ProductVerificationScreen(rawJson = SAMPLE_JSON)
    }
}