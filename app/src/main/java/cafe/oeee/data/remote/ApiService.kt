package cafe.oeee.data.remote

import cafe.oeee.data.model.ActiveCommunitiesResponse
import cafe.oeee.data.model.Comment
import cafe.oeee.data.model.CommentsListResponse
import cafe.oeee.data.model.CommunityDetail
import cafe.oeee.data.model.MyCommunitiesResponse
import cafe.oeee.data.model.PublicCommunitiesResponse
import cafe.oeee.data.model.CreateCommentRequest
import cafe.oeee.data.model.CommunityMembersListResponse
import cafe.oeee.data.model.CommunityInvitationsListResponse
import cafe.oeee.data.model.UserInvitationsListResponse
import cafe.oeee.data.model.CreateCommunityRequest
import cafe.oeee.data.model.CreateCommunityResponse
import cafe.oeee.data.model.UpdateCommunityRequest
import cafe.oeee.data.model.InviteUserRequest
import cafe.oeee.data.model.PostDetailResponse
import cafe.oeee.data.model.PostsResponse
import cafe.oeee.data.model.ProfileDetail
import cafe.oeee.data.model.ProfileFollowingsListResponse
import cafe.oeee.data.model.RecentCommentsResponse
import cafe.oeee.data.model.auth.CurrentUser
import cafe.oeee.data.model.auth.DeleteAccountRequest
import cafe.oeee.data.model.auth.DeleteAccountResponse
import cafe.oeee.data.model.auth.LoginRequest
import cafe.oeee.data.model.auth.LoginResponse
import cafe.oeee.data.model.auth.LogoutRequest
import cafe.oeee.data.model.auth.LogoutResponse
import cafe.oeee.data.model.auth.RequestEmailVerificationRequest
import cafe.oeee.data.model.auth.RequestEmailVerificationResponse
import cafe.oeee.data.model.auth.SignupRequest
import cafe.oeee.data.model.auth.SignupResponse
import cafe.oeee.data.model.auth.VerifyEmailCodeRequest
import cafe.oeee.data.model.auth.VerifyEmailCodeResponse
import cafe.oeee.data.model.notification.DeleteNotificationResponse
import cafe.oeee.data.model.notification.MarkAllReadResponse
import cafe.oeee.data.model.notification.MarkNotificationReadResponse
import cafe.oeee.data.model.notification.NotificationsResponse
import cafe.oeee.data.model.notification.UnreadCountResponse
import cafe.oeee.data.model.push.DeletePushTokenResponse
import cafe.oeee.data.model.push.RegisterPushTokenRequest
import cafe.oeee.data.model.push.RegisterPushTokenResponse
import cafe.oeee.data.model.reaction.ReactionResponse
import cafe.oeee.data.model.reaction.ReactorsResponse
import cafe.oeee.data.model.search.SearchResponse
import cafe.oeee.data.remote.model.DraftPostsResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("/api/v1/posts/public")
    suspend fun getPublicPosts(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 18
    ): PostsResponse

    @GET("/api/v1/posts/drafts")
    suspend fun getDraftPosts(): DraftPostsResponse

    @GET("/api/v1/communities/active")
    suspend fun getActiveCommunities(): ActiveCommunitiesResponse

    @GET("/api/v1/communities")
    suspend fun getCommunitiesList(): MyCommunitiesResponse

    @GET("/api/v1/communities/public")
    suspend fun getPublicCommunities(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): PublicCommunitiesResponse

    @GET("/api/v1/communities/search")
    suspend fun searchPublicCommunities(
        @Query("q") query: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): PublicCommunitiesResponse

    @GET("/api/v1/comments/latest")
    suspend fun getLatestComments(): RecentCommentsResponse

    @GET("/api/v1/posts/{postId}")
    suspend fun getPostDetail(
        @Path("postId") postId: String
    ): PostDetailResponse

    @GET("/api/v1/posts/{postId}/comments")
    suspend fun getPostComments(
        @Path("postId") postId: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): CommentsListResponse

    @POST("/api/v1/posts/{postId}/comments")
    suspend fun postComment(
        @Path("postId") postId: String,
        @Body request: CreateCommentRequest
    ): Comment

    @DELETE("/api/v1/comments/{commentId}")
    suspend fun deleteComment(
        @Path("commentId") commentId: String
    )

    @GET("/api/v1/profiles/{loginName}")
    suspend fun getProfileDetail(
        @Path("loginName") loginName: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 18
    ): ProfileDetail

    @GET("/api/v1/profiles/{loginName}/followings")
    suspend fun getProfileFollowings(
        @Path("loginName") loginName: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50
    ): ProfileFollowingsListResponse

    @POST("/api/v1/profiles/{loginName}/follow")
    suspend fun followProfile(
        @Path("loginName") loginName: String
    )

    @POST("/api/v1/profiles/{loginName}/unfollow")
    suspend fun unfollowProfile(
        @Path("loginName") loginName: String
    )

    @GET("/api/v1/communities/{slug}")
    suspend fun getCommunityDetail(
        @Path("slug") slug: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 18
    ): CommunityDetail

    // Authentication endpoints
    @POST("/api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @POST("/api/v1/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): LogoutResponse

    @POST("/api/v1/auth/signup")
    suspend fun signup(
        @Body request: SignupRequest
    ): SignupResponse

    @GET("/api/v1/auth/me")
    suspend fun getCurrentUser(): CurrentUser

    @HTTP(method = "DELETE", path = "/api/v1/account", hasBody = true)
    suspend fun deleteAccount(
        @Body request: DeleteAccountRequest
    ): DeleteAccountResponse

    @GET("/api/v1/account")
    suspend fun getAccount(): CurrentUser

    @POST("/api/v1/account/request-verify-email")
    suspend fun requestEmailVerification(
        @Body request: RequestEmailVerificationRequest
    ): RequestEmailVerificationResponse

    @POST("/api/v1/account/verify-email")
    suspend fun verifyEmailCode(
        @Body request: VerifyEmailCodeRequest
    ): VerifyEmailCodeResponse

    // Notification endpoints
    @GET("/api/v1/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): NotificationsResponse

    @GET("/api/v1/notifications/unread-count")
    suspend fun getUnreadNotificationCount(): UnreadCountResponse

    @POST("/api/v1/notifications/mark-all-read")
    suspend fun markAllNotificationsAsRead(): MarkAllReadResponse

    @POST("/api/v1/notifications/{notificationId}/mark-read")
    suspend fun markNotificationAsRead(
        @Path("notificationId") notificationId: String
    ): MarkNotificationReadResponse

    @DELETE("/api/v1/notifications/{notificationId}")
    suspend fun deleteNotification(
        @Path("notificationId") notificationId: String
    ): DeleteNotificationResponse

    // Search endpoint
    @GET("/api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null
    ): SearchResponse

    // Reaction endpoints
    @GET("/api/v1/posts/{postId}/reactions/{emoji}")
    suspend fun getPostReactionsByEmoji(
        @Path("postId") postId: String,
        @Path("emoji") emoji: String
    ): ReactorsResponse

    // Post deletion endpoint
    @DELETE("/api/v1/posts/{postId}")
    suspend fun deletePost(
        @Path("postId") postId: String
    )

    // Reaction endpoints
    @POST("/api/v1/posts/{postId}/reactions/{emoji}")
    suspend fun addReaction(
        @Path("postId") postId: String,
        @Path("emoji") emoji: String
    ): ReactionResponse

    @DELETE("/api/v1/posts/{postId}/reactions/{emoji}")
    suspend fun removeReaction(
        @Path("postId") postId: String,
        @Path("emoji") emoji: String
    ): ReactionResponse

    // Push token endpoints
    @POST("/api/v1/push-tokens")
    suspend fun registerPushToken(
        @Body request: RegisterPushTokenRequest
    ): RegisterPushTokenResponse

    @DELETE("/api/v1/push-tokens/{deviceToken}")
    suspend fun deletePushToken(
        @Path("deviceToken") deviceToken: String
    ): DeletePushTokenResponse

    // Community member management endpoints
    @GET("/api/v1/communities/{slug}/members")
    suspend fun getCommunityMembers(
        @Path("slug") slug: String
    ): CommunityMembersListResponse

    @POST("/api/v1/communities/{slug}/members")
    suspend fun inviteUser(
        @Path("slug") slug: String,
        @Body request: InviteUserRequest
    )

    @DELETE("/api/v1/communities/{slug}/members/{userId}")
    suspend fun removeMember(
        @Path("slug") slug: String,
        @Path("userId") userId: String
    )

    @GET("/api/v1/communities/{slug}/invitations")
    suspend fun getCommunityInvitations(
        @Path("slug") slug: String
    ): CommunityInvitationsListResponse

    @DELETE("/api/v1/communities/{slug}/invitations/{invitationId}")
    suspend fun retractInvitation(
        @Path("slug") slug: String,
        @Path("invitationId") invitationId: String
    ): retrofit2.Response<Void>

    // User invitations endpoints
    @GET("/api/v1/invitations")
    suspend fun getUserInvitations(): UserInvitationsListResponse

    @POST("/invitations/{id}/accept")
    suspend fun acceptInvitation(
        @Path("id") id: String
    )

    @POST("/invitations/{id}/reject")
    suspend fun rejectInvitation(
        @Path("id") id: String
    )

    // Community CRUD endpoints
    @POST("/api/v1/communities")
    suspend fun createCommunity(
        @Body request: CreateCommunityRequest
    ): CreateCommunityResponse

    @retrofit2.http.PUT("/api/v1/communities/{slug}")
    suspend fun updateCommunity(
        @Path("slug") slug: String,
        @Body request: UpdateCommunityRequest
    )

    @DELETE("/api/v1/communities/{slug}")
    suspend fun deleteCommunity(
        @Path("slug") slug: String
    )
}
