package core.network.repository
import core.network.models.AuditLogRequest
import core.network.models.AuditLogResponse
import core.network.models.BarcodeResponse
import core.network.models.FetchAi
import core.network.models.LocationDatas
import core.network.models.NominatimResponse
import network.ApiClient
import io.ktor.http.*


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
            val response = ApiClient.get<NominatimResponse>(
                endpoint = fullUrl
            )

            val cityValue = response.address.city
                ?: response.address.town
                ?: response.address.village
                ?: response.address.county
                ?: response.display_name.split(",").firstOrNull()

            val location = LocationDatas(
                latitude = latitude,
                longitude = longitude,
                displayName = response.display_name,
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