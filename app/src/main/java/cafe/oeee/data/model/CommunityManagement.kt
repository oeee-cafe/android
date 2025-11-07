package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// MARK: - Community Member Models

@JsonClass(generateAdapter = true)
data class CommunityMembersListResponse(
    @Json(name = "members")
    val members: List<CommunityMember>
)

@JsonClass(generateAdapter = true)
data class CommunityMember(
    @Json(name = "id")
    val id: String,
    @Json(name = "user_id")
    val userId: String,
    @Json(name = "username")
    val username: String,
    @Json(name = "display_name")
    val displayName: String,
    @Json(name = "avatar_url")
    val avatarUrl: String?,
    @Json(name = "role")
    val role: String,
    @Json(name = "joined_at")
    val joinedAt: String,
    @Json(name = "invited_by_username")
    val invitedByUsername: String?
)

// MARK: - Invitation Models

@JsonClass(generateAdapter = true)
data class UserInvitationsListResponse(
    @Json(name = "invitations")
    val invitations: List<UserInvitation>
)

@JsonClass(generateAdapter = true)
data class UserInvitation(
    @Json(name = "id")
    val id: String,
    @Json(name = "community")
    val community: InvitationCommunityInfo,
    @Json(name = "inviter")
    val inviter: InvitationUserInfo,
    @Json(name = "created_at")
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class InvitationCommunityInfo(
    @Json(name = "id")
    val id: String,
    @Json(name = "name")
    val name: String,
    @Json(name = "slug")
    val slug: String,
    @Json(name = "description")
    val description: String,
    @Json(name = "visibility")
    val visibility: String
)

@JsonClass(generateAdapter = true)
data class InvitationUserInfo(
    @Json(name = "id")
    val id: String,
    @Json(name = "username")
    val username: String,
    @Json(name = "display_name")
    val displayName: String,
    @Json(name = "avatar_url")
    val avatarUrl: String?
)

@JsonClass(generateAdapter = true)
data class CommunityInvitationsListResponse(
    @Json(name = "invitations")
    val invitations: List<CommunityInvitation>
)

@JsonClass(generateAdapter = true)
data class CommunityInvitation(
    @Json(name = "id")
    val id: String,
    @Json(name = "community_id")
    val communityId: String,
    @Json(name = "invitee")
    val invitee: InvitationUserInfo,
    @Json(name = "inviter")
    val inviter: InvitationUserInfo,
    @Json(name = "created_at")
    val createdAt: String
)

// MARK: - Community CRUD Models

@JsonClass(generateAdapter = true)
data class CreateCommunityRequest(
    @Json(name = "name")
    val name: String,
    @Json(name = "slug")
    val slug: String,
    @Json(name = "description")
    val description: String,
    @Json(name = "visibility")
    val visibility: String
)

@JsonClass(generateAdapter = true)
data class CreateCommunityResponse(
    @Json(name = "community")
    val community: CommunityInfo
)

// Note: CommunityInfo is defined in CommunityDetail.kt to avoid duplication

@JsonClass(generateAdapter = true)
data class UpdateCommunityRequest(
    @Json(name = "name")
    val name: String,
    @Json(name = "description")
    val description: String,
    @Json(name = "visibility")
    val visibility: String
)

@JsonClass(generateAdapter = true)
data class InviteUserRequest(
    @Json(name = "login_name")
    val loginName: String
)
