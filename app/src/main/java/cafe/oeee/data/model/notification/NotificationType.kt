package cafe.oeee.data.model.notification

import com.squareup.moshi.Json

enum class NotificationType {
    @Json(name = "Comment")
    COMMENT,

    @Json(name = "CommentReply")
    COMMENT_REPLY,

    @Json(name = "Reaction")
    REACTION,

    @Json(name = "Follow")
    FOLLOW,

    @Json(name = "GuestbookEntry")
    GUESTBOOK_ENTRY,

    @Json(name = "GuestbookReply")
    GUESTBOOK_REPLY,

    @Json(name = "Mention")
    MENTION,

    @Json(name = "PostReply")
    POST_REPLY
}
