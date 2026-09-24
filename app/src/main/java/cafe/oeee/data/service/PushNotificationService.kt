package cafe.oeee.data.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * This device's FCM token, which the app gets and the site registers. The app hands it to
 * every page that says someone is signed in (`window.oeeeApp.pushToken`), and the page
 * registers it for them from its own session and says which it is, so that signing out on
 * the site unregisters it there (app_bridge.jinja and devices.rs in oeee-cafe/web). The app
 * makes no request to the site itself. It is not kept: FCM keeps it, and it is asked for
 * again whenever someone signs in, which on a later launch is the first page that says so.
 */
object PushNotificationService {
    private const val TAG = "PushNotificationService"

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    /** Asks FCM for the token, once someone has signed in. */
    suspend fun fetchToken() {
        try {
            tokenArrived(FirebaseMessaging.getInstance().token.await())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get the FCM token", e)
        }
    }

    /** A token from FCM, asked for or refreshed; a page showing is handed it at once. */
    fun tokenArrived(token: String) {
        _token.value = token
    }
}
