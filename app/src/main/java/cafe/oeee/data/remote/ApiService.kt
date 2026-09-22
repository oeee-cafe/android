package cafe.oeee.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * The few API calls the app makes natively; everything else is the site in the web views,
 * which also tells the app what it shows (app_bridge.jinja in oeee-cafe/web).
 */
interface ApiService {
    /** Who is signed in, asked once at start, before any page has been able to say. */
    @GET("/api/v1/auth/me")
    suspend fun getCurrentUser(): CurrentUser

    @POST("/api/v1/devices")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): RegisterDeviceResponse
}

@JsonClass(generateAdapter = true)
data class CurrentUser(
    @Json(name = "id") val id: String,
    @Json(name = "login_name") val loginName: String,
    @Json(name = "display_name") val displayName: String
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
