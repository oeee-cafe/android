package cafe.oeee.data.model.device

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeleteDeviceResponse(
    @Json(name = "success") val success: Boolean
)
