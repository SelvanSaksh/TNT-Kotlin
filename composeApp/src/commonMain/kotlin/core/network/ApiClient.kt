package network

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*

object ApiClient {

    val client get() = HttpClientFactory.httpClient

    suspend inline fun <reified T> get(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
    ): T {
        return if (endpoint.startsWith("http")) {
            client.get(endpoint) {
                query.forEach { (key, value) -> parameter(key, value) }
            }.body()
        } else {
            client.get(Config.BASE_URL + endpoint) {
                query.forEach { (key, value) -> parameter(key, value) }
            }.body()
        }
    }

    suspend inline fun <reified Req, reified Res> post(endpoint: String, payload: Req): Res {
        return client.post(Config.BASE_URL + endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()
    }

    suspend inline fun <reified Req, reified Res> patch(endpoint: String, payload: Req): Res {
        return client.patch(Config.BASE_URL + endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }.body()
    }
}
