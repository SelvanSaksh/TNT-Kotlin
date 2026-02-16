package core.storage

import android.content.Context

private lateinit var appContext: Context

/**
 * Initialize the app context for Android
 * Should be called from MainActivity
 */
fun initAndroidContext(context: Context) {
    appContext = context.applicationContext
}

/**
 * Android implementation to get LocalStorage instance
 */
actual fun getLocalStorage(): LocalStorage {
    return LocalStorage(appContext)
}
