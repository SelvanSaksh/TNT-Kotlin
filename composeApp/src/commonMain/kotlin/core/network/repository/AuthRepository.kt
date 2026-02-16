package network.repository

import network.ApiClient
import network.models.SendOtpRequest
import network.models.SendOtpResponse
import network.models.VerifyOtpRequest
import network.models.VerifyOtpResponse

object AuthRepository {


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

            println("Sending OTP verification request...")
            println("OTP: $otp")
            println("Identifier: $identifier")

            val response = ApiClient.post<VerifyOtpRequest, VerifyOtpResponse>(
                endpoint = "/auth/otp-verification",
                payload = VerifyOtpRequest(
                    otp = otp,
                )
            )

            println("VERIFY SUCCESS RESPONSE: $response")

            Result.success(response)

        } catch (e: Exception) {

            println("VERIFY EXCEPTION: ${e.message}")
            e.printStackTrace()

            Result.failure(e)
        }
    }
}
