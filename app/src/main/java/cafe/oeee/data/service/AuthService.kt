package cafe.oeee.data.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cafe.oeee.data.model.auth.CurrentUser
import cafe.oeee.data.model.auth.DeleteAccountRequest
import cafe.oeee.data.model.auth.LoginRequest
import cafe.oeee.data.model.auth.LogoutRequest
import cafe.oeee.data.model.auth.SignupRequest
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthService private constructor(private val context: Context) {
    private val apiService = ApiClient.apiService
    private val pushService = PushNotificationService.getInstance(context)

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "auth_prefs_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // If encrypted preferences fail (e.g., after reinstall or security state change),
            // delete the corrupted file and recreate
            try {
                context.deleteSharedPreferences("auth_prefs_encrypted")

                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    "auth_prefs_encrypted",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                // Fall back to regular SharedPreferences if encryption completely fails
                context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUser = MutableStateFlow<CurrentUser?>(null)
    val currentUser: StateFlow<CurrentUser?> = _currentUser.asStateFlow()

    private val _isCheckingAuth = MutableStateFlow(true)
    val isCheckingAuth: StateFlow<Boolean> = _isCheckingAuth.asStateFlow()

    companion object {
        private const val AUTH_STATE_KEY = "isAuthenticated"

        @Volatile
        private var INSTANCE: AuthService? = null

        fun getInstance(context: Context): AuthService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        // Restore session on init
        restoreSession()
    }

    suspend fun login(loginName: String, password: String): Result<CurrentUser> {
        return try {
            val request = LoginRequest(loginName, password)
            val response = apiService.login(request)

            if (response.success && response.user != null) {
                // Update state
                _currentUser.value = response.user
                _isAuthenticated.value = true

                // Save auth state to SharedPreferences
                prefs.edit().putBoolean(AUTH_STATE_KEY, true).apply()

                Result.success(response.user)
            } else {
                Result.failure(Exception(response.error ?: "Login failed"))
            }
        } catch (e: retrofit2.HttpException) {
            // Parse the error response body to get the actual error message
            val errorMessage = try {
                val errorBody = e.response()?.errorBody()?.string()
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val adapter = moshi.adapter(cafe.oeee.data.model.auth.LoginResponse::class.java)
                val errorResponse = adapter.fromJson(errorBody ?: "")
                errorResponse?.error ?: "Login failed"
            } catch (parseException: Exception) {
                "Login failed"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signup(
        loginName: String,
        password: String,
        displayName: String
    ): Result<CurrentUser> {
        return try {
            val request = SignupRequest(loginName, password, displayName)
            val response = apiService.signup(request)

            if (response.success && response.user != null) {
                // Update state (user is auto-logged in after signup)
                _currentUser.value = response.user
                _isAuthenticated.value = true

                // Save auth state to SharedPreferences
                prefs.edit().putBoolean(AUTH_STATE_KEY, true).apply()

                Result.success(response.user)
            } else {
                Result.failure(Exception(response.error ?: "Signup failed"))
            }
        } catch (e: retrofit2.HttpException) {
            // Parse the error response body to get the actual error message
            val errorMessage = try {
                val errorBody = e.response()?.errorBody()?.string()
                val moshi = com.squareup.moshi.Moshi.Builder().build()
                val adapter = moshi.adapter(cafe.oeee.data.model.auth.SignupResponse::class.java)
                val errorResponse = adapter.fromJson(errorBody ?: "")
                errorResponse?.error ?: "Signup failed"
            } catch (parseException: Exception) {
                "Signup failed"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> {
        // Get the device token to include in logout request
        val deviceToken = pushService.getDeviceToken()
        Log.d("AuthService", "Logging out with device token: ${deviceToken?.take(20)}...")

        // Call logout API with device token (if this fails, we should still try to clear local state)
        try {
            val logoutRequest = LogoutRequest(deviceToken)
            Log.d("AuthService", "Sending logout request with token: ${logoutRequest.deviceToken?.take(20)}...")
            apiService.logout(logoutRequest)
            Log.d("AuthService", "Logout API call successful")
        } catch (e: Exception) {
            Log.e("AuthService", "Logout API call failed", e)
            // Continue with local logout even if API call fails
        }

        // Also delete push notification token using the old endpoint as a fallback
        // This ensures cleanup even if the logout API call failed
        Log.d("AuthService", "Calling deletePushToken as fallback")
        pushService.deletePushToken()

        // Clear state (always execute)
        _currentUser.value = null
        _isAuthenticated.value = false

        // Clear SharedPreferences (always execute)
        prefs.edit().remove(AUTH_STATE_KEY).apply()

        // Clear cookies (always execute)
        ApiClient.clearCookies()

        return Result.success(Unit)
    }

    suspend fun deleteAccount(password: String): Result<Unit> {
        return try {
            val request = DeleteAccountRequest(password)
            val response = apiService.deleteAccount(request)

            if (response.success) {
                Log.d("AuthService", "Account deleted successfully")

                // Delete push notification token locally
                pushService.deletePushToken()

                // Clear state
                _currentUser.value = null
                _isAuthenticated.value = false

                // Clear SharedPreferences
                prefs.edit().remove(AUTH_STATE_KEY).apply()

                // Clear cookies
                ApiClient.clearCookies()

                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Failed to delete account"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkAuthStatus() {
        _isCheckingAuth.value = true

        try {
            val user = apiService.getCurrentUser()

            // Update state
            _currentUser.value = user
            _isAuthenticated.value = true

            // Save state
            prefs.edit().putBoolean(AUTH_STATE_KEY, true).apply()
        } catch (e: Exception) {
            // Not authenticated, clear state
            _currentUser.value = null
            _isAuthenticated.value = false
            prefs.edit().remove(AUTH_STATE_KEY).apply()
        } finally {
            _isCheckingAuth.value = false
        }
    }

    fun updateCurrentUser(user: CurrentUser) {
        _currentUser.value = user
    }

    private fun restoreSession() {
        // Check if we have saved auth state
        val hasAuthState = prefs.getBoolean(AUTH_STATE_KEY, false)

        if (hasAuthState) {
            // We have a saved state, but we need to verify the session is still valid
            // This will be done asynchronously by the caller
            _isCheckingAuth.value = true
        } else {
            // No saved state
            _isCheckingAuth.value = false
        }
    }

    fun hasStoredAuthState(): Boolean {
        return prefs.getBoolean(AUTH_STATE_KEY, false)
    }
}
