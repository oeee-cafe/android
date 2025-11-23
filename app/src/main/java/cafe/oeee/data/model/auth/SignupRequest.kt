package cafe.oeee.data.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignupRequest(
    val loginName: String,
    val password: String,
    val displayName: String,
    val preferredLanguage: String? = null
)
