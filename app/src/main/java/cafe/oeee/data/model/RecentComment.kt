package cafe.oeee.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class RecentComment(
    @Json(name = "id")
    val id: String,
    @Json(name = "post_id")
    val postId: String,
    @Json(name = "actor_id")
    val actorId: String,
    @Json(name = "content")
    val content: String,
    @Json(name = "content_html")
    val contentHtml: String?,
    @Json(name = "actor_name")
    val actorName: String,
    @Json(name = "actor_handle")
    val actorHandle: String,
    @Json(name = "actor_login_name")
    val actorLoginName: String?,
    @Json(name = "is_local")
    val isLocal: Boolean,
    @Json(name = "created_at")
    val createdAt: Date,
    @Json(name = "post_title")
    val postTitle: String?,
    @Json(name = "post_author_login_name")
    val postAuthorLoginName: String,
    @Json(name = "post_image_url")
    val postImageUrl: String?,
    @Json(name = "post_image_width")
    val postImageWidth: Int?,
    @Json(name = "post_image_height")
    val postImageHeight: Int?
)

@JsonClass(generateAdapter = true)
data class RecentCommentsResponse(
    @Json(name = "comments")
    val comments: List<RecentComment>
)
