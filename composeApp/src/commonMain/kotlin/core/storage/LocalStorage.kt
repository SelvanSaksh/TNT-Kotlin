package core.storage

import network.clearAuthToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import core.session.WarehouseStaffRole

expect class LocalStorage {
    fun saveString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
    fun clear()
}

object StorageKeys {
    const val ACCESS_TOKEN = "access_token"
    const val USER_ID = "user_id"
    const val USER_EMAIL = "user_email"
    const val USER_DETAIL = "user_detail"
    const val COMPANY_ID = "company_id"
    const val SUBSCRIPTION_DATA = "subscription_data"
    const val SUBSCRIPTION_STATUS = "subscription_status"
    const val SUBSCRIPTION_PLAN_ID = "subscription_plan_id"
    const val LOCATION_DETAILS = "location_details"
    const val CACHED_LAT = "cached_lat"
    const val CACHED_LON = "cached_lon"
    const val CACHED_GEO_LABEL = "cached_geo_label"
    const val GUEST_SCANNER_ID = "guest_scanner_id"
    const val USER_ROLE = "user_role"
    const val USER_MOBILE_MODULES = "user_mobile_modules"
    const val ACTIVE_WAREHOUSE_STAFF_ROLE = "active_warehouse_staff_role"
}

class SessionManager(private val storage: LocalStorage) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun saveSession(
        accessToken: String,
        userId: Int,
        userEmail: String,
        userDetail: String
    ) {
        storage.saveString(StorageKeys.ACCESS_TOKEN, accessToken)
        storage.saveString(StorageKeys.USER_ID, userId.toString())
        storage.saveString(StorageKeys.USER_EMAIL, userEmail)
        storage.saveString(StorageKeys.USER_DETAIL, userDetail)
    }



    fun getAccessToken(): String? {
        return storage.getString(StorageKeys.ACCESS_TOKEN)
    }

    fun getUserId(): Int? {
        return storage.getString(StorageKeys.USER_ID)?.toIntOrNull()
    }

    fun getUserEmail(): String? {
        return storage.getString(StorageKeys.USER_EMAIL)
    }

    fun getUserDetail(): String? {
        return storage.getString(StorageKeys.USER_DETAIL)
    }

    fun getCompanyId(): String? {
        return storage.getString(StorageKeys.COMPANY_ID)
    }

    fun saveCompanyId(companyId: String) {
        storage.saveString(StorageKeys.COMPANY_ID, companyId)
    }

    fun saveSubscription(
        rawJson: String,
        status: String,
        planId: String
    ) {
        storage.saveString(StorageKeys.SUBSCRIPTION_DATA, rawJson)
        storage.saveString(StorageKeys.SUBSCRIPTION_STATUS, status)
        storage.saveString(StorageKeys.SUBSCRIPTION_PLAN_ID, planId)
    }

    fun getSubscriptionData(): String? = storage.getString(StorageKeys.SUBSCRIPTION_DATA)

    fun getSubscriptionStatus(): String? = storage.getString(StorageKeys.SUBSCRIPTION_STATUS)

    fun getSubscriptionPlanId(): String? = storage.getString(StorageKeys.SUBSCRIPTION_PLAN_ID)

    fun saveLocationDetails(rawJson: String) {
        storage.saveString(StorageKeys.LOCATION_DETAILS, rawJson)
    }

    fun getLocationDetails(): String? = storage.getString(StorageKeys.LOCATION_DETAILS)

    fun saveCachedDeviceLocation(lat: Double, lon: Double, geoLabel: String) {
        storage.saveString(StorageKeys.CACHED_LAT, lat.toString())
        storage.saveString(StorageKeys.CACHED_LON, lon.toString())
        storage.saveString(StorageKeys.CACHED_GEO_LABEL, geoLabel)
    }

    fun getCachedLatitude(): Double? = storage.getString(StorageKeys.CACHED_LAT)?.toDoubleOrNull()

    fun getCachedLongitude(): Double? = storage.getString(StorageKeys.CACHED_LON)?.toDoubleOrNull()

    fun getCachedGeoLabel(): String? = storage.getString(StorageKeys.CACHED_GEO_LABEL)

    fun getGuestScannerId(): String? = storage.getString(StorageKeys.GUEST_SCANNER_ID)

    fun saveGuestScannerId(id: String) {
        storage.saveString(StorageKeys.GUEST_SCANNER_ID, id)
    }

    fun saveUserRole(role: Int) {
        storage.saveString(StorageKeys.USER_ROLE, role.toString())
    }

    fun getUserRole(): Int = storage.getString(StorageKeys.USER_ROLE)?.toIntOrNull() ?: 0

    fun saveMobileWarehouseModules(modules: List<String>) {
        storage.saveString(StorageKeys.USER_MOBILE_MODULES, json.encodeToString(modules))
    }

    fun getMobileWarehouseModules(): List<String> {
        val raw = storage.getString(StorageKeys.USER_MOBILE_MODULES) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse { emptyList() }
    }

    fun saveActiveWarehouseStaffRole(rawValue: String) {
        storage.saveString(StorageKeys.ACTIVE_WAREHOUSE_STAFF_ROLE, rawValue)
    }

    fun getActiveWarehouseStaffRole(): WarehouseStaffRole? =
        WarehouseStaffRole.fromRaw(storage.getString(StorageKeys.ACTIVE_WAREHOUSE_STAFF_ROLE))

    fun clearActiveWarehouseStaffRole() {
        storage.remove(StorageKeys.ACTIVE_WAREHOUSE_STAFF_ROLE)
    }

    /** True only after OTP verification stored a token and user profile (not mid-login). */
    fun isLoggedIn(): Boolean {
        val token = getAccessToken()?.trim().orEmpty()
        val detail = getUserDetail()?.trim().orEmpty()
        return token.isNotEmpty() && detail.isNotEmpty()
    }

    fun clearSession() {
        // User requested local storage to be cleared on logout.
        storage.clear()
        clearAuthToken()
    }

    /** Guest / explore mode — no completed sign-in session. */
    fun isGuestUser(): Boolean = !isLoggedIn()
}

/** Sign-in prompt shown once per cold start when entering guest shell. */
object GuestPromptState {
    var shownThisLaunch: Boolean = false
}
