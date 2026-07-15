package features.app.warehouse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utils.Gs1Parser

/** Line item shown on warehouse batch scan / verification screens (aligned with iOS scan models). */
data class BatchScanLineItem(
    val id: Int,
    val name: String,
    val sku: String = "",
    val gtin: String = "",
    val batch: String,
    val maxQuantity: Int,
)

data class ScanCandidateInfo(
    val raw: String,
    val values: Set<String>,
    val batch: String? = null,
    val gtin: String? = null,
    val sku: String? = null,
)

@Serializable
private data class ScannedProductJson(
    val batch: String? = null,
    val gtin: String? = null,
    val sku: String? = null,
)

private val scanJson = Json { ignoreUnknownKeys = true }

fun productMatchesScan(payload: String, expectedGtin: String, expectedBatch: String): Boolean {
    if (!gtinMatchesScan(payload, expectedGtin)) return false
    val expectedBatchNorm = normalizeBatch(expectedBatch)
    if (expectedBatchNorm.isEmpty()) return true
    return batchMatchesScan(payload, expectedBatch)
}

fun batchMatchesScan(payload: String, expectedBatch: String): Boolean {
    val expected = normalizeBatch(expectedBatch)
    if (expected.isEmpty()) return false
    val candidates = scanCandidatesFromPayload(payload)
    if (candidates.values.any { normalizeBatch(it) == expected }) return true
    val plain = normalizeBatch(payload)
    return plain.isNotEmpty() && plain == expected
}

fun gtinMatchesScan(payload: String, expectedGtin: String): Boolean {
    val expected = normalizeGtinDigits(expectedGtin)
    if (expected.isEmpty()) return false
    val candidates = scanCandidatesFromPayload(payload, expectedGtin)
    val scanned = candidates.gtin?.let(::normalizeGtinDigits)
        ?: candidates.values.map { normalizeGtinDigits(it) }.firstOrNull { it.isNotEmpty() }
    return scanned != null && gtinEquivalent(scanned, expected)
}

fun normalizeBatch(raw: String): String = raw.trim().uppercase()

fun normalizeGtinDigits(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return if (digits.length >= 14) digits.take(14) else digits.padStart(14, '0')
}

private fun gtinEquivalent(a: String, b: String): Boolean {
    val na = normalizeGtinDigits(a)
    val nb = normalizeGtinDigits(b)
    if (na == nb) return true
    val ta = na.trimStart('0')
    val tb = nb.trimStart('0')
    return ta.isNotEmpty() && ta == tb
}

/** Parses scan payload and checks batch / GTIN / SKU (same rules as iOS `PickerScanFullScreenView`). */
fun validateWarehouseScan(
    payload: String,
    expectedBatch: String,
    expectedGtin: String,
    expectedSku: String,
): Pair<Boolean, ScanCandidateInfo> {
    val candidates = scanCandidatesFromPayload(payload, expectedGtin, expectedSku)
    val expBatch = expectedBatch.trim().lowercase()
    val expGtin = expectedGtin.filter { it.isDigit() }
    val expSku = expectedSku.trim().lowercase()

    val matchesBatch = candidates.values.any { it.trim().lowercase() == expBatch }
    val matchesGtin = expGtin.isNotEmpty() && candidates.values.any { it.filter { c -> c.isDigit() } == expGtin }
    val matchesSku = expSku.isNotEmpty() && candidates.values.any { it.trim().lowercase() == expSku }

    val ok = matchesBatch || matchesGtin || matchesSku
    return ok to candidates
}

fun mismatchMessage(
    scanned: ScanCandidateInfo,
    expectedBatch: String,
    expectedGtin: String,
    expectedSku: String,
): String {
    val parts = buildList {
        scanned.gtin?.let { add("GTIN $it") }
        scanned.batch?.let { add("Batch $it") }
        scanned.sku?.let { add("SKU $it") }
    }
    val summary = if (parts.isEmpty()) "Raw ${scanned.raw}" else parts.joinToString(", ")
    return "$summary does not match expected GTIN $expectedGtin, Batch $expectedBatch, SKU $expectedSku."
}

fun scanCandidatesFromPayload(
    payload: String,
    expectedGtin: String = "",
    expectedSku: String = "",
): ScanCandidateInfo {
    val trimmed = payload.trim().replace("\u001D", "<GS>")
    val values = mutableSetOf(trimmed)
    var detectedBatch: String? = null
    var detectedGtin: String? = null
    var detectedSku: String? = null

    runCatching {
        scanJson.decodeFromString<ScannedProductJson>(trimmed)
    }.getOrNull()?.let { decoded ->
        decoded.batch?.trim()?.takeIf { it.isNotEmpty() }?.let {
            values += it
            detectedBatch = it
        }
        decoded.gtin?.trim()?.takeIf { it.isNotEmpty() }?.let {
            values += it
            detectedGtin = it
        }
        decoded.sku?.trim()?.takeIf { it.isNotEmpty() }?.let {
            values += it
            detectedSku = it
        }
    }

    Gs1Parser.parse(trimmed).let { parsed ->
        parsed.batch?.trim()?.takeIf { it.isNotEmpty() }?.let {
            values += it
            detectedBatch = detectedBatch ?: it
        }
        parsed.gtin?.trim()?.takeIf { it.isNotEmpty() }?.let {
            values += it
            detectedGtin = detectedGtin ?: it
        }
    }

    parseSimpleGs1(trimmed).forEach { (ai, value) ->
        when (ai) {
            "10" -> {
                values += value
                detectedBatch = detectedBatch ?: value
            }
            "01" -> {
                values += value
                detectedGtin = detectedGtin ?: value
            }
        }
    }

    val numeric = trimmed.filter { it.isDigit() }
    val expGtinDigits = expectedGtin.filter { it.isDigit() }
    if (detectedGtin == null && expGtinDigits.isNotEmpty() && numeric == expGtinDigits) {
        detectedGtin = trimmed
    }
    if (detectedSku == null && expectedSku.isNotEmpty() &&
        trimmed.equals(expectedSku, ignoreCase = true)
    ) {
        detectedSku = expectedSku
    }

    return ScanCandidateInfo(
        raw = trimmed,
        values = values,
        batch = detectedBatch,
        gtin = detectedGtin,
        sku = detectedSku,
    )
}

/** Lightweight GS1 parenthetical / FNC1 AI extraction for batch (10) and GTIN (01). */
private fun parseSimpleGs1(raw: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    val paren = Regex("""\((\d{2,4})\)([^()]+)""")
    paren.findAll(raw).forEach { m ->
        out += m.groupValues[1] to m.groupValues[2].trim()
    }
    if (out.isNotEmpty()) return out

    var i = 0
    val s = raw.replace("<GS>", "\u001D")
    while (i < s.length - 2) {
        val ai = s.substring(i, i + 2)
        if (ai == "01" && i + 16 <= s.length) {
            out += "01" to s.substring(i + 2, i + 16)
            i += 16
        } else if (ai == "10") {
            val end = s.indexOf('\u001D', i + 2).takeIf { it >= 0 } ?: s.length
            out += "10" to s.substring(i + 2, end)
            i = if (end == s.length) s.length else end + 1
        } else {
            i++
        }
    }
    return out
}
