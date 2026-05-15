package features.app.subscription

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiResponse<T>(
    val items: List<T> = emptyList()
)

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String,
    @SerialName("display_order")
    val displayOrder: Int = 0
)

@Serializable
data class PlanFeature(
    val id: String,
    val name: String,
    val description: String? = null,
    val type: String,
    val status: String
)

@Serializable
data class Price(
    val id: String,
    val amount: String,
    @SerialName("display_amount")
    val displayAmount: String,
    val currency: String,
    @SerialName("billing_period")
    val billingPeriod: String,
    @SerialName("entity_id")
    val entityId: String,
    val status: String
)

@Serializable
data class Entitlement(
    val id: String,
    @SerialName("plan_id")
    val planId: String,
    @SerialName("feature_id")
    val featureId: String,
    @SerialName("feature_type")
    val featureType: String,
    @SerialName("is_enabled")
    val isEnabled: Boolean,
    @SerialName("usage_limit")
    val usageLimit: Int? = null,
    @SerialName("is_soft_limit")
    val isSoftLimit: Boolean = false,
    @SerialName("static_value")
    val staticValue: String? = null,
    val status: String
)

data class PlanDisplayItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Price?,
    val features: List<FeatureDisplayItem>
)

data class FeatureDisplayItem(
    val id: String,
    val name: String,
    val value: String?,
    val isEnabled: Boolean
)

@Serializable
data class CompanySubscriptionsResponse(
    val data: List<CompanySubscription> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 10,
    val totalPages: Int = 0
)

@Serializable
data class CompanySubscription(
    val id: String,
    @SerialName("company_id")
    val companyId: Int,
    @SerialName("plan_id")
    val planId: String,
    @SerialName("flexi_subscription_id")
    val flexiSubscriptionId: String,
    val status: String,
    val features: JsonObject? = null,
    @SerialName("started_at")
    val startedAt: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("is_active")
    val isActive: Boolean? = null
)

@Serializable
data class PaymentOrderResponse(
    val success: Boolean,
    val order: RazorpayOrderPayload
)

@Serializable
data class RazorpayOrderPayload(
    val id: String,
    val entity: String,
    val amount: Int,
    val currency: String,
    /** Razorpay / DB order row lifecycle. Create-order must return `created` before checkout or verify. */
    val status: String? = null,
)

@Serializable
data class PaymentVerifyResponse(
    val success: Boolean
)

internal fun meteredFeatureDisplayValue(
    limit: Int,
    isSoftLimit: Boolean,
    featureName: String
): String {
    val soft = if (isSoftLimit) " (soft)" else ""
    if (limit == 0) return "Unlimited$soft"
    val lower = featureName.lowercase()

    val unitPair: Pair<String, String>? = when {
        lower.contains("product") -> "product" to "products"
        lower.contains("sku") -> "SKU" to "SKUs"
        lower.contains("seat") || lower.contains("user seat") -> "seat" to "seats"
        lower.contains("multi") && lower.contains("url") -> "URL" to "URLs"
        lower.contains("barcode") && (lower.contains("generation") || lower.contains("generat")) -> "" to ""
        lower.contains("scan") -> "scan" to "scans"
        else -> null
    }

    return if (unitPair != null) {
        val unit = if (limit == 1) unitPair.first else unitPair.second
        if (unit.isBlank()) "$limit$soft" else "$limit $unit$soft"
    } else {
        "$limit$soft"
    }
}

