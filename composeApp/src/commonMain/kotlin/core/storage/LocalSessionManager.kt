package core.storage

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Composition local for providing SessionManager throughout the app
 */
val LocalSessionManager = staticCompositionLocalOf<SessionManager> {
    error("SessionManager not provided")
}
