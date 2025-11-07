package cafe.oeee.data.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignupRequest(
    @Json(name = "login_name") val loginName: String,
    @Json(name = "password") val password: String,
    @Json(name = "display_name") val displayName: String
)
