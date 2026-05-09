package core.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }

    fun clearSession() {
        // User requested local storage to be cleared on logout.
        storage.clear()
    }
}
