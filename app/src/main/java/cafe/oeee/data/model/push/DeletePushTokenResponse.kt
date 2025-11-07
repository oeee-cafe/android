package cafe.oeee.data.model.push

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeletePushTokenResponse(
    @Json(name = "success") val success: Boolean
)
