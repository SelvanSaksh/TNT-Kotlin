package core.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Android implementation of LocalStorage using SharedPreferences
 */
actual class LocalStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "SakkshAssetPrefs",
        Context.MODE_PRIVATE
    )
    
    actual fun saveString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }
    
    actual fun getString(key: String): String? {
        return sharedPreferences.getString(key, null)
    }
    
    actual fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
    
    actual fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
