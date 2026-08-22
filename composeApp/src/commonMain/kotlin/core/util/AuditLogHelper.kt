package core.util

import core.location.AppLocationCache
import core.network.PublicIpCache
import core.network.models.GenerationLogRequest
import core.network.models.ScanLogCreateRequest
import core.storage.SessionManager
import com.ratifye.app.getPlatform
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import network.Config
import network.models.UserDetail
import org.json.JSONObject
import resolver.parseDigitalLink
import kotlin.random.Random

object AuditLogHelper {

    private val userDetailJson = Json { ignoreUnknownKeys = true }

    fun companyIdFromBarcode(vararg sources: String): Int? {
        val blob = sources.joinToString(" ").replace("\\/", "/")
        parseIntId(
            Regex(""""ai"\s*:\s*"97"[\s\S]{0,180}?"value"\s*:\s*"([^"]+)"""")
                .find(blob)?.groupValues?.getOrNull(1),
        )?.let { return it }
        parseIntId(
            Regex(""""97"\s*:\s*\{[\s\S]{0,180}?"value"\s*:\s*"([^"]+)"""")
                .find(blob)?.groupValues?.getOrNull(1),
        )?.let { return it }
        parseIntId(
            Regex("""(?:/97/|\(97\)|[?&]97=)([^/?#&()"'\s]+)""")
                .find(blob)?.groupValues?.getOrNull(1),
        )?.let { return it }
        Regex("""https?://[^\s"'<>]+""").findAll(blob).forEach { match ->
            val parsed = runCatching { parseDigitalLink(match.value) }.getOrNull() ?: return@forEach
            parseIntId(parsed.ai97)?.let { return it }
            parsed.data.specialIdentifiers
                .firstOrNull { it.code == "97" }
                ?.value
                ?.let { parseIntId(it) }
                ?.let { return it }
        }
        return null
    }

    fun gtinFromBarcode(vararg sources: String): String {
        val blob = sources.joinToString(" ").replace("\\/", "/")
        Regex(""""ai"\s*:\s*"0[12]"[\s\S]{0,180}?"value"\s*:\s*"([^"]+)"""")
            .find(blob)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return normalizeGtin(it) }
        Regex("""(?:/01/|\(01\)|[?&]01=)([^/?#&()"'\s]+)""")
            .find(blob)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return normalizeGtin(it) }
        Regex("""https?://[^\s"'<>]+""").findAll(blob).forEach { match ->
            val parsed = runCatching { parseDigitalLink(match.value) }.getOrNull() ?: return@forEach
            parsed.gtin?.let { return normalizeGtin(it) }
            parsed.data.identifiers.firstOrNull { it.code == "01" || it.code == "02" }?.value
                ?.let { return normalizeGtin(it) }
        }
        return ""
    }

    private fun parseIntId(raw: String?): Int? =
        raw?.trim()?.filter { it.isDigit() }?.toIntOrNull()?.takeIf { it > 0 }

    fun isoEventTime(): String {
        val raw = Clock.System.now().toString().removeSuffix("Z")
        val main = raw.substringBefore('.')
        val frac = raw.substringAfter('.', "000").filter { it.isDigit() }.padEnd(3, '0').take(3)
        return "${main}.${frac}Z"
    }

    fun resolveCompanyId(sessionManager: SessionManager, vararg barcodeHints: String): Int {
        companyIdFromBarcode(*barcodeHints)?.let { return it }
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
        return when {
            quality.equals("Real", ignoreCase = true) -> "AUTHENTIC"
            quality.equals("Fake", ignoreCase = true) -> "DIVERTED"
            !isAuthFlow -> "AUTHENTIC"
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

    suspend fun buildScanLogRequest(
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
        signature: String? = null,
        companyIdOverride: Int? = null,
    ): ScanLogCreateRequest {
        PublicIpCache.ensure()
        val (lat, lon) = AppLocationCache.coordinates()
        val geo = AppLocationCache.geoLocation.trim().let { label ->
            if (label.isNotBlank() && !label.equals("Unknown", ignoreCase = true)) {
                label.take(512)
            } else {
                listOf(lat, lon).joinToString(",")
            }
        }
        val fromBarcode = companyIdOverride?.takeIf { it > 0 }
            ?: companyIdFromBarcode(barcodeData, scannedValue, epcId)
        val companyId = fromBarcode
            ?: sessionManager.getCompanyId()?.toIntOrNull()?.takeIf { it > 0 }
            ?: run {
                val ud = sessionManager.getUserDetail().orEmpty()
                if (ud.isBlank()) null
                else runCatching { userDetailJson.decodeFromString<UserDetail>(ud).companyId }
                    .getOrNull()
                    ?.takeIf { it > 0 }
            }
            ?: Config.DEFAULT_COMPANY_ID
        val userId = resolveUserId(sessionManager).takeIf { it > 0 }
        val isGuest = userId == null
        val quality = parseScanQuality(scannedValue)
        val authResult = authResultOverride
            ?: mapAuthResultForScan(quality, isAuthFlow, isGuest)

        val gtinPayload = normalizeGtin(gtin).ifBlank {
            gtinFromBarcode(barcodeData, scannedValue, epcId)
        }
        val serialPayload = serial.trim().ifBlank { "0" }
        val epcUrn = if (gtinPayload.isNotBlank()) {
            sgtinEpcUrn(gtinPayload, serialPayload)
        } else {
            epcId
        }
        val device = if (getPlatform().name.startsWith("iOS", ignoreCase = true)) "ios" else "android"
        val sig = signature?.trim()?.takeIf { it.isNotEmpty() && it.length in 1..128 } ?: "0xandroid"
        val scanner = scannerId(sessionManager)
        return ScanLogCreateRequest(
            event_type = "SCAN",
            epc_id = epcUrn,
            event_time = isoEventTime(),
            biz_step = "urn:epcglobal:cbv:bizstep:inspecting",
            biz_location = "urn:epc:id:sgln:0614141.00001.0",
            geo_location = geo,
            auth_result = authResult,
            scanner_id = scanner,
            signature = sig,
            company_id = companyId,
            user_id = userId,
            gtin = gtinPayload,
            lat = lat,
            longitude = lon,
            barcode_type = barcodeType.ifBlank { "QR" },
            barcode_data = if (gtinPayload.isNotBlank() && !barcodeData.contains("/01/")) {
                "https://dl.ratifye.ai/01/$gtinPayload"
            } else {
                barcodeData.ifBlank {
                    if (gtinPayload.isNotBlank()) "https://dl.ratifye.ai/01/$gtinPayload" else scannedValue
                }
            },
            device_type = device,
            device_id = scanner,
            ipaddress = PublicIpCache.value,
            serial = serialPayload,
            batch = batch.trim().ifBlank { "NA" },
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
