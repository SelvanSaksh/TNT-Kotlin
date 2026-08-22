package core.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import network.ApiClient

object PublicIpCache {
    var value: String = ""
        private set

    suspend fun ensure() {
        if (value.isNotBlank()) return
        value = runCatching {
            ApiClient.client.get("https://api.ipify.org").body<String>().trim()
        }.getOrNull().orEmpty()
    }
}
