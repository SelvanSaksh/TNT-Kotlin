package core.util

import core.network.models.ScanLogCreateRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ScanAuditLog {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun formatRequestBody(request: ScanLogCreateRequest): String =
        runCatching { json.encodeToString(request) }
            .getOrElse { request.toString() }

    fun line(message: String) {
        println("SCAN_AUDIT: $message")
    }
}
