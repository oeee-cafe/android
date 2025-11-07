package cafe.oeee.data.model.notification

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationsResponse(
    @Json(name = "notifications") val notifications: List<NotificationItem>,
    @Json(name = "total") val total: Int,
    @Json(name = "has_more") val hasMore: Boolean
)
