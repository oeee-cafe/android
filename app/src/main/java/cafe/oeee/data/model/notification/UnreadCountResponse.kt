package cafe.oeee.data.model.notification

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UnreadCountResponse(
    @Json(name = "count") val count: Long
)
