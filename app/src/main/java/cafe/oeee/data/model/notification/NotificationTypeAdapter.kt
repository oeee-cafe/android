package cafe.oeee.data.model.notification

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Custom Moshi adapter for NotificationType that gracefully handles unknown notification types
 * by returning null instead of throwing an exception.
 *
 * This allows the app to ignore notifications with unknown types that may be added
 * by the server in future versions.
 */
class NotificationTypeAdapter {
    @FromJson
    fun fromJson(value: String): NotificationType? {
        return try {
            when (value) {
                "Comment" -> NotificationType.COMMENT
                "CommentReply" -> NotificationType.COMMENT_REPLY
                "Reaction" -> NotificationType.REACTION
                "Follow" -> NotificationType.FOLLOW
                "GuestbookEntry" -> NotificationType.GUESTBOOK_ENTRY
                "GuestbookReply" -> NotificationType.GUESTBOOK_REPLY
                "Mention" -> NotificationType.MENTION
                "PostReply" -> NotificationType.POST_REPLY
                else -> null // Unknown notification type - will be filtered out
            }
        } catch (e: Exception) {
            null // Return null for any parsing errors
        }
    }

    @ToJson
    fun toJson(type: NotificationType?): String? {
        return when (type) {
            NotificationType.COMMENT -> "Comment"
            NotificationType.COMMENT_REPLY -> "CommentReply"
            NotificationType.REACTION -> "Reaction"
            NotificationType.FOLLOW -> "Follow"
            NotificationType.GUESTBOOK_ENTRY -> "GuestbookEntry"
            NotificationType.GUESTBOOK_REPLY -> "GuestbookReply"
            NotificationType.MENTION -> "Mention"
            NotificationType.POST_REPLY -> "PostReply"
            null -> null
        }
    }
}
