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

/** Top-level scan result mapped from the API response */
data class ScanResult(
    val barcodeData: String,
    val gs1Fields: List<Gs1Field>,   // dynamic – ordered as received from JSON
    val encryptedText: String,
    val quality: String              // "Real" | "Fake" | …
)


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
fun parseScanResponse(jsonString: String): ScanResult? {
    return try {
        val array = JSONArray(jsonString)
        if (array.length() == 0) return null

        val obj: JSONObject = array.getJSONObject(0)

        val barcodeData   = obj.optString("barcode_data", "")
        val encryptedText = obj.optString("encrypted_text", "")
        val quality       = obj.optString("quality", "Unknown")

        // gs1_data is a JSON object whose keys are AI numbers
        val gs1Object: JSONObject = obj.optJSONObject("gs1_data") ?: JSONObject()
        val gs1Fields = mutableListOf<Gs1Field>()

        val keys = gs1Object.keys()
        while (keys.hasNext()) {
            val ai = keys.next()
            val fieldObj: JSONObject = gs1Object.optJSONObject(ai) ?: continue
            gs1Fields.add(
                Gs1Field(
                    ai    = ai,
                    name  = fieldObj.optString("name", ai),
                    value = fieldObj.optString("value", "—")
                )
            )
        }

        ScanResult(
            barcodeData   = barcodeData,
            gs1Fields     = gs1Fields,
            encryptedText = encryptedText,
            quality       = quality
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
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


// ─────────────────────────────────────────────────────────────────────────────
// 4.  DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuthenticProductDialog(
    result: ScanResult,
    onDismiss: () -> Unit = {},
    onContinue: () -> Unit = {},
    onLinkClick: (String) -> Unit = {}
) {
    val isReal        = result.quality.equals("Real", ignoreCase = true)
    val headerColor   = if (isReal) GreenPrimary else RedPrimary
    val headerBg      = if (isReal) GreenLight   else RedLight
    val badgeDotColor = if (isReal) GreenBadge   else RedPrimary
    val badgeLabel    = if (isReal) "Verified"   else "Not Verified"
    val titleText     = if (isReal) "Authentic Product"    else "Counterfeit Detected"
    val subtitleText  = if (isReal) "Verified as genuine." else "This product may be fake."

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
                                    imageVector     = if (isReal) Icons.Filled.CheckCircle
                                    else Icons.Filled.Warning,
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

                        // ── Product Information ───────────────────────────────
                        Text(
                            text       = "PRODUCT INFORMATION",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            color      = LabelGray,
                            modifier   = Modifier.padding(bottom = 12.dp)
                        )

                        // Dynamic 2-column grid built from whatever gs1Fields arrives
                        DynamicGs1Grid(fields = result.gs1Fields)

                        HorizontalDivider(
                            color    = DividerColor,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )

                        // ── Digital Link ─────────────────────────────────────
                        Text(
                            text       = "DIGITAL LINK",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            color      = LabelGray,
                            modifier   = Modifier.padding(bottom = 10.dp)
                        )

                        Surface(
                            shape    = RoundedCornerShape(12.dp),
                            color    = BlueLinkBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLinkClick(result.barcodeData) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(BlueLinkText.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🔗", fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text     = result.barcodeData,
                                    color    = BlueLinkText,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector        = Icons.Filled.OpenInNew,
                                    contentDescription = "Open link",
                                    tint               = BlueLinkText,
                                    modifier           = Modifier.size(18.dp)
                                )
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
                                containerColor = if (isReal) BluePrimary else RedPrimary
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