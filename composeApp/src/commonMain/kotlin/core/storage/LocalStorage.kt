package core.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Interface for local storage operations
 * Platform-specific implementations will be provided for Android and iOS
 */
expect class LocalStorage {
    fun saveString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
    fun clear()
}

/**
 * Storage keys used throughout the app
 */
object StorageKeys {
    const val ACCESS_TOKEN = "access_token"
    const val USER_ID = "user_id"
    const val USER_EMAIL = "user_email"
    const val USER_DETAIL = "user_detail"
}

/**
 * User session manager for handling authentication data
 */
class SessionManager(private val storage: LocalStorage) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Save user session data
     */
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
    
    /**
     * Get access token
     */
    fun getAccessToken(): String? {
        return storage.getString(StorageKeys.ACCESS_TOKEN)
    }
    
    /**
     * Get user ID
     */
    fun getUserId(): Int? {
        return storage.getString(StorageKeys.USER_ID)?.toIntOrNull()
    }
    
    /**
     * Get user email
     */
    fun getUserEmail(): String? {
        return storage.getString(StorageKeys.USER_EMAIL)
    }
    
    /**
     * Get user detail as JSON string
     */
    fun getUserDetail(): String? {
        return storage.getString(StorageKeys.USER_DETAIL)
    }
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }
    
    /**
     * Clear all session data (logout)
     */
    fun clearSession() {
        storage.remove(StorageKeys.ACCESS_TOKEN)
        storage.remove(StorageKeys.USER_ID)
        storage.remove(StorageKeys.USER_EMAIL)
        storage.remove(StorageKeys.USER_DETAIL)
    }
}
