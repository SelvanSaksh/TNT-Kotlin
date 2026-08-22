package features.app

import core.network.models.BarcodeLookupQuery
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import resolver.stripAuthAis97And98

private fun JsonObject.nonBlankString(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * Primary label for dashboard / history rows. Handles nested `details.barcode`
 * and flat audit-log fields (`barcode_data`, `epc_id`).
 */
fun JsonObject.barcodeLogDisplayTitle(): String {
    val raw = this["details"]?.jsonObject?.nonBlankString("barcode")
        ?: nonBlankString("barcode_data")
        ?: nonBlankString("epc_id")
        ?: nonBlankString("data")
        ?: return "Unknown"
    return stripAuthAis97And98(raw).ifBlank { raw }
}

/**
 * Status / action line. Handles nested `details.status` and flat `auth_result`, `event_type`.
 */
fun JsonObject.barcodeLogDisplayAction(fallback: String = "Processed"): String {
    this["details"]?.jsonObject?.nonBlankString("status")?.let { return it }
    nonBlankString("auth_result")?.let { return it }
    nonBlankString("event_type")?.let { return it }
    return fallback
}

private val Gs1DlGtinPath = Regex("""/01/([^/?#]+)""")
private val Gs1DlBatchPath = Regex("""/10/([^/?#]+)""")
private val Gs1DlSerialPath = Regex("""/21/([^/?#]+)""")
private val ParenGtin = Regex("""\(01\)([^()]+)""")
private val ParenBatch = Regex("""\(10\)([^()]+)""")
private val ParenSerial = Regex("""\(21\)([^()]+)""")

private fun extractGtinFromUrlOrParen(raw: String): String {
    if (raw.isBlank()) return ""
    Gs1DlGtinPath.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    ParenGtin.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return ""
}

private fun extractBatchFromUrlOrParen(raw: String): String {
    if (raw.isBlank()) return ""
    Gs1DlBatchPath.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    ParenBatch.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return ""
}

private fun extractSerialFromUrlOrParen(raw: String): String {
    if (raw.isBlank()) return ""
    Gs1DlSerialPath.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    ParenSerial.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return ""
}

/**
 * Builds lookup query from a history / dashboard log row (`JsonObject`).
 * Fills [BarcodeLookupQuery.gtin], [batch], [serial] from JSON fields and GS1 digital link / element string.
 */
fun JsonObject.barcodeLogLookupParams(): BarcodeLookupQuery {
    val barcodeData = nonBlankString("barcode_data").orEmpty()
    val urlGtin = extractGtinFromUrlOrParen(barcodeData)
    val urlBatch = extractBatchFromUrlOrParen(barcodeData)
    val urlSerial = extractSerialFromUrlOrParen(barcodeData)

    val gtin = nonBlankString("gtin")?.trim().orEmpty()
        .ifBlank { nonBlankString("epc_id")?.trim().orEmpty() }
        .ifBlank { urlGtin }

    val batch = nonBlankString("batch")?.trim().orEmpty().ifBlank { urlBatch }
    val serial = nonBlankString("serial")?.trim().orEmpty().ifBlank { urlSerial }

    val legacyKey = barcodeLogLookupKey()
    return BarcodeLookupQuery(
        gtin = gtin,
        batch = batch,
        serial = serial,
        legacyKey = legacyKey,
    )
}

/**
 * Value for `/companies/barcode/logs/lookup?key=` — GTIN / EPC, batch, or other key the API accepts.
 */
fun JsonObject.barcodeLogLookupKey(): String {
    nonBlankString("epc_id")?.let { return it.trim() }
    nonBlankString("gtin")?.let { return it.trim() }
    val barcodeData = nonBlankString("barcode_data").orEmpty()
    Gs1DlGtinPath.find(barcodeData)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    nonBlankString("batch")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    nonBlankString("serial")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    val title = barcodeLogDisplayTitle().trim()
    if (title.matches(Regex("""\d{8,14}"""))) return title
    return title
}
