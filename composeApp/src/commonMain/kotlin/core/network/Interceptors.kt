package network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders

var AUTH_TOKEN: String? = null

val AuthInterceptor = createClientPlugin("AuthInterceptor") {
    onRequest { request, _ ->
        AUTH_TOKEN?.let {
            request.headers {
                append(HttpHeaders.Authorization, "Bearer $it")
            }
        }
    }
}
