package cafe.oeee.data.model.notification

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class NotificationItem(
    @Json(name = "id") val id: String,
    @Json(name = "recipient_id") val recipientId: String,
    @Json(name = "actor_id") val actorId: String,
    @Json(name = "actor_name") val actorName: String,
    @Json(name = "actor_handle") val actorHandle: String,
    @Json(name = "notification_type") val notificationType: NotificationType,
    @Json(name = "post_id") val postId: String?,
    @Json(name = "comment_id") val commentId: String?,
    @Json(name = "reaction_iri") val reactionIri: String?,
    @Json(name = "reaction_emoji") val reactionEmoji: String?,
    @Json(name = "guestbook_entry_id") val guestbookEntryId: String?,
    @Json(name = "read_at") val readAt: Date?,
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "post_title") val postTitle: String?,
    @Json(name = "post_author_login_name") val postAuthorLoginName: String?,
    @Json(name = "post_image_filename") val postImageFilename: String?,
    @Json(name = "post_image_url") val postImageUrl: String?,
    @Json(name = "post_image_width") val postImageWidth: Int?,
    @Json(name = "post_image_height") val postImageHeight: Int?,
    @Json(name = "comment_content") val commentContent: String?,
    @Json(name = "comment_content_html") val commentContentHtml: String?,
    @Json(name = "guestbook_content") val guestbookContent: String?
) {
    val isRead: Boolean
        get() = readAt != null
}
