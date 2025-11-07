package cafe.oeee.data.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RequestEmailVerificationRequest(
    @Json(name = "email") val email: String
)

@JsonClass(generateAdapter = true)
data class RequestEmailVerificationResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "challenge_id") val challengeId: String?,
    @Json(name = "email") val email: String?,
    @Json(name = "expires_in_seconds") val expiresInSeconds: Int?,
    @Json(name = "error") val error: String?
)

@JsonClass(generateAdapter = true)
data class VerifyEmailCodeRequest(
    @Json(name = "challenge_id") val challengeId: String,
    @Json(name = "token") val token: String
)

@JsonClass(generateAdapter = true)
data class VerifyEmailCodeResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String?,
    @Json(name = "error") val error: String?
)
