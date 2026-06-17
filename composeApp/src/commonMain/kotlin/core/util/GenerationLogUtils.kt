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

/**
 * Mints a unique-per-generation event id, e.g. `evt_android_1746780600000_4271`.
 * The shape mirrors the API example (`evt_custom_generation_0001`).
 */
fun newGenerationEventId(prefix: String = "evt_android"): String {
    val ts = Clock.System.now().toEpochMilliseconds()
    val rnd = Random.nextInt(1000, 9999)
    return "${prefix}_${ts}_$rnd"
}
