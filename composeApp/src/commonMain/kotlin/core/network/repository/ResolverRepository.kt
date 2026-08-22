package core.network.repository

import core.network.models.BarcodeAuthRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import network.ApiClient
import network.Config

/**
 * Digital Link resolver endpoints.
 *
 * CMS pages and the product config are author-defined JSON with no fixed schema,
 * so those responses stay as [JsonElement] and are read through the binding-path
 * helpers in `resolver.cms.CmsCore`.
 */
object ResolverRepository {

    suspend fun authenticateBarcode(
        barcodeData: String,
        encryptedText: String,
        companyId: String,
    ): Result<JsonObject?> {
        return try {
            val response = ApiClient.post<BarcodeAuthRequest, JsonElement>(
                endpoint = "/productmaster/scan/authenticate",
                payload = BarcodeAuthRequest(
                    barcode_data = barcodeData,
                    encrypted_text = encryptedText,
                    company_id = companyId,
                ),
            )
            Result.success(firstAuthRecord(response))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /** The API answers with a bare array, `{ "data": [...] }`, or a single object. */
    private fun firstAuthRecord(payload: JsonElement): JsonObject? = when (payload) {
        is JsonArray -> payload.firstOrNull() as? JsonObject
        is JsonObject -> {
            val data = payload["data"]
            if (data is JsonArray) data.firstOrNull() as? JsonObject else payload
        }
        else -> null
    }

    suspend fun fetchCmsPage(gtin: String, companyId: String): Result<JsonElement> {
        return try {
            val url = Config.BASE_URL + "/ratifye/pages/resolve/by-gtin" +
                "?companyId=" + companyId.encodeURLParameter() +
                "&gtin=" + gtin.encodeURLParameter()
            val response = ApiClient.client.get(url) {
                headers { append("Accept", "application/json") }
            }.body<JsonElement>()
            Result.success(response)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun fetchProductDetails(
        gtin: String,
        companyId: String,
        batch: String? = null,
        serial: String? = null,
    ): Result<JsonObject> {
        return try {
            val qs = buildString {
                append("?companyId=").append(companyId.encodeURLParameter())
                append("&gtin=").append(gtin.encodeURLParameter())
                if (!batch.isNullOrBlank()) append("&batch=").append(batch.encodeURLParameter())
                if (!serial.isNullOrBlank()) append("&serial=").append(serial.encodeURLParameter())
            }
            val url = Config.BASE_URL + "/productmaster/details/by-gtin" + qs
            val response = ApiClient.client.get(url) {
                headers { append("Accept", "application/json") }
            }.body<JsonElement>()
            Result.success((response as? JsonObject) ?: JsonObject(emptyMap()))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun sendConfigPayload(payload: JsonObject): Result<JsonElement> {
        return try {
            val response = ApiClient.post<JsonObject, JsonElement>(
                endpoint = "/productmaster/config",
                payload = payload,
            )
            Result.success(response)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
