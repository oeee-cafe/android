package cafe.oeee.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The one API call the app makes natively, registering the device for push; everything else
 * is the site in the web views, which also tells the app what it shows, who is signed in
 * included (app_bridge.jinja in oeee-cafe/web).
 */
interface ApiService {
    @POST("/api/v1/devices")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): RegisterDeviceResponse
}

@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "platform") val platform: String
)

@JsonClass(generateAdapter = true)
data class RegisterDeviceResponse(
    @Json(name = "id") val id: String
)
