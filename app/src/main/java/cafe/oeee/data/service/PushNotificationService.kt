package cafe.oeee.data.service

import android.util.Log
import android.webkit.CookieManager
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.remote.RegisterDeviceRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Registers this device's FCM token for the signed-in user, and tells the site which token
 * it is: signing out on the site's own page then unregisters it there (POST /logout reads
 * the `oeee_device` cookie), with no need for the app to catch the page on its way out.
 */
object PushNotificationService {
    private const val TAG = "PushNotificationService"
    private const val DEVICE_COOKIE = "oeee_device"

    /** As long as a session could last; the token is registered again on every sign-in anyway. */
    private const val DEVICE_COOKIE_MAX_AGE = 400L * 24 * 60 * 60

    /**
     * Registers the FCM token with the backend for whoever is signed in. Called after signing in,
     * and from FirebaseMessagingService when the token is refreshed.
     */
    suspend fun registerFcmToken(token: String? = null) {
        if (!AuthService.isAuthenticated.value) return
        try {
            val fcmToken = token ?: FirebaseMessaging.getInstance().token.await()
            ApiClient.apiService.registerDevice(
                RegisterDeviceRequest(deviceToken = fcmToken, platform = "android")
            )
            setDeviceCookie(fcmToken, DEVICE_COOKIE_MAX_AGE)
            Log.d(TAG, "Registered device")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token", e)
        }
    }

    /** Signed out: the site has deleted the device, so the cookie naming it goes too. */
    fun forgetDevice() {
        setDeviceCookie("", 0)
    }

    /**
     * Only for the site's own requests, and out of its scripts' reach: the page has no need
     * to read the token, and one that could would be able to hand it to anyone.
     */
    private fun setDeviceCookie(token: String, maxAge: Long) {
        val cookies = CookieManager.getInstance()
        cookies.setCookie(
            ApiClient.BASE_URL,
            "$DEVICE_COOKIE=$token; Path=/; Max-Age=$maxAge; Secure; HttpOnly; SameSite=Lax"
        )
        cookies.flush()
    }
}
