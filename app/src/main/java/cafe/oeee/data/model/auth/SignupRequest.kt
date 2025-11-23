package cafe.oeee.data.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignupRequest(
    @Json(name = "login_name") val loginName: String,
    val password: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "preferred_language") val preferredLanguage: String? = null
)
