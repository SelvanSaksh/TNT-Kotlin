package features.app.subscription

import core.storage.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import network.Config
import network.HttpClientFactory
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.Clock

object BillingRepository {

    private const val BILLING_BASE_URL = "https://billing.sakksh.com/v1"
    private const val RAZORPAY_KEY_ID = "rzp_live_SpXl3J7uMe5WTD"

    // Same headers as iOS implementation.
    private val billingHeaders: Map<String, String> = mapOf(
        "x-api-key" to "sk_local_flexprice_test_key",
        "x-environment-id" to "00000000-0000-0000-0000-000000000000",
        "Accept" to "*/*"
    )

    private val client: HttpClient by lazy { HttpClientFactory.httpClient }
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private suspend inline fun <reified T> billingGetItems(path: String): List<T> {
        val url = "$BILLING_BASE_URL/$path"
        val response = client.get(url) {
            headers {
                billingHeaders.forEach { (k, v) -> append(k, v) }
            }
        }
        val decoded = response.body<ApiResponse<T>>()
        return decoded.items
    }

    suspend fun fetchAll(): Result<BillingBundle> {
        return try {
            // Run sequentially to keep error surface simple.
            val plans = billingGetItems<Plan>("plans")
            val features = billingGetItems<PlanFeature>("features")
            val prices = billingGetItems<Price>("prices")
            val entitlements = billingGetItems<Entitlement>("entitlements")

            Result.success(
                BillingBundle(
                    plans = plans,
                    features = features,
                    prices = prices,
                    entitlements = entitlements
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun postJson(
        endpointPath: String,
        body: JsonObject,
        sessionManager: SessionManager
    ): Pair<String, Int> {
        val token = sessionManager.getAccessToken().orEmpty()
        val response = client.post("${Config.BASE_URL}/$endpointPath") {
            contentType(ContentType.Application.Json)
            headers {
                if (token.isNotBlank()) append("Authorization", "Bearer $token")
            }
            setBody(body)
        }
        return response.body<String>() to response.status.value
    }

    private suspend fun patchJson(
        endpointPath: String,
        body: JsonObject,
        sessionManager: SessionManager
    ): Pair<String, Int> {
        val token = sessionManager.getAccessToken().orEmpty()
        val response = client.patch("${Config.BASE_URL}/$endpointPath") {
            contentType(ContentType.Application.Json)
            headers {
                if (token.isNotBlank()) append("Authorization", "Bearer $token")
            }
            setBody(body)
        }
        return response.body<String>() to response.status.value
    }

    /**
     * PATCH `/companies/subscriptions/{subscription_id}` with updated feature usage.
     * Mirrors iOS [SubscriptionUsageUpdater.incrementUsage]: bumps [featureKey]'s `usage_count`
     * by [by], capped at `usage_limit`. Does not PATCH if already at limit.
     */
    suspend fun incrementUsage(
        sessionManager: SessionManager,
        featureKey: String,
        by: Int = 1
    ): Result<Unit> {
        if (by <= 0) return Result.success(Unit)
        val raw = sessionManager.getSubscriptionData().orEmpty().trim()
        if (raw.isBlank()) {
            println("⚠️ incrementUsage: no subscription data")
            return Result.failure(IllegalStateException("No subscription data"))
        }

        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            return Result.failure(it)
        }

        val subscriptionId = root["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (subscriptionId.isEmpty()) {
            println("⚠️ incrementUsage: missing subscription id in stored JSON")
            return Result.failure(IllegalStateException("Missing subscription id"))
        }

        val featuresEl = root["features"]?.jsonObject
            ?: return Result.failure(IllegalStateException("Missing features"))

        val featureNode = featuresEl[featureKey]
            ?: run {
                println("⚠️ incrementUsage: feature '$featureKey' not present")
                return Result.failure(IllegalStateException("Feature not found: $featureKey"))
            }

        val featureObj = featureNode as? JsonObject
            ?: run {
                println("⚠️ incrementUsage: feature '$featureKey' is not a usage object")
                return Result.failure(IllegalStateException("Feature $featureKey has no usage fields"))
            }
        val current = featureObj["usage_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val limit = featureObj["usage_limit"]?.jsonPrimitive?.content?.toIntOrNull()
        val unlimited = limit == null || limit == 0

        val remaining = when {
            unlimited -> Int.MAX_VALUE
            else -> (limit!! - current).coerceAtLeast(0)
        }

        if (!unlimited && remaining == 0) {
            println("⚠️ incrementUsage: limit reached for $featureKey ($current / $limit)")
            return Result.failure(SubscriptionError.UsageLimitReached(featureKey))
        }

        val appliedDelta = by.coerceAtMost(remaining)
        if (appliedDelta <= 0) return Result.success(Unit)

        val newCount = if (unlimited) current + by else current + appliedDelta

        if (newCount == current) return Result.success(Unit)

        val newFeatureObj = buildJsonObject {
            featureObj.forEach { (fk, el) ->
                when (fk) {
                    "usage_count" -> put("usage_count", JsonPrimitive(newCount))
                    else -> put(fk, el)
                }
            }
            if (!featureObj.containsKey("usage_count")) {
                put("usage_count", JsonPrimitive(newCount))
            }
        }

        val updatedFeatures = buildJsonObject {
            featuresEl.forEach { (k, v) ->
                if (k == featureKey) put(k, newFeatureObj)
                else put(k, v)
            }
        }

        val payload = buildJsonObject {
            root["company_id"]?.let { put("company_id", it) }
            root["plan_id"]?.let { put("plan_id", it) }
            root["flexi_subscription_id"]?.let { put("flexi_subscription_id", it) }
            root["status"]?.let { put("status", it) }
            put("features", updatedFeatures)
            root["started_at"]?.let { put("started_at", it) }
            root["expires_at"]?.let { put("expires_at", it) }
        }

        val planIdFallback = root["plan_id"]?.jsonPrimitive?.contentOrNull.orEmpty()

        return try {
            println("📤 PATCH companies/subscriptions/$subscriptionId (feature=$featureKey +$appliedDelta → $newCount)")
            val (responseBody, status) = patchJson(
                "companies/subscriptions/$subscriptionId",
                payload,
                sessionManager
            )
            println("📥 PATCH status: $status body: $responseBody")

            if (status !in 200..299) {
                val message = parseMessage(responseBody) ?: "Subscription usage update failed"
                return Result.failure(SubscriptionError.ServerError(message))
            }

            val trimmed = responseBody.trim()
            if (trimmed.startsWith("{")) {
                storeSubscriptionFromBackendResponse(trimmed, planIdFallback, sessionManager)
            } else {
                persistMergedSubscription(root, updatedFeatures, sessionManager)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun persistMergedSubscription(
        root: JsonObject,
        updatedFeatures: JsonObject,
        sessionManager: SessionManager
    ) {
        val merged = buildJsonObject {
            root.forEach { (k, v) ->
                if (k == "features") put(k, updatedFeatures)
                else put(k, v)
            }
        }
        val jsonStr = json.encodeToString(JsonElement.serializer(), merged)
        val status = merged["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val planId = merged["plan_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        sessionManager.saveSubscription(rawJson = jsonStr, status = status, planId = planId)
    }

    private fun parseAmountInRupees(rawAmount: String): Int? {
        val normalized = rawAmount.trim()
        return normalized.toIntOrNull()
            ?: normalized.toDoubleOrNull()?.toInt()
    }

    private fun computeExpiryIso(planId: String): String {
        val lower = planId.lowercase()
        val start = Clock.System.now()
        val expiry = when {
            lower.contains("year") || lower.contains("annual") -> start + 365.days
            lower.contains("week") -> start + 7.days
            else -> start + 30.days
        }
        return expiry.toString()
    }

    private fun featureKey(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    private fun usageLimit(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val digits = value.filter { it.isDigit() }
        if (digits.isBlank()) return null
        return digits.toIntOrNull()
    }

    private fun buildFeaturesPayload(features: List<FeatureDisplayItem>): JsonObject {
        return buildJsonObject {
            features.filter { it.isEnabled }.forEach { feature ->
                val key = featureKey(feature.name)
                if (key.isBlank()) return@forEach

                val limit = usageLimit(feature.value)
                if (limit != null) {
                    put(
                        key,
                        buildJsonObject {
                            put("enabled", true)
                            put("usage_limit", limit)
                            put("usage_count", 0)
                        }
                    )
                } else {
                    put(key, JsonPrimitive(true))
                }
            }
        }
    }

    /**
     * POST `/payments/create-order` — matches server contract:
     * `{ "amount", "currency", "receipt", "userId", "companyId", "notes": { "plan" } }`.
     */
    suspend fun createOrder(
        amountRupees: Int,
        planId: String,
        sessionManager: SessionManager,
        currency: String = "INR"
    ): Result<RazorpayOrderPayload> {
        return try {
            if (amountRupees <= 0) {
                return Result.failure(SubscriptionError.InvalidAmount)
            }
            val userId = sessionManager.getUserId()
                ?: return Result.failure(SubscriptionError.ServerError("Missing user id"))
            val companyIdRaw = sessionManager.getCompanyId()?.trim().orEmpty()
                .ifBlank { return Result.failure(SubscriptionError.ServerError("Missing company id")) }

            val receipt = "order_rcpt_${companyIdRaw}_${Clock.System.now().epochSeconds}"
            val payload = buildJsonObject {
                put("amount", amountRupees)
                put("currency", currency)
                put("receipt", receipt)
                put("userId", userId)
                val companyNumeric = companyIdRaw.toIntOrNull()
                if (companyNumeric != null) {
                    put("companyId", companyNumeric)
                } else {
                    put("companyId", companyIdRaw)
                }
                put(
                    "notes",
                    buildJsonObject {
                        put("plan", planId)
                    }
                )
            }

            println("💳 [Razorpay] Create order payload: $payload")
            val (raw, status) = postJson("payments/create-order", payload, sessionManager)
            println("💳 [Razorpay] Create order status: $status")
            println("💳 [Razorpay] Create order response: $raw")

            if (status !in 200..299) {
                return Result.failure(
                    SubscriptionError.ServerError(parseMessage(raw) ?: "Unable to create payment order.")
                )
            }
            val decoded = json.decodeFromString(PaymentOrderResponse.serializer(), raw)
            if (!decoded.success) {
                return Result.failure(SubscriptionError.ServerError("Unable to create payment order."))
            }
            val ord = decoded.order
            if (ord.status?.equals("created", ignoreCase = true) != true) {
                return Result.failure(
                    SubscriptionError.ServerError(
                        "Payment order must be in created status before checkout (got: ${ord.status ?: "missing"})."
                    )
                )
            }
            Result.success(ord)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POST `/payments/verify` — only after a `created` order from [createOrder] and Razorpay success for that same `order.id`. */
    suspend fun verifyPayment(
        orderId: String,
        paymentId: String,
        signature: String,
        sessionManager: SessionManager
    ): Result<Unit> {
        return try {
            val payload = buildJsonObject {
                put("razorpay_order_id", orderId)
                put("razorpay_payment_id", paymentId)
                put("razorpay_signature", signature)
            }
            println("💳 [Razorpay] Verify payload: $payload")

            val (raw, status) = postJson("payments/verify", payload, sessionManager)
            println("💳 [Razorpay] Verify status: $status")
            println("💳 [Razorpay] Verify response: $raw")

            if (status !in 200..299) {
                return Result.failure(
                    SubscriptionError.ServerError(parseMessage(raw) ?: "Payment verification failed.")
                )
            }
            val decoded = json.decodeFromString(PaymentVerifyResponse.serializer(), raw)
            if (!decoded.success) {
                return Result.failure(SubscriptionError.PaymentVerificationFailed)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun subscribeWithRazorpay(
        planId: String,
        planName: String,
        price: Price?,
        features: List<FeatureDisplayItem>,
        sessionManager: SessionManager
    ): Result<Unit> {
        if (price == null) {
            return subscribe(planId = planId, features = features, sessionManager = sessionManager)
        }

        val rupees = parseAmountInRupees(price.amount)
            ?: return Result.failure(SubscriptionError.InvalidAmount)

        val order = createOrder(
            amountRupees = rupees,
            planId = planId,
            sessionManager = sessionManager,
            currency = price.currency.trim().ifBlank { "INR" }.uppercase()
        ).getOrElse { return Result.failure(it) }

        when (
            val paid = presentRazorpayCheckout(
                RazorpayCheckoutArgs(
                    keyId = RAZORPAY_KEY_ID,
                    orderId = order.id,
                    businessName = "Sakksh",
                    description = planName,
                    customerEmail = sessionManager.getUserEmail(),
                )
            )
        ) {
            RazorpayCheckoutOutcome.Unsupported -> {
                return subscribe(planId = planId, features = features, sessionManager = sessionManager)
            }
            RazorpayCheckoutOutcome.Cancelled -> {
                return Result.failure(SubscriptionError.PaymentCancelled)
            }
            is RazorpayCheckoutOutcome.Error -> {
                return Result.failure(SubscriptionError.ServerError(paid.message))
            }
            is RazorpayCheckoutOutcome.Success -> {
                if (paid.orderId != order.id) {
                    return Result.failure(
                        SubscriptionError.ServerError(
                            "Payment does not match the active order. Verification was not sent."
                        )
                    )
                }
                verifyPayment(
                    orderId = paid.orderId,
                    paymentId = paid.paymentId,
                    signature = paid.signature,
                    sessionManager = sessionManager
                ).getOrElse { return Result.failure(it) }
                return subscribe(planId = planId, features = features, sessionManager = sessionManager)
            }
        }
    }

    suspend fun subscribe(
        planId: String,
        features: List<FeatureDisplayItem> = emptyList(),
        sessionManager: SessionManager
    ): Result<Unit> {
        return try {
            val companyId = sessionManager.getCompanyId()?.toIntOrNull()
                ?: return Result.failure(IllegalStateException("Missing company id"))

            val payload = buildJsonObject {
                put("company_id", companyId)
                put("plan_id", planId)
                put("flexi_subscription_id", "sub_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000, 9999)}")
                put("status", "active")
                put("features", buildFeaturesPayload(features))
                put("started_at", Clock.System.now().toString())
                put("expires_at", computeExpiryIso(planId))
            }
            println("📦 REQUEST URL: ${Config.BASE_URL}/companies/subscriptions")
            println("📦 FINAL JSON PAYLOAD: $payload")

            val (raw, status) = postJson("companies/subscriptions", payload, sessionManager)
            println("📥 STATUS CODE: $status")
            println("📥 RESPONSE BODY: $raw")

            if (status !in 200..299) {
                val message = parseMessage(raw) ?: "Subscription failed"
                return Result.failure(SubscriptionError.ServerError(message))
            }

            storeSubscriptionFromBackendResponse(raw, planId, sessionManager)
            println("✅ Subscription successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCompanySubscriptions(sessionManager: SessionManager): Result<List<CompanySubscription>> {
        return try {
            val companyId = sessionManager.getCompanyId()?.toIntOrNull()
                ?: return Result.failure(IllegalStateException("Missing company id"))
            val token = sessionManager.getAccessToken()

            val response = client.get("${Config.BASE_URL}/companies/subscriptions?company_id=$companyId") {
                headers {
                    if (!token.isNullOrBlank()) {
                        append("Authorization", "Bearer $token")
                    }
                }
            }

            if (response.status.value !in 200..299) {
                val bodyText = response.body<String>()
                val message = parseMessage(bodyText) ?: "Unable to fetch subscriptions"
                return Result.failure(SubscriptionError.ServerError(message))
            }

            val body = response.body<CompanySubscriptionsResponse>()
            Result.success(body.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncAndStoreSubscription(sessionManager: SessionManager): Result<Boolean> {
        val fetchResult = fetchCompanySubscriptions(sessionManager)
        return fetchResult.map { subscriptions ->
            val active = subscriptions.firstOrNull { it.status.equals("active", ignoreCase = true) }
            if (active != null) {
                sessionManager.saveSubscription(
                    rawJson = json.encodeToString(active),
                    status = active.status,
                    planId = active.planId
                )
                true
            } else {
                sessionManager.saveSubscription(
                    rawJson = "",
                    status = "",
                    planId = ""
                )
                false
            }
        }
    }

    private fun storeSubscriptionFromBackendResponse(
        rawResponse: String,
        planId: String,
        sessionManager: SessionManager
    ) {
        val root = runCatching { json.parseToJsonElement(rawResponse).jsonObject }.getOrNull()
        if (root == null) {
            sessionManager.saveSubscription(rawJson = rawResponse, status = "active", planId = planId)
            return
        }

        val nestedSubscriptionData = root["subscription_data"]?.jsonObject
        if (nestedSubscriptionData != null) {
            val nestedSubscription = nestedSubscriptionData["subscription"]?.jsonObject
            val status = nestedSubscription?.get("status")?.jsonPrimitive?.content
                ?: "active"
            val nestedPlan = nestedSubscriptionData["plan"]?.jsonObject
            val plan = nestedPlan?.get("lookup_key")?.jsonPrimitive?.content
                ?: nestedPlan?.get("name")?.jsonPrimitive?.content
                ?: planId
            sessionManager.saveSubscription(
                rawJson = nestedSubscriptionData.toString(),
                status = status,
                planId = plan
            )
            return
        }

        val subscriptionObj = root["subscription"]?.jsonObject ?: root
        val status = subscriptionObj["status"]?.jsonPrimitive?.content ?: "active"
        val responsePlanId = subscriptionObj["plan_id"]?.jsonPrimitive?.content ?: planId
        sessionManager.saveSubscription(
            rawJson = subscriptionObj.toString(),
            status = status,
            planId = responsePlanId
        )
        println("✅ Subscription saved locally — status: $status")
    }

    private fun parseMessage(rawBody: String): String? {
        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val root = json.parseToJsonElement(trimmed).jsonObject
            root["message"]?.jsonPrimitive?.content
        }.getOrNull()
    }
}

data class BillingBundle(
    val plans: List<Plan>,
    val features: List<PlanFeature>,
    val prices: List<Price>,
    val entitlements: List<Entitlement>
)

sealed class SubscriptionError(message: String) : Throwable(message) {
    class ServerError(message: String) : SubscriptionError(message)
    class UsageLimitReached(val featureKey: String) :
        SubscriptionError("Usage limit reached for $featureKey")
    data object InvalidAmount : SubscriptionError("Invalid plan amount.")
    data object PaymentCancelled : SubscriptionError("Payment was cancelled.")
    data object PaymentVerificationFailed : SubscriptionError("Payment verification failed.")
    data object PaymentSetupIncomplete : SubscriptionError("Payment setup is incomplete. Please contact support.")
}

