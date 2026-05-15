package network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SendOtpRequest(
    val email: String  // Email address
)

@Serializable
data class SendOtpResponse(
    val isAutoGen: Boolean = false,
    /**
     * Present only in some environments. Production may omit this; the user
     * receives the OTP via realtime message (SMS / email) instead.
     */
    val otp: String? = null,
    val expiresAt: String,
    val email: String,
    val userId: Int
)

@Serializable
data class VerifyOtpRequest(
    val email: String,
    val otp: String
)

@Serializable
data class VerifyOtpResponse(
    val message: String,
    val userId: Int,
    val userEmail: String,
    val userDetail: UserDetail,
    val accessToken: String,
    /**
     * Backend has shipped multiple shapes for this field
     * (`[{id, name, moduleType}]`, `[{name, isActive}]`, …). Kept as a
     * raw [JsonElement] so we never fail decode on a schema change —
     * no app code reads it today.
     */
    val modules: JsonElement? = null,
    val subscription: UserSubscription? = null,
    @SerialName("location_details")
    val locationDetails: List<UserAssignedLocationDetail>? = null
)

@Serializable
data class UserDetail(

    val id: Int,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: Int = 0,

    val phone: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    @SerialName("companyid")
    val companyId: Int = 0,

    val status: Int = 0,

    /**
     * Backend has returned this in several shapes:
     *  - `["barcode", "reports"]`            (list of module names)
     *  - `[{ "id": 14, "name": "Categories", ... }]` (full objects)
     *
     * Kept as a raw [JsonElement] so we never fail decode on a schema
     * change — no app code reads it today.
     */
    @SerialName("access_modules")
    val accessModules: JsonElement? = null,

    /**
     * Backend now returns just `{ "locationId": ..., "assignmentType": ... }`
     * here; the full location records arrive in the top-level
     * [VerifyOtpResponse.locationDetails] array.
     */
    val locations: List<UserAssignedLocation> = emptyList(),

    @SerialName("subscription_status")
    val subscriptionStatus: String? = null,
    @SerialName("subscription_plan")
    val subscriptionPlan: String? = null,
    @SerialName("subscription_data")
    val subscriptionData: JsonObject? = null
)

@Serializable
data class UserAssignedLocation(
    val locationId: Int,
    val assignmentType: String? = null
)

/**
 * Top-level entry returned in `location_details`. The nested `location`
 * object is intentionally kept as a [JsonObject] so we don't have to
 * mirror every backend column here — only the assignment fields are
 * declared statically.
 */
@Serializable
data class UserAssignedLocationDetail(
    val locationId: Int,
    val assignmentType: String? = null,
    val location: JsonObject? = null
)

@Serializable
data class UserSubscription(
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
