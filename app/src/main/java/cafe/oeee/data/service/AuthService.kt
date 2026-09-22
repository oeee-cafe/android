package cafe.oeee.data.service

import android.util.Log
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.remote.CurrentUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Who is signed in on the site. Signing in and out happens on the web views; `WebSession`
 * asks for a re-check whenever their cookies change.
 */
object AuthService {
    private const val TAG = "AuthService"

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUser = MutableStateFlow<CurrentUser?>(null)
    val currentUser: StateFlow<CurrentUser?> = _currentUser.asStateFlow()

    suspend fun checkAuthStatus() {
        try {
            val user = ApiClient.apiService.getCurrentUser()
            Log.d(TAG, "Signed in as ${user.loginName}")
            _currentUser.value = user
            _isAuthenticated.value = true
        } catch (e: Exception) {
            Log.w(TAG, "Auth check failed: ${e.message}")
            _currentUser.value = null
            _isAuthenticated.value = false
        }
    }
}
