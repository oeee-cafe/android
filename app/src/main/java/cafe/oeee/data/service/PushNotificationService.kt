package cafe.oeee.data.service

import android.content.Context
import android.content.SharedPreferences
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
 * makes no request to the site itself. The token is kept, so a page that loads before FCM
 * has answered on a later launch still gets one.
 */
object PushNotificationService {
    private const val TAG = "PushNotificationService"
    private const val PREFS = "push"
    private const val TOKEN = "fcm_token"

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private var prefs: SharedPreferences? = null

    /** Picks up the token kept from before, before anything reads [token]. */
    @Synchronized
    fun start(context: Context) {
        if (prefs != null) return
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = stored
        if (_token.value == null) _token.value = stored.getString(TOKEN, null)
    }

    /** Asks FCM for the token, once someone has signed in and may be sent notifications. */
    suspend fun fetchToken() {
        try {
            tokenArrived(FirebaseMessaging.getInstance().token.await())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get the FCM token", e)
        }
    }

    /** A token from FCM, asked for or refreshed; a page showing is handed it at once. */
    fun tokenArrived(token: String) {
        if (_token.value == token) return
        _token.value = token
        prefs?.edit()?.putString(TOKEN, token)?.apply()
    }
}
