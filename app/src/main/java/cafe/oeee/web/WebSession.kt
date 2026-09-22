package cafe.oeee.web

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.remote.PersistentCookieStore
import cafe.oeee.data.service.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the native side (API calls, push registration, badge counts) following whoever is
 * signed in on the web views. The API client reads the web views' own cookie store; this
 * notices when those cookies change and re-checks who is signed in.
 */
object WebSession {
    private const val TAG = "WebSession"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var checkJob: Job? = null
    private var lastSignature: String? = null

    private val cookieManager get() = CookieManager.getInstance()

    /**
     * Seeds the web views with the cookies of a session signed in natively (before the app
     * became a web view), then finds out who is signed in.
     */
    suspend fun start(context: Context) {
        cookieManager.setAcceptCookie(true)
        migrateNativeCookies(context)
        lastSignature = signature()
        AuthService.checkAuthStatus()
    }

    /** Called as pages load and navigate: signing in or out changes the site's cookies. */
    fun cookiesMayHaveChanged() {
        checkJob?.cancel()
        checkJob = scope.launch {
            delay(300)
            val signature = signature()
            // Only re-check who is signed in when the cookies actually changed.
            if (signature == lastSignature) return@launch
            lastSignature = signature
            cookieManager.flush()
            Log.d(TAG, "Cookies changed, checking auth status")
            AuthService.checkAuthStatus()
        }
    }

    private fun signature(): String? = cookieManager.getCookie(ApiClient.getBaseUrl())

    private fun migrateNativeCookies(context: Context) {
        val store = PersistentCookieStore(context)
        val cookies = store.getCookies()
        if (cookies.isEmpty()) return

        val baseUrl = ApiClient.getBaseUrl()
        if (cookieManager.getCookie(baseUrl).isNullOrEmpty()) {
            val host = Uri.parse(baseUrl).host
            for (cookie in cookies) {
                val domain = cookie.domain?.removePrefix(".")
                if (host == null || domain == null || !(host == domain || host.endsWith(".$domain"))) continue
                val attributes = buildString {
                    append("${cookie.name}=${cookie.value}; Path=${cookie.path ?: "/"}")
                    if (domain != host) append("; Domain=$domain")
                    if (cookie.maxAge > 0) append("; Max-Age=${cookie.maxAge}")
                    if (cookie.secure) append("; Secure")
                }
                cookieManager.setCookie(baseUrl, attributes)
            }
            cookieManager.flush()
            Log.i(TAG, "Moved ${cookies.size} cookies from the native session to the web views")
        }
        store.removeAll()
    }
}
