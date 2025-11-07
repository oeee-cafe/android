package cafe.oeee.data.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cafe.oeee.data.model.push.RegisterPushTokenRequest
import cafe.oeee.data.remote.ApiClient
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class PushNotificationService private constructor(private val context: Context) {
    private val apiService = ApiClient.apiService

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "push_prefs_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // If encrypted preferences fail (e.g., after reinstall or security state change),
            // delete the corrupted file and recreate
            try {
                context.deleteSharedPreferences("push_prefs_encrypted")

                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    "push_prefs_encrypted",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                // Fall back to regular SharedPreferences if encryption completely fails
                context.getSharedPreferences("push_prefs_fallback", android.content.Context.MODE_PRIVATE)
            }
        }
    }

    private val _permissionGranted = MutableStateFlow(checkPermissionStatus())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    companion object {
        private const val TAG = "PushNotificationService"
        private const val TOKEN_KEY = "fcm_device_token"
        const val PERMISSION_REQUEST_CODE = 1001

        @Volatile
        private var INSTANCE: PushNotificationService? = null

        fun getInstance(context: Context): PushNotificationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PushNotificationService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Request notification permissions and register for push notifications
     * This should be called after successful login
     */
    suspend fun requestPermissionsAndRegister(activity: Activity) {
        // For Android 13 (API 33) and above, we need to request POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    _permissionGranted.value = true
                    Log.d(TAG, "Push notification permission already granted")
                    registerFcmToken()
                }

                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    // Show rationale and request permission
                    showPermissionRationale()
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST_CODE
                    )
                }

                else -> {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST_CODE
                    )
                }
            }
        } else {
            // For Android 12 and below, notifications are enabled by default
            _permissionGranted.value = true
            registerFcmToken()
        }
    }

    /**
     * Handle permission request result
     * This should be called from the activity's onRequestPermissionsResult
     */
    suspend fun handlePermissionResult(granted: Boolean) {
        _permissionGranted.value = granted

        if (granted) {
            Log.d(TAG, "Push notification permission granted")
            registerFcmToken()
        } else {
            Log.d(TAG, "Push notification permission denied")
            Toast.makeText(
                context,
                "You can enable notifications later in app settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Register FCM token with backend
     * Called after permission is granted or from FirebaseMessagingService when token is refreshed
     */
    suspend fun registerFcmToken(token: String? = null) {
        try {
            // Get token if not provided
            val fcmToken = token ?: FirebaseMessaging.getInstance().token.await()

            Log.d(TAG, "Registering FCM token: $fcmToken")

            // Check if we already registered this token
            val savedToken = prefs.getString(TOKEN_KEY, null)
            if (savedToken == fcmToken) {
                Log.d(TAG, "Token already registered, skipping")
                return
            }

            // Register with backend
            val request = RegisterPushTokenRequest(
                deviceToken = fcmToken,
                platform = "android"
            )

            val response = apiService.registerPushToken(request)
            Log.d(TAG, "Successfully registered push token: ${response.id}")

            // Save token to prevent duplicate registrations
            prefs.edit().putString(TOKEN_KEY, fcmToken).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token", e)
        }
    }

    /**
     * Get the current device token
     */
    fun getDeviceToken(): String? {
        return prefs.getString(TOKEN_KEY, null)
    }

    /**
     * Delete push token from backend
     * This should be called during logout
     */
    suspend fun deletePushToken() {
        try {
            val deviceToken = prefs.getString(TOKEN_KEY, null)

            if (deviceToken.isNullOrEmpty()) {
                Log.d(TAG, "No device token to delete")
                return
            }

            Log.d(TAG, "Deleting push token from backend")
            apiService.deletePushToken(deviceToken)
            Log.d(TAG, "Successfully deleted push token")

            // Clear saved token
            prefs.edit().remove(TOKEN_KEY).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete push token", e)
            // Still clear the local token even if backend deletion fails
            prefs.edit().remove(TOKEN_KEY).apply()
        }
    }

    private fun checkPermissionStatus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Notifications are enabled by default on Android 12 and below
        }
    }

    private fun showPermissionRationale() {
        Toast.makeText(
            context,
            "Enable notifications to receive updates and messages",
            Toast.LENGTH_SHORT
        ).show()
    }
}
