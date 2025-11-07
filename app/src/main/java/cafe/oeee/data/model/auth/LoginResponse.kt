package cafe.oeee.data.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "user") val user: CurrentUser?,
    @Json(name = "error") val error: String?
)
