package core.util

import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Helpers for building [core.network.models.GenerationLogRequest] payloads
 * — extracting GS1 application identifier values from a generated barcode
 * string and minting a unique generation event id.
 */

private val Gs1ParenAiPattern = Regex("""\((\d{2,4})\)([^()]+)""")

/**
 * Extracts the value for a single GS1 Application Identifier from a
 * parenthesized GS1 element string such as
 * `(01)08906038070010(10)BATCH001(21)SN000001`.
 *
 * Returns an empty string when the AI is not present or when the input
 * uses the un-parenthesized FNC1 form (since reliable parsing of that
 * shape requires the full AI length table).
 */
fun extractGs1AiValue(gs1: String, ai: String): String {
    if (gs1.isBlank()) return ""
    return Gs1ParenAiPattern.findAll(gs1)
        .firstOrNull { it.groupValues[1] == ai }
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()
        .orEmpty()
}

/** Convenience accessor — GS1 AI 21 is "Serial Number". */
fun extractGs1Serial(gs1: String): String = extractGs1AiValue(gs1, "21")

/** Convenience accessor — GS1 AI 10 is "Batch / Lot Number". */
fun extractGs1Batch(gs1: String): String = extractGs1AiValue(gs1, "10")

/** Convenience accessor — GS1 AI 01 is "GTIN". Also parses `/01/<gtin>` digital links. */
fun extractGs1Gtin(gs1: String): String {
    val fromAi = extractGs1AiValue(gs1, "01")
    if (fromAi.isNotBlank()) return fromAi
    return Regex("""/01/([^/?#]+)""")
        .find(gs1)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
}

fun normalizeGtin(gtin: String): String {
    val digits = gtin.filter { it.isDigit() }
    return if (digits.length in 8..13) digits.padStart(14, '0') else digits
}

/** True when the scan carries GS1 AIs (GTIN, Digital Link, element string, or GS1-128). */
fun isGs1Barcode(
    gtin: String = "",
    serial: String = "",
    batch: String = "",
    barcodeType: String = "",
    barcodeData: String = "",
): Boolean {
    if (gtin.filter { it.isDigit() }.length in 8..14) return true
    val type = barcodeType.uppercase().replace("-", "").replace(" ", "")
    if ("GS1" in type) return true
    val data = barcodeData
    if (data.contains("(01)") || data.contains("/01/") ||
        data.contains("dl.ratifye.ai", ignoreCase = true) ||
        data.contains("id.gs1.org", ignoreCase = true)
    ) return true
    if (data.contains('\u001D')) return true
    return serial.isNotBlank() && batch.isNotBlank() &&
        data.any { it.isDigit() }
}

/**
 * EPC SGTIN-96 URI, e.g. `urn:epc:id:sgtin:8901234.567890.123456`.
 *
 * Company prefix is 7 digits for GS1 India (`890…`); otherwise 7 as well so
 * the item reference stays 5–6 digits after dropping the GTIN check digit.
 */
fun sgtinEpcUrn(gtin: String, serial: String): String {
    val digits = gtin.filter { it.isDigit() }
    if (digits.isEmpty()) return serial.ifBlank { gtin }
    val gtin14 = digits.padStart(14, '0').takeLast(14)
    val body = gtin14.drop(1).dropLast(1)
    val gcpLen = if (body.startsWith("890")) 7 else 7
    val gcp = body.take(gcpLen)
    val itemRef = body.drop(gcpLen)
    val ser = serial.trim().ifBlank { "0" }
    return "urn:epc:id:sgtin:$gcp.$itemRef.$ser"
}

/**
 * Mints a unique-per-generation event id, e.g. `evt_android_1746780600000_4271`.
 * The shape mirrors the API example (`evt_custom_generation_0001`).
 */
fun newGenerationEventId(prefix: String = "evt_android"): String {
    val ts = Clock.System.now().toEpochMilliseconds()
    val rnd = Random.nextInt(1000, 9999)
    return "${prefix}_${ts}_$rnd"
}
