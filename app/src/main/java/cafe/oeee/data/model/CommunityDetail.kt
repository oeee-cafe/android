package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CommunityInfo(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "slug") val slug: String,
    @Json(name = "description") val description: String?,
    @Json(name = "visibility") val visibility: String,
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "background_color") val backgroundColor: String?,
    @Json(name = "foreground_color") val foregroundColor: String?
)

@JsonClass(generateAdapter = true)
data class CommunityStats(
    @Json(name = "total_posts") val totalPosts: Int,
    @Json(name = "total_contributors") val totalContributors: Int,
    @Json(name = "total_comments") val totalComments: Int
)

@JsonClass(generateAdapter = true)
data class CommunityDetailPost(
    @Json(name = "id") val id: String,
    @Json(name = "image_url") val imageUrl: String,
    @Json(name = "image_width") val imageWidth: Int,
    @Json(name = "image_height") val imageHeight: Int
)

@JsonClass(generateAdapter = true)
data class CommunityDetail(
    @Json(name = "community") val community: CommunityInfo,
    @Json(name = "stats") val stats: CommunityStats,
    @Json(name = "posts") val posts: List<CommunityDetailPost>,
    @Json(name = "pagination") val pagination: Pagination,
    @Json(name = "comments") val comments: List<RecentComment>
)
