package cafe.oeee.data.model.notification

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarkNotificationReadResponse(
    @Json(name = "notification") val notification: NotificationItem
)
