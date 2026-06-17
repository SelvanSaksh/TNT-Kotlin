package core.util

import core.location.AppLocationCache
import core.network.models.GenerationLogRequest
import core.network.models.ScanLogCreateRequest
import core.storage.SessionManager
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import network.Config
import network.models.UserDetail
import org.json.JSONObject
import kotlin.random.Random

object AuditLogHelper {

    private val userDetailJson = Json { ignoreUnknownKeys = true }

    fun resolveCompanyId(sessionManager: SessionManager): Int {
        sessionManager.getCompanyId()?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        val ud = sessionManager.getUserDetail().orEmpty()
        if (ud.isNotBlank()) {
            runCatching { userDetailJson.decodeFromString<UserDetail>(ud).companyId }
                .getOrNull()
                ?.takeIf { it > 0 }
                ?.let { return it }
            runCatching {
                JSONObject(ud).optInt("companyid", 0)
                    .takeIf { it > 0 }
                    ?: JSONObject(ud).optInt("company_id", 0).takeIf { it > 0 }
                    ?: JSONObject(ud).optInt("CompanyId", 0).takeIf { it > 0 }
            }.getOrNull()?.let { return it }
        }
        return Config.DEFAULT_COMPANY_ID
    }

    fun resolveUserId(sessionManager: SessionManager): Int =
        if (sessionManager.isLoggedIn()) sessionManager.getUserId() ?: 0 else 0

    fun scannerId(sessionManager: SessionManager): String {
        val userId = resolveUserId(sessionManager)
        if (userId > 0) return "scn_$userId"
        return sessionManager.getOrCreateGuestScannerId()
    }

    private fun SessionManager.getOrCreateGuestScannerId(): String {
        getGuestScannerId()?.let { return it }
        val id = "scn_android_${Random.nextInt(0x10000000, 0x7FFFFFFF).toString(16)}"
        saveGuestScannerId(id)
        return id
    }

    fun mapAuthResultForScan(
        quality: String?,
        isAuthFlow: Boolean,
        isGuest: Boolean,
    ): String {
        if (!isAuthFlow) return "UNKNOWN"
        return when {
            quality.equals("Real", ignoreCase = true) -> "AUTHENTIC"
            quality.equals("Fake", ignoreCase = true) -> "DIVERTED"
            isGuest -> "DIVERTED"
            else -> "AUTHENTIC"
        }
    }

    fun parseScanQuality(scannedValue: String): String? {
        val trimmed = scannedValue.trim()
        if (trimmed.isEmpty()) return null
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = org.json.JSONArray(trimmed)
                    if (arr.length() == 0) null
                    else arr.optJSONObject(0)?.optString("quality")?.takeIf { it.isNotBlank() }
                }
                trimmed.startsWith("{") -> {
                    JSONObject(trimmed).optString("quality").takeIf { it.isNotBlank() }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun buildScanLogRequest(
        sessionManager: SessionManager,
        epcId: String,
        barcodeType: String,
        barcodeData: String,
        gtin: String,
        serial: String,
        batch: String,
        isAuthFlow: Boolean,
        scannedValue: String,
        authResultOverride: String? = null,
    ): ScanLogCreateRequest {
        val (lat, lon) = AppLocationCache.coordinates()
        val geo = AppLocationCache.geoLocation
        val companyId = resolveCompanyId(sessionManager)
        val userId = resolveUserId(sessionManager)
        val isGuest = userId == 0
        val quality = parseScanQuality(scannedValue)
        val authResult = authResultOverride
            ?: mapAuthResultForScan(quality, isAuthFlow, isGuest)

        val gtinPayload = gtin.trim()
        return ScanLogCreateRequest(
            event_type = if (isAuthFlow) "AUTHENTICATE" else "SCAN",
            epc_id = epcId,
            event_time = Clock.System.now().toString(),
            geo_location = geo,
            auth_result = authResult,
            scanner_id = scannerId(sessionManager),
            company_id = companyId,
            user_id = userId,
            lat = lat,
            long = lon,
            barcode_type = barcodeType,
            barcode_data = barcodeData,
            serial = serial,
            batch = batch,
            gtin = gtinPayload,
        )
    }

    fun buildGenerationLogRequest(
        sessionManager: SessionManager,
        barcodeType: String,
        barcodeData: String,
        serial: String = extractGs1Serial(barcodeData),
        batch: String = extractGs1Batch(barcodeData),
    ): GenerationLogRequest {
        val (lat, lon) = AppLocationCache.coordinates()
        val geo = AppLocationCache.geoLocation
        val companyId = resolveCompanyId(sessionManager)
        val userId = resolveUserId(sessionManager)
        val epcId = extractGs1Gtin(barcodeData).ifBlank { barcodeData.trim().take(64) }

        return GenerationLogRequest(
            barcode_type = barcodeType,
            barcode_data = barcodeData,
            company_id = companyId,
            user_id = userId,
            event_id = newGenerationEventId(),
            event_time = Clock.System.now().toString(),
            epc_id = epcId,
            geo_location = geo,
            scanner_id = scannerId(sessionManager),
            lat = lat,
            long = lon,
            serial = serial,
            batch = batch,
        )
    }
}
