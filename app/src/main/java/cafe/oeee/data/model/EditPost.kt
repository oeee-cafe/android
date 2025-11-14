package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EditPostRequest(
    @Json(name = "title") val title: String,
    @Json(name = "content") val content: String,
    @Json(name = "hashtags") val hashtags: String?,
    @Json(name = "is_sensitive") val isSensitive: Boolean,
    @Json(name = "allow_relay") val allowRelay: Boolean
)

@JsonClass(generateAdapter = true)
data class EditPostResponse(
    @Json(name = "success") val success: Boolean
)
