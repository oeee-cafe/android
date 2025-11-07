package cafe.oeee.data.model.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LogoutRequest(
    @Json(name = "device_token") val deviceToken: String?
)
