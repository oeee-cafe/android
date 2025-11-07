package cafe.oeee.data.model.notification

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarkNotificationReadResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "notification") val notification: NotificationItem?,
    @Json(name = "error") val error: String?
)
