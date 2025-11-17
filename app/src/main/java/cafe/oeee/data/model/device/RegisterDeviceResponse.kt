package cafe.oeee.data.model.device

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegisterDeviceResponse(
    @Json(name = "id") val id: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "platform") val platform: String,
    @Json(name = "created_at") val createdAt: String
)
