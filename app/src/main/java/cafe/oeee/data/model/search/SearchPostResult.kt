package cafe.oeee.data.model.search

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchPostResult(
    @Json(name = "id") val id: String,
    @Json(name = "image_url") val imageUrl: String,
    @Json(name = "image_width") val imageWidth: Int?,
    @Json(name = "image_height") val imageHeight: Int?,
    @Json(name = "is_sensitive") val isSensitive: Boolean
)
