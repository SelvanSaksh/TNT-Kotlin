package network.repository

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            val httpResponse: HttpResponse = ApiClient.client.post(Config.BASE_URL + "/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    SendOtpRequest(
                        email = identifier,
                        fromMobile = true,
                    ),
                )
            }

            val rawBody: String = httpResponse.body()
            println("LOGIN_LOG: ◀ status=${httpResponse.status.value} body=$rawBody")

            if (!httpResponse.status.isSuccess()) {
                return Result.failure(
                    IllegalStateException(parseApiMessage(rawBody) ?: "Login failed (${httpResponse.status.value})"),
                )
            }

            val decoded = json.decodeFromString(SendOtpResponse.serializer(), rawBody)
            if (!decoded.isAutoGen) {
                return Result.failure(
                    IllegalStateException("Login failed. Please check your email or phone number."),
                )
            }
            if (decoded.userId == null || decoded.userId <= 0) {
                return Result.failure(
                    IllegalStateException("Login failed. User id missing from server response."),
                )
            }

            Result.success(decoded)
        } catch (e: Exception) {
            println("LOGIN_LOG: ❌ sendOtp exception: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(
        identifier: String,
        otp: String,
        userId: Int? = null,
    ): Result<VerifyOtpResponse> {
        return try {
            val request = if (userId != null && userId > 0) {
                VerifyOtpRequest(otp = otp, userId = userId)
            } else {
                VerifyOtpRequest(otp = otp, email = identifier)
            }

            println("OTP_LOG: ▶ POST /auth/otp-verification request=$request")

            val httpResponse: HttpResponse = ApiClient.client.post(
                Config.BASE_URL + "/auth/otp-verification",
            ) {
                contentType(ContentType.Application.Json)
                setBody(request)
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
                    IllegalStateException(
                        parseApiMessage(rawBody) ?: "OTP verification failed: HTTP ${httpResponse.status.value}",
                    ),
                )
            }

            val decoded: VerifyOtpResponse = json.decodeFromString(
                VerifyOtpResponse.serializer(),
                rawBody,
            )
            println(
                "OTP_LOG: ✓ decoded userId=${decoded.userId} email=${decoded.userEmail} " +
                    "modulesIsNull=${decoded.modules == null} " +
                    "subscriptionId=${decoded.subscription?.id} " +
                    "locationDetailsCount=${decoded.locationDetails?.size ?: 0}",
            )

            Result.success(decoded)
        } catch (e: Exception) {
            println("OTP_LOG: ❌ verify exception: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun parseApiMessage(rawBody: String): String? {
        return runCatching {
            val element = json.parseToJsonElement(rawBody)
            when (element) {
                is JsonObject -> element["message"]?.jsonPrimitive?.content
                    ?: element["error"]?.jsonPrimitive?.content
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}

private fun io.ktor.http.HttpStatusCode.isSuccess(): Boolean = value in 200..299
