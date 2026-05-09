package features.app.subscription

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object SubscriptionFeatureKeys {
    const val BARCODE_GENERATION = "barcode_generation"
    const val MULTI_URL = "multi_url"
}

object SubscriptionLimitMessages {
    const val BARCODE_GENERATION =
        "Based on your current plan, your barcode generation limit has been reached. Upgrade your plan to generate more barcodes."
    const val MULTI_URL =
        "Based on your current plan, your multi-link generation limit has been reached. Upgrade your plan to generate more barcodes."
}

object SubscriptionPlanLimits {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * True when a metered feature is disabled, or [usage_count] >= [usage_limit] (for limit > 0).
     * Features stored as `true` (JsonPrimitive) are treated as enabled without metering.
     */
    fun isMeteredFeatureExhausted(subscriptionJson: String?, featureKey: String): Boolean {
        if (subscriptionJson.isNullOrBlank()) return false
        return runCatching {
            val root = json.parseToJsonElement(subscriptionJson).jsonObject
            val features = root["features"]?.jsonObject ?: return@runCatching false
            val el = features[featureKey] ?: return@runCatching false
            when (el) {
                is JsonPrimitive -> when (el.booleanOrNull) {
                    true -> false
                    false -> true
                    else -> false
                }
                is JsonObject -> {
                    val enabled = el["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                    if (!enabled) return@runCatching true
                    val count = el["usage_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val limit = el["usage_limit"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (limit == null || limit == 0) return@runCatching false
                    count >= limit
                }
                else -> false
            }
        }.getOrDefault(false)
    }
}

@Composable
fun GenerationLimitAlertDialog(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generation limit reached", fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onUpgrade()
                }
            ) { Text("Upgrade plan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
