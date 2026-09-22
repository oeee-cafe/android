package cafe.oeee.data.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.remote.RegisterDeviceRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/** Registers this device's FCM token for the signed-in user, and unregisters it on signing out. */
class PushNotificationService private constructor(private val context: Context) {
    private val apiService get() = ApiClient.apiService

    private val prefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // If encrypted preferences fail (e.g., after reinstall or security state change),
            // delete the corrupted file and recreate
            try {
                context.deleteSharedPreferences(PREFS_NAME)
                createEncryptedPrefs()
            } catch (e2: Exception) {
                // Fall back to regular SharedPreferences if encryption completely fails
                context.getSharedPreferences("push_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    companion object {
        private const val TAG = "PushNotificationService"
        private const val PREFS_NAME = "push_prefs_encrypted"
        private const val TOKEN_KEY = "fcm_device_token"

        @Volatile
        private var INSTANCE: PushNotificationService? = null

        fun getInstance(context: Context): PushNotificationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PushNotificationService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Registers the FCM token with the backend for whoever is signed in. Called after signing in,
     * and from FirebaseMessagingService when the token is refreshed.
     */
    suspend fun registerFcmToken(token: String? = null) {
        if (!AuthService.isAuthenticated.value) return
        try {
            val fcmToken = token ?: FirebaseMessaging.getInstance().token.await()
            val response = apiService.registerDevice(
                RegisterDeviceRequest(deviceToken = fcmToken, platform = "android")
            )
            Log.d(TAG, "Registered device: ${response.id}")
            prefs.edit().putString(TOKEN_KEY, fcmToken).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token", e)
        }
    }

    /** Unregisters this device, while the session that is signing out is still valid. */
    suspend fun deleteDevice() {
        val deviceToken = prefs.getString(TOKEN_KEY, null)
        if (deviceToken.isNullOrEmpty()) {
            Log.d(TAG, "No device token to delete")
            return
        }
        try {
            apiService.deleteDevice(deviceToken)
            Log.d(TAG, "Deleted device")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete device", e)
        }
        prefs.edit().remove(TOKEN_KEY).apply()
    }
}
