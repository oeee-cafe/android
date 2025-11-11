package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MovableCommunitiesResponse(
    @Json(name = "communities")
    val communities: List<MovableCommunity>
)

@JsonClass(generateAdapter = true)
data class MovableCommunity(
    @Json(name = "id")
    val id: String?,  // null for "Personal Post" option
    @Json(name = "name")
    val name: String,
    @Json(name = "slug")
    val slug: String?,
    @Json(name = "visibility")
    val visibility: String?,
    @Json(name = "background_color")
    val backgroundColor: String?,
    @Json(name = "foreground_color")
    val foregroundColor: String?,
    @Json(name = "owner_login_name")
    val ownerLoginName: String?,
    @Json(name = "owner_display_name")
    val ownerDisplayName: String?,
    @Json(name = "has_participated")
    val hasParticipated: Boolean?  // null for "Personal Post" option, true/false for communities
) {
    val isPersonalPost: Boolean
        get() = id == null

    val isTwoTone: Boolean
        get() = backgroundColor != null && foregroundColor != null

    val listId: String
        get() = id ?: "personal"
}

@JsonClass(generateAdapter = true)
data class MoveCommunityRequest(
    @Json(name = "community_id")
    val communityId: String?  // null to move to personal posts
)
