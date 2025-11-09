package cafe.oeee.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DraftPost(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String?,
    @Json(name = "image_url") val imageUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "community_id") val communityId: String?,
    @Json(name = "width") val width: Int,
    @Json(name = "height") val height: Int
)

@JsonClass(generateAdapter = true)
data class DraftPostsResponse(
    @Json(name = "drafts") val drafts: List<DraftPost>
)
