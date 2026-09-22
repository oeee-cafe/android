package cafe.oeee.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** The few API calls the app makes natively; everything else is the site in the web views. */
interface ApiService {
    @GET("/api/v1/auth/me")
    suspend fun getCurrentUser(): CurrentUser

    @GET("/api/v1/notifications/unread-count")
    suspend fun getUnreadNotificationCount(): UnreadCountResponse

    @GET("/api/v1/invitations")
    suspend fun getUserInvitations(): InvitationsResponse

    @POST("/api/v1/devices")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): RegisterDeviceResponse

    @DELETE("/api/v1/devices/{deviceToken}")
    suspend fun deleteDevice(@Path("deviceToken") deviceToken: String)
}

@JsonClass(generateAdapter = true)
data class CurrentUser(
    @Json(name = "id") val id: String,
    @Json(name = "login_name") val loginName: String,
    @Json(name = "display_name") val displayName: String
)

@JsonClass(generateAdapter = true)
data class UnreadCountResponse(
    @Json(name = "count") val count: Long
)

@JsonClass(generateAdapter = true)
data class InvitationsResponse(
    @Json(name = "invitations") val invitations: List<Any>
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "platform") val platform: String
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceResponse(
    @Json(name = "id") val id: String
)
