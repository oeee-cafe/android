package cafe.oeee.data.model

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
data class ProfileUser(
    @Json(name = "id") val id: String,
    @Json(name = "login_name") val loginName: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "is_following") val isFollowing: Boolean
)

@JsonClass(generateAdapter = true)
data class ProfileBanner(
    @Json(name = "id") val id: String,
    @Json(name = "image_filename") val imageFilename: String,
    @Json(name = "image_url") val imageUrl: String
)

@JsonClass(generateAdapter = true)
data class ProfilePost(
    @Json(name = "id") val id: String,
    @Json(name = "image_url") val imageUrl: String,
    @Json(name = "image_width") val imageWidth: Int,
    @Json(name = "image_height") val imageHeight: Int
)

@Parcelize
@JsonClass(generateAdapter = true)
data class ProfileFollowing(
    @Json(name = "id") val id: String,
    @Json(name = "login_name") val loginName: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "banner_image_url") val bannerImageUrl: String?,
    @Json(name = "banner_image_width") val bannerImageWidth: Int?,
    @Json(name = "banner_image_height") val bannerImageHeight: Int?
) : Parcelable

@JsonClass(generateAdapter = true)
data class ProfileLink(
    @Json(name = "id") val id: String,
    @Json(name = "url") val url: String,
    @Json(name = "description") val description: String?
)

@JsonClass(generateAdapter = true)
data class ProfileDetail(
    @Json(name = "user") val user: ProfileUser,
    @Json(name = "banner") val banner: ProfileBanner?,
    @Json(name = "posts") val posts: List<ProfilePost>,
    @Json(name = "pagination") val pagination: Pagination,
    @Json(name = "followings") val followings: List<ProfileFollowing>,
    @Json(name = "total_followings") val totalFollowings: Int,
    @Json(name = "links") val links: List<ProfileLink>
)

@JsonClass(generateAdapter = true)
data class ProfileFollowingsListResponse(
    @Json(name = "followings") val followings: List<ProfileFollowing>,
    @Json(name = "pagination") val pagination: Pagination
)
