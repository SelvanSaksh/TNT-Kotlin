package core.network.repository
import core.network.models.AuditLogRequest
import core.network.models.AuditLogResponse
import core.network.models.BarcodeResponse
import core.network.models.FetchAi
import core.network.models.BarcodeLookupQuery
import core.network.models.BarcodeLookupResponse
import core.network.models.GenerationLogRequest
import core.network.models.LocationDatas
import core.network.models.NominatimResponse
import core.network.models.ScanLogCreateRequest
import core.util.ScanAuditLog
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.encodeURLParameter
import network.ApiClient


object AppRepository {
    suspend fun fetchAI(): Result<List<FetchAi>> {
        return try {
            val response = ApiClient.get<List<FetchAi>>(
                endpoint = "/company-configs/gs1/ai-data"
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateBarcode(
        bcType: String,
        data: String
    ): Result<String> {
        return try {

            // ✅ Encode safely
            val encodedData = data.encodeURLParameter()

            val fullUrl =
                "https://dlhub.8aiku.com/gen/gen-barcode?bc_type=$bcType&data=$encodedData"
            // ✅ Call external API
            val response = ApiClient.get<BarcodeResponse>(endpoint = fullUrl)

            // ✅ Build preview URL
            val imageUrl =
                "https://dlhub.8aiku.com/gen/download-image" +
                        "?folder_variable=TMP_IMAGE_FOLDER" +
                        "&filename=${response.filename}"

            Result.success(imageUrl)

        } catch (e: Exception) {
            e.printStackTrace() // ✅ debug help
            Result.failure(Exception("Failed to generate barcode"))
        }
    }

    suspend fun sendAuditLog(
        body: AuditLogRequest
    ): Result<Unit> {
        return try {
            val response = ApiClient.post<AuditLogRequest, AuditLogResponse>(
                endpoint = "/companies/barcode/create",
                payload = body
            )

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendScanCreateLog(
        body: ScanLogCreateRequest,
    ): Result<Unit> {
        ScanAuditLog.line(
            "POST /companies/barcode/create event=${body.event_type} user=${body.user_id} company=${body.company_id}",
        )
        ScanAuditLog.line("body=${ScanAuditLog.formatRequestBody(body)}")
        return try {
            val response = ApiClient.post<ScanLogCreateRequest, AuditLogResponse>(
                endpoint = "/companies/barcode/create",
                payload = body,
            )
            ScanAuditLog.line("POST OK success=${response.success} msg=${response.message}")
            Result.success(Unit)
        } catch (e: Exception) {
            ScanAuditLog.line("POST FAILED ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun sendGenerationLog(
        body: GenerationLogRequest
    ): Result<Unit> {
        return try {
            ApiClient.post<GenerationLogRequest, AuditLogResponse>(
                endpoint = "/companies/barcode/generation",
                payload = body
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun lookupBarcodeLogs(query: BarcodeLookupQuery): Result<BarcodeLookupResponse> {
        return try {
            if (!query.canOpenTrackTrace()) {
                return Result.failure(IllegalArgumentException("lookup query is empty"))
            }
            val qs = buildBarcodeLookupQueryString(query)
            if (qs.isBlank()) {
                return Result.failure(IllegalArgumentException("lookup query string is empty"))
            }
            val response = ApiClient.get<BarcodeLookupResponse>(
                endpoint = "/companies/barcode/logs/lookup?$qs",
            )
            Result.success(response)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun buildBarcodeLookupQueryString(query: BarcodeLookupQuery): String {
        if (query.hasStructured()) {
            val parts = mutableListOf<String>()
            if (query.gtin.isNotBlank()) parts.add("gtin=" + query.gtin.encodeURLParameter())
            if (query.batch.isNotBlank()) parts.add("batch=" + query.batch.encodeURLParameter())
            if (query.serial.isNotBlank()) parts.add("serial=" + query.serial.encodeURLParameter())
            return parts.joinToString("&")
        }
        val key = query.legacyKey.trim()
        if (key.isBlank()) return ""
        // Prefer `gtin=` for bare numeric GTIN (matches dedicated GTIN endpoint style).
        if (key.matches(Regex("""^\d{8,14}$"""))) {
            return "gtin=" + key.encodeURLParameter()
        }
        return "key=" + key.encodeURLParameter()
    }

    suspend fun getLocationDetails(
        latitude: Double,
        longitude: Double
    ): Result<LocationDatas> {
        return try {
            val fullUrl =
                "https://nominatim.openstreetmap.org/reverse" +
                        "?lat=$latitude" +
                        "&lon=$longitude" +
                        "&format=json"
            val response = ApiClient.client.get(fullUrl) {
                headers {
                    append("User-Agent", "SakkshAsset/1.0 (android; contact@sakksh.com)")
                    append("Accept", "application/json")
                    append("Accept-Language", "en")
                }
            }.body<NominatimResponse>()

            val display = response.display_name.trim()
            val cityValue = response.address.city
                ?: response.address.town
                ?: response.address.village
                ?: response.address.county
                ?: display.split(",").firstOrNull()?.trim()

            val location = LocationDatas(
                latitude = latitude,
                longitude = longitude,
                displayName = display.ifBlank { "Unknown" },
                city = cityValue,
                state = response.address.state,
                country = response.address.country
            )

            Result.success(location)

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Failed to fetch location details"))
        }
    }

}