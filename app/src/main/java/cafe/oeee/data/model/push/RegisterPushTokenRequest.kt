package cafe.oeee.data.model.push

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterPushTokenRequest(
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "platform") val platform: String
)
