package network

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*

object ApiClient {

    val client get() = HttpClientFactory.httpClient

    suspend inline fun <reified T> get(endpoint: String): T {
        return client.get(Config.BASE_URL + endpoint).body()
    }

    suspend inline fun <reified Req, reified Res> post(endpoint: String, payload: Req): Res {
        return client.post(Config.BASE_URL + endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()
    }
}
