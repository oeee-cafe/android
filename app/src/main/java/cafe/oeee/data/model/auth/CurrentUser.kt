package cafe.oeee.data.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CurrentUser(
    @Json(name = "id") val id: String,
    @Json(name = "login_name") val loginName: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "email") val email: String?,
    @Json(name = "email_verified_at") val emailVerifiedAt: String?,
    @Json(name = "banner_id") val bannerId: String?,
    @Json(name = "preferred_language") val preferredLanguage: String?
)
