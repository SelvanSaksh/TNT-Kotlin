package core.storage

/**
 * iOS implementation to get LocalStorage instance
 */
actual fun getLocalStorage(): LocalStorage {
    return LocalStorage()
}
