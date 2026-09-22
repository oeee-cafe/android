package cafe.oeee.web

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.service.AuthService

/**
 * Starts the web views' session: the cookies of one signed in natively, before the app
 * became a web view, and then who is signed in, for the tab bar the first page is shown under.
 */
object WebSession {
    private const val TAG = "WebSession"

    /** Where the native versions of the app kept their cookies (a java.net.CookieStore). */
    private const val LEGACY_PREFS = "cookie_prefs"

    private val cookieManager get() = CookieManager.getInstance()

    suspend fun start(context: Context) {
        cookieManager.setAcceptCookie(true)
        migrateNativeCookies(context)
        AuthService.checkAuthStatus()
    }

    /**
     * Seeds the web views with the native session's cookies, unless they have one of their
     * own already, and forgets the native ones either way. The native store kept each URI's
     * cookies as one string, `|COOKIE|` between cookies and `|FIELD|` between a cookie's
     * name, value, domain, path, max age, secure flag and version.
     */
    private fun migrateNativeCookies(context: Context) {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val stored = prefs.all.values.filterIsInstance<String>()
        if (stored.isEmpty()) return

        val baseUrl = ApiClient.BASE_URL
        val host = Uri.parse(baseUrl).host
        if (host != null && cookieManager.getCookie(baseUrl).isNullOrEmpty()) {
            var moved = 0
            for (cookie in stored.flatMap { it.split("|COOKIE|") }) {
                val fields = cookie.split("|FIELD|")
                if (fields.size < 7) continue
                val (name, value, domainField, path, maxAgeField) = fields
                val domain = domainField.lowercase().removePrefix(".").ifEmpty { null } ?: continue
                val maxAge = maxAgeField.toLongOrNull() ?: -1L
                // The store dropped a cookie once it had expired; zero is one told to expire now.
                if (maxAge == 0L || !(host == domain || host.endsWith(".$domain"))) continue
                val attributes = buildString {
                    append("${name.trim()}=$value; Path=${path.ifEmpty { "/" }}")
                    if (domain != host) append("; Domain=$domain")
                    if (maxAge > 0) append("; Max-Age=$maxAge")
                    if (fields[5].toBoolean()) append("; Secure")
                }
                cookieManager.setCookie(baseUrl, attributes)
                moved++
            }
            cookieManager.flush()
            Log.i(TAG, "Moved $moved cookies from the native session to the web views")
        }
        prefs.edit().clear().apply()
    }
}
