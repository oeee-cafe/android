package cafe.oeee.ui.notifications

import android.content.Context
import cafe.oeee.R
import cafe.oeee.data.model.notification.NotificationItem
import cafe.oeee.data.model.notification.NotificationType

fun NotificationItem.getDisplayText(context: Context): String {
    return when (notificationType) {
        NotificationType.COMMENT -> context.getString(R.string.notification_comment, actorName)
        NotificationType.COMMENT_REPLY -> context.getString(R.string.notification_comment_reply, actorName)
        NotificationType.REACTION -> {
            reactionEmoji?.let {
                context.getString(R.string.notification_reaction_emoji, actorName, it)
            } ?: context.getString(R.string.notification_reaction, actorName)
        }
        NotificationType.FOLLOW -> context.getString(R.string.notification_follow, actorName)
        NotificationType.POST_REPLY -> context.getString(R.string.notification_post_reply, actorName)
        NotificationType.GUESTBOOK_ENTRY -> context.getString(R.string.notification_guestbook_entry, actorName)
        NotificationType.GUESTBOOK_REPLY -> context.getString(R.string.notification_guestbook_reply, actorName)
        NotificationType.MENTION -> context.getString(R.string.notification_mention, actorName)
        null -> "" // Should never happen as null types are filtered out, but handle defensively
    }
}
