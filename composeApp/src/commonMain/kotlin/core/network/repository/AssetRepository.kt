package network.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import network.models.Asset
import network.Config
import network.HttpClientFactory
import core.storage.SessionManager

object AssetRepository {
    private val client: HttpClient by lazy {
        HttpClientFactory.httpClient
    }

    /**
     * Get assets by company ID
     */
    suspend fun getAssetsByCompany(
        companyId: Int,
        sessionManager: SessionManager
    ): Result<List<Asset>> {
        return try {
            val token = sessionManager.getAccessToken()
            
            println("AssetRepository: Fetching assets for company $companyId")
            println("AssetRepository: Token exists: ${token != null}")
            println("AssetRepository: URL: ${Config.BASE_URL}/assets/getAssetByComp/$companyId")
            
            val response = client.get("${Config.BASE_URL}/assets/getAssetByComp/$companyId") {
                headers {
                    if (token != null) {
                        append("Authorization", "Bearer $token")
                    }
                }
            }
            
            println("AssetRepository: Response status: ${response.status}")
            
            // Get raw body as string first to debug
            val bodyText = response.body<String>()
            println("AssetRepository: Raw response body (first 200 chars): ${bodyText.take(200)}")
            
            // Check if response is an error object or success array
            val trimmedBody = bodyText.trim()
            
            if (trimmedBody.startsWith("{")) {
                // This is an error response
                val errorResponse = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString<network.models.ApiErrorResponse>(bodyText)
                
                println("AssetRepository: API returned error: ${errorResponse.message}")
                throw Exception("API Error: ${errorResponse.message}")
            } else {
                // This is a success response (array)
                val assets: List<Asset> = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString(bodyText)
                
                println("AssetRepository: Successfully parsed ${assets.size} assets")
                Result.success(assets)
            }
        } catch (e: Exception) {
            println("AssetRepository Error: ${e.message}")
            println("AssetRepository Error type: ${e::class.simpleName}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
