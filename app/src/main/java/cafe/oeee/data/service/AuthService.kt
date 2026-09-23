package cafe.oeee.data.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether someone is signed in on the site. Signing in and out happens on the web views,
 * and every page with the toolbar says which it is (BridgeMessage.Page.signedIn), so nothing
 * is asked of the API. What the last page said is kept, so the next launch starts the way it
 * was left rather than signed out until the first page has loaded; that page corrects it if
 * the session has ended since.
 */
object AuthService {
    private const val PREFS = "auth"
    private const val SIGNED_IN = "signed_in"

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private var prefs: SharedPreferences? = null

    /** Picks up what the last page said, before anything reads [isAuthenticated]. */
    fun start(context: Context) {
        if (prefs != null) return
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = stored
        _isAuthenticated.value = stored.getBoolean(SIGNED_IN, false)
    }

    /** What a page of the site said. */
    fun pageSaid(signedIn: Boolean) {
        if (_isAuthenticated.value == signedIn) return
        _isAuthenticated.value = signedIn
        prefs?.edit()?.putBoolean(SIGNED_IN, signedIn)?.apply()
    }
}
