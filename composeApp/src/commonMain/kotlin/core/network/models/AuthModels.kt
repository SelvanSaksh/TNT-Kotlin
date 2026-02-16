package network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendOtpRequest(
    val email: String  // Email address
)

@Serializable
data class SendOtpResponse(
    val isAutoGen: Boolean,
    val otp: String,
    val expiresAt: String,
    val email: String,
    val userId: Int
)

@Serializable
data class VerifyOtpRequest(
    val otp: String
)

@Serializable
data class VerifyOtpResponse(
    val message: String,
    val userId: Int,
    val userEmail: String,
    val userDetail: UserDetail,
    val accessToken: String
)

@Serializable
data class UserDetail(

    val id: Int,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: Int,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("updated_at")
    val updatedAt: String,

    @SerialName("companyid")
    val companyId: Int,

    val status: Int,

    @SerialName("access_modules")
    val accessModules: List<Module> = emptyList(),

    val locations: List<Location> = emptyList()
)

@Serializable
data class Module(
    val id: Int,
    val name: String,
    val moduleType: String
)
