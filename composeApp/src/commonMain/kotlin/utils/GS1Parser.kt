package utils

/**
 * GS1 element-string parser for warehouse + pharma traceability.
 * Supports parenthetical `(01)…(10)…` and raw FNC1 (`\u001D`) element strings.
 */
data class Gs1ParseResult(
    val raw: String,
    val gtin: String? = null,
    val batch: String? = null,
    val expiry: String? = null,
    val serial: String? = null,
    val sscc: String? = null,
    val productionDate: String? = null,
    val applicationIdentifiers: Map<String, String> = emptyMap(),
) {
    val hasProductIdentity: Boolean
        get() = !gtin.isNullOrBlank() || !serial.isNullOrBlank()

    val hasLogisticsIdentity: Boolean
        get() = !sscc.isNullOrBlank()
}

private val ParenAiPattern = Regex("""\((\d{2,4})\)([^()]+)""")
private val DigitalLinkGtinPattern = Regex("""/01/([^/?#]+)""")

/** Fixed-length AIs in GS1 general spec (subset used in pharma). */
private val FixedLengthAis = mapOf(
    "00" to 18, // SSCC
    "01" to 14, // GTIN
    "02" to 14,
    "11" to 6,
    "12" to 6,
    "13" to 6,
    "15" to 6,
    "16" to 6,
    "17" to 6,
)

object Gs1Parser {

    fun parse(raw: String): Gs1ParseResult {
        val trimmed = raw.trim().replace("\u001D", "<GS>")
        if (trimmed.isBlank()) return Gs1ParseResult(raw = raw)

        val ais = mutableMapOf<String, String>()
        parseParenthetical(trimmed, ais)
        if (ais.isEmpty()) parseFnc1ElementString(trimmed.replace("<GS>", "\u001D"), ais)
        parseDigitalLink(trimmed, ais)

        return Gs1ParseResult(
            raw = raw,
            gtin = ais["01"],
            batch = ais["10"],
            expiry = ais["17"],
            serial = ais["21"],
            sscc = ais["00"],
            productionDate = ais["11"],
            applicationIdentifiers = ais.toMap(),
        )
    }

    fun extractGtin(raw: String): String? = parse(raw).gtin
    fun extractBatch(raw: String): String? = parse(raw).batch
    fun extractSerial(raw: String): String? = parse(raw).serial
    fun extractSscc(raw: String): String? = parse(raw).sscc
    fun extractExpiry(raw: String): String? = parse(raw).expiry

    private fun parseParenthetical(raw: String, out: MutableMap<String, String>) {
        ParenAiPattern.findAll(raw).forEach { match ->
            val ai = match.groupValues[1]
            val value = match.groupValues[2].trim()
            if (value.isNotEmpty()) out[ai] = value
        }
    }

    private fun parseDigitalLink(raw: String, out: MutableMap<String, String>) {
        if (out.containsKey("01")) return
        DigitalLinkGtinPattern.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            out["01"] = it
        }
    }

    private fun parseFnc1ElementString(raw: String, out: MutableMap<String, String>) {
        var index = 0
        while (index < raw.length) {
            val ai = detectAi(raw, index) ?: break
            val fixedLen = FixedLengthAis[ai]
            val value = if (fixedLen != null) {
                val end = index + ai.length + fixedLen
                if (end > raw.length) break
                raw.substring(index + ai.length, end).also { index = end }
            } else {
                val start = index + ai.length
                val gs = raw.indexOf('\u001D', start)
                val end = if (gs >= 0) gs else raw.length
                if (start >= end) break
                raw.substring(start, end).also { index = if (gs >= 0) gs + 1 else raw.length }
            }
            if (value.isNotBlank()) out[ai] = value.trim()
        }
    }

    private fun detectAi(raw: String, index: Int): String? {
        if (index + 2 > raw.length) return null
        val two = raw.substring(index, index + 2)
        if (FixedLengthAis.containsKey(two) || two in variableLengthAis) return two
        if (index + 3 <= raw.length) {
            val three = raw.substring(index, index + 3)
            if (three in variableLengthAis) return three
        }
        if (index + 4 <= raw.length) {
            val four = raw.substring(index, index + 4)
            if (four in variableLengthAis) return four
        }
        return null
    }

    private val variableLengthAis = setOf("10", "21", "22", "240", "241", "242", "243")
}
