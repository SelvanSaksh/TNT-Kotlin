package utils

/**
 * EPC pure-identity URI builders — aligned with TrackandTrace-backend `epc-uri.util.ts`.
 */
object EpcUriBuilder {

    /**
     * Builds `urn:epc:id:sgtin:CompanyPrefix.ItemRef.SerialNumber`.
     *
     * @param gtin 13- or 14-digit GTIN (AI 01)
     * @param serialNumber serial from AI 21
     */
    fun buildSgtinEpcUri(gtin: String, serialNumber: String): String {
        val digits = gtin.filter { it.isDigit() }
        require(digits.isNotEmpty()) { "GTIN is required" }
        require(serialNumber.isNotBlank()) { "Serial number is required" }

        val indicator = if (digits.length == 14) digits[0] else '0'
        val body = if (digits.length == 14) digits.drop(1) else digits.padStart(13, '0').take(13)
        require(body.length == 13) { "GTIN body must resolve to 13 digits" }

        val companyPrefix = body.take(7)
        val itemRef = "$indicator${body.drop(7)}"
        return "urn:epc:id:sgtin:$companyPrefix.$itemRef.$serialNumber"
    }

    /**
     * Builds `urn:epc:id:sscc:CompanyPrefix.SerialReference` from an 18-digit SSCC (AI 00).
     */
    fun buildSsccEpcUri(sscc: String): String {
        val digits = sscc.filter { it.isDigit() }
        require(digits.length >= 17) { "SSCC must contain at least 17 digits" }

        val normalized = digits.padStart(18, '0').takeLast(18)
        val extensionDigit = normalized[0]
        val companyPrefix = normalized.substring(1, 8)
        val serialRef = extensionDigit + normalized.substring(8)
        return "urn:epc:id:sscc:$companyPrefix.$serialRef"
    }

    /** Percent-encodes an EPC URI for use in a REST path segment. */
    fun encodeEpcUriForPath(epcUri: String): String = buildString {
        epcUri.forEach { ch ->
            when {
                ch.isLetterOrDigit() || ch in "-_.~" -> append(ch)
                else -> {
                    val hex = ch.code.toString(16).uppercase().padStart(2, '0')
                    append('%').append(hex)
                }
            }
        }
    }

    /** Parse scan payload → SGTIN EPC URI when GTIN + serial are present. */
    fun sgtinEpcUriFromScan(raw: String): String? {
        val parsed = Gs1Parser.parse(raw)
        val gtin = parsed.gtin ?: return null
        val serial = parsed.serial ?: return null
        return buildSgtinEpcUri(gtin, serial)
    }

    /** Parse scan payload → SSCC EPC URI when AI 00 is present. */
    fun ssccEpcUriFromScan(raw: String): String? {
        val sscc = Gs1Parser.parse(raw).sscc ?: return null
        return buildSsccEpcUri(sscc)
    }
}
