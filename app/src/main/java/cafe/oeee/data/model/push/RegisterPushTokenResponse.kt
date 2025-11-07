package cafe.oeee.data.model.push

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterPushTokenResponse(
    @Json(name = "id") val id: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "platform") val platform: String,
    @Json(name = "created_at") val createdAt: String
)
