package network

import core.network.AuthSessionEvents
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

var AUTH_TOKEN: String? = null

/** Call after logout so outbound requests are not still authenticated. */
fun clearAuthToken() {
    AUTH_TOKEN = null
}

/**
 * Endpoints that are reachable without a session. Digital Link resolution runs
 * for guests too, so a 401 from those paths must not sign anyone out.
 */
private val PUBLIC_PATHS = setOf(
    "/auth/login",
    "/auth/otp-verification",
    "/productmaster/scan/authenticate",
    "/productmaster/config",
    "/ratifye/pages/resolve/by-gtin",
    "/productmaster/details/by-gtin",
    "/companies/barcode/create",
)

private fun shouldForceLoginOnUnauthorized(url: String): Boolean {
    if (!url.startsWith(Config.BASE_URL)) return false
    val path = url.removePrefix(Config.BASE_URL).substringBefore('?')
    return path !in PUBLIC_PATHS
}

val AuthInterceptor = createClientPlugin("AuthInterceptor") {
    onRequest { request, _ ->
        val url = request.url.toString()
        // Only attach app JWT to our API — never to third-party hosts (e.g. Nominatim).
        if (!url.startsWith(Config.BASE_URL)) return@onRequest
        AUTH_TOKEN?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
            request.headers.append(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}

val UnauthorizedInterceptor = createClientPlugin("UnauthorizedInterceptor") {
    onResponse { response ->
        if (response.status != HttpStatusCode.Unauthorized) return@onResponse
        val url = response.call.request.url.toString()
        if (shouldForceLoginOnUnauthorized(url)) {
            AuthSessionEvents.notifyUnauthorized()
        }
    }
}
