package features.app

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun JsonObject.nonBlankString(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * Primary label for dashboard / history rows. Handles nested `details.barcode`
 * and flat audit-log fields (`barcode_data`, `epc_id`).
 */
fun JsonObject.barcodeLogDisplayTitle(): String {
    this["details"]?.jsonObject?.nonBlankString("barcode")?.let { return it }
    nonBlankString("barcode_data")?.let { return it }
    nonBlankString("epc_id")?.let { return it }
    nonBlankString("data")?.let { return it }
    return "Unknown"
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
