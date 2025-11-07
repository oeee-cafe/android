package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CommunityPost(
    @Json(name = "id")
    val id: String,
    @Json(name = "image_url")
    val imageUrl: String,
    @Json(name = "image_width")
    val imageWidth: Int,
    @Json(name = "image_height")
    val imageHeight: Int,
    @Json(name = "is_sensitive")
    val isSensitive: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ActiveCommunity(
    @Json(name = "id")
    val id: String,
    @Json(name = "name")
    val name: String,
    @Json(name = "slug")
    val slug: String,
    @Json(name = "description")
    val description: String?,
    @Json(name = "visibility")
    val visibility: String,
    @Json(name = "owner_login_name")
    val ownerLoginName: String,
    @Json(name = "posts_count")
    val postsCount: Int?,
    @Json(name = "members_count")
    val membersCount: Int?,
    @Json(name = "recent_posts")
    val recentPosts: List<CommunityPost>
)

@JsonClass(generateAdapter = true)
data class ActiveCommunitiesResponse(
    @Json(name = "communities")
    val communities: List<ActiveCommunity>
)

@JsonClass(generateAdapter = true)
data class MyCommunitiesResponse(
    @Json(name = "communities")
    val communities: List<ActiveCommunity>
)

@JsonClass(generateAdapter = true)
data class PaginationMeta(
    @Json(name = "offset")
    val offset: Long,
    @Json(name = "limit")
    val limit: Long,
    @Json(name = "total")
    val total: Long?,
    @Json(name = "has_more")
    val hasMore: Boolean
)

@JsonClass(generateAdapter = true)
data class PublicCommunitiesResponse(
    @Json(name = "communities")
    val communities: List<ActiveCommunity>,
    @Json(name = "pagination")
    val pagination: PaginationMeta
)
