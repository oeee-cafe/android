package cafe.oeee.data.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RequestEmailVerificationRequest(
    @Json(name = "email") val email: String
)

@JsonClass(generateAdapter = true)
data class RequestEmailVerificationResponse(
    @Json(name = "challenge_id") val challengeId: String,
    @Json(name = "email") val email: String,
    @Json(name = "expires_in_seconds") val expiresInSeconds: Int
)

@JsonClass(generateAdapter = true)
data class VerifyEmailCodeRequest(
    @Json(name = "challenge_id") val challengeId: String,
    @Json(name = "token") val token: String
)
