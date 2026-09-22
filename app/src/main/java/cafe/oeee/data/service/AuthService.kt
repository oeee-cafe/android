package cafe.oeee.data.service

import android.util.Log
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether someone is signed in on the site. Signing in and out happens on the web views,
 * and every page with the toolbar says which it is (BridgeMessage.Page.signedIn); the API
 * is asked only at start, so the tab bar is right before the first page has loaded.
 */
object AuthService {
    private const val TAG = "AuthService"

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    suspend fun checkAuthStatus() {
        _isAuthenticated.value = try {
            ApiClient.apiService.getCurrentUser()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Auth check failed: ${e.message}")
            false
        }
    }

    /** What a page of the site said; it knows better than the API was asked at start. */
    fun pageSaid(signedIn: Boolean) {
        _isAuthenticated.value = signedIn
    }
}
