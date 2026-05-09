package network.repository

import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import network.ApiClient
import network.Config
import network.models.SendOtpRequest
import network.models.SendOtpResponse
import network.models.VerifyOtpRequest
import network.models.VerifyOtpResponse

object AuthRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    suspend fun sendOtp(identifier: String): Result<SendOtpResponse> {
        return try {
            val response = ApiClient.post<SendOtpRequest, SendOtpResponse>(
                endpoint = "/auth/login",
                payload = SendOtpRequest(email = identifier)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(
        identifier: String,
        otp: String
    ): Result<VerifyOtpResponse> {

        return try {

            println("OTP_LOG: ▶ POST /auth/otp-verification identifier=$identifier otp=$otp")

            val httpResponse: HttpResponse = ApiClient.client.post(
                Config.BASE_URL + "/auth/otp-verification"
            ) {
                contentType(ContentType.Application.Json)
                setBody(VerifyOtpRequest(email = identifier, otp = otp))
            }

            val rawBody: String = httpResponse.body()
            val pretty = runCatching {
                val element = json.parseToJsonElement(rawBody)
                prettyJson.encodeToString(JsonElement.serializer(), element)
            }.getOrElse { rawBody }

            println("OTP_LOG: ◀ status=${httpResponse.status.value} headers:")
            httpResponse.headers.entries().forEach { (k, v) ->
                println("OTP_LOG:   $k: ${v.joinToString()}")
            }
            println("OTP_LOG: ◀ raw response body (len=${rawBody.length}):")
            println("OTP_VERIFY_RESPONSE_JSON:\n$pretty")

            if (!httpResponse.status.isSuccess()) {
                return Result.failure(
                    IllegalStateException("OTP verification failed: HTTP ${httpResponse.status.value}")
                )
            }

            val decoded: VerifyOtpResponse = json.decodeFromString(
                VerifyOtpResponse.serializer(),
                rawBody
            )
            println(
                "OTP_LOG: ✓ decoded userId=${decoded.userId} email=${decoded.userEmail} " +
                    "modulesIsNull=${decoded.modules == null} " +
                    "subscriptionId=${decoded.subscription?.id} " +
                    "locationDetailsCount=${decoded.locationDetails?.size ?: 0}"
            )

            Result.success(decoded)

        } catch (e: Exception) {
            println("OTP_LOG: ❌ verify exception: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

private fun io.ktor.http.HttpStatusCode.isSuccess(): Boolean = value in 200..299
