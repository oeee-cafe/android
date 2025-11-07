package cafe.oeee.data.model.notification

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarkAllReadResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "count") val count: Long
)
