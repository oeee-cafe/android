package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class ReactionCount(
    @Json(name = "emoji") val emoji: String,
    @Json(name = "count") val count: Int,
    @Json(name = "reacted_by_user") val reactedByUser: Boolean
)

@JsonClass(generateAdapter = true)
data class ImageInfo(
    @Json(name = "filename") val filename: String,
    @Json(name = "width") val width: Int,
    @Json(name = "height") val height: Int,
    @Json(name = "tool") val tool: String,
    @Json(name = "paint_duration") val paintDuration: String
) {
    val url: String
        get() {
            val prefix = filename.take(2)
            return "https://r2.oeee.cafe/image/$prefix/$filename"
        }
}

@JsonClass(generateAdapter = true)
data class AuthorInfo(
    @Json(name = "id") val id: String,
    @Json(name = "login_name") val loginName: String,
    @Json(name = "display_name") val displayName: String
)

@JsonClass(generateAdapter = true)
data class PostCommunityInfo(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "slug") val slug: String
)

@JsonClass(generateAdapter = true)
data class PostDetail(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String?,
    @Json(name = "content") val content: String?,
    @Json(name = "author") val author: AuthorInfo,
    @Json(name = "viewer_count") val viewerCount: Int,
    @Json(name = "image") val image: ImageInfo,
    @Json(name = "is_sensitive") val isSensitive: Boolean,
    @Json(name = "published_at_utc") val publishedAtUtc: Date?,
    @Json(name = "community") val community: PostCommunityInfo,
    @Json(name = "hashtags") val hashtags: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Comment(
    @Json(name = "id") val id: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "parent_comment_id") val parentCommentId: String?,
    @Json(name = "actor_id") val actorId: String,
    @Json(name = "content") val content: String?,
    @Json(name = "content_html") val contentHtml: String?,
    @Json(name = "actor_name") val actorName: String,
    @Json(name = "actor_handle") val actorHandle: String,
    @Json(name = "actor_login_name") val actorLoginName: String?,
    @Json(name = "is_local") val isLocal: Boolean,
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "updated_at") val updatedAt: Date,
    @Json(name = "deleted_at") val deletedAt: Date?,
    @Json(name = "children") val children: List<Comment> = emptyList()
) {
    val isDeleted: Boolean
        get() = deletedAt != null

    val shouldDisplay: Boolean
        get() = !isDeleted || children.isNotEmpty()

    val displayText: String
        get() = when {
            isDeleted -> "[deleted]"
            contentHtml != null -> contentHtml.htmlToPlainText()
            content != null -> content
            else -> "[deleted]"
        }

    private fun String.htmlToPlainText(): String {
        return this.replace(Regex("<[^>]*>"), "").trim()
    }
}

@JsonClass(generateAdapter = true)
data class ChildPostImage(
    @Json(name = "url") val url: String,
    @Json(name = "width") val width: Int,
    @Json(name = "height") val height: Int
)

@JsonClass(generateAdapter = true)
data class ChildPostAuthor(
    @Json(name = "id") val id: String,
    @Json(name = "login_name") val loginName: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "actor_handle") val actorHandle: String
)

@JsonClass(generateAdapter = true)
data class ChildPost(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String?,
    @Json(name = "content") val content: String?,
    @Json(name = "author") val author: ChildPostAuthor,
    @Json(name = "image") val image: ChildPostImage,
    @Json(name = "published_at") val publishedAt: Date?,
    @Json(name = "comments_count") val commentsCount: Int,
    @Json(name = "children") val children: List<ChildPost> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CommentsListResponse(
    @Json(name = "comments") val comments: List<Comment>,
    @Json(name = "pagination") val pagination: Pagination
)

@JsonClass(generateAdapter = true)
data class CreateCommentRequest(
    @Json(name = "content") val content: String,
    @Json(name = "parent_comment_id") val parentCommentId: String? = null
)

@JsonClass(generateAdapter = true)
data class PostDetailResponse(
    @Json(name = "post") val post: PostDetail,
    @Json(name = "parent_post") val parentPost: ChildPost?,
    @Json(name = "child_posts") val childPosts: List<ChildPost>,
    @Json(name = "reactions") val reactions: List<ReactionCount>
)
