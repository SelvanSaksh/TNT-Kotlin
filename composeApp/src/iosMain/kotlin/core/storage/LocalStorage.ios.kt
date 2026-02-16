package core.storage

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of LocalStorage using NSUserDefaults
 */
actual class LocalStorage {
    
    private val userDefaults = NSUserDefaults.standardUserDefaults
    
    actual fun saveString(key: String, value: String) {
        userDefaults.setObject(value, key)
        userDefaults.synchronize()
    }
    
    actual fun getString(key: String): String? {
        return userDefaults.stringForKey(key)
    }
    
    actual fun remove(key: String) {
        userDefaults.removeObjectForKey(key)
        userDefaults.synchronize()
    }
    
    actual fun clear() {
        val dictionary = userDefaults.dictionaryRepresentation()
        dictionary.keys.forEach { key ->
            userDefaults.removeObjectForKey(key as String)
        }
        userDefaults.synchronize()
    }
}
