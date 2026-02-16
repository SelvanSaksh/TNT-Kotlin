package network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                    isLenient = true
                    explicitNulls = false
                    encodeDefaults = false
                }
            )
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }

        install(AuthInterceptor)
    }
}
