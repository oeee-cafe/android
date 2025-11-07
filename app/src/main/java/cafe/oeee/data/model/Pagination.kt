package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Pagination(
    @Json(name = "offset")
    val offset: Int,
    @Json(name = "limit")
    val limit: Int,
    @Json(name = "total")
    val total: Int?,
    @Json(name = "has_more")
    val hasMore: Boolean
)
