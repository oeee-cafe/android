package cafe.oeee.data.remote

import android.content.Context
import android.content.SharedPreferences
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.net.URISyntaxException

class PersistentCookieStore(context: Context) : CookieStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("cookie_prefs", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<URI, MutableList<HttpCookie>>()

    init {
        // Load cookies from SharedPreferences
        loadCookies()
    }

    private fun loadCookies() {
        val allCookies = prefs.all
        for ((uriString, cookieString) in allCookies) {
            try {
                val uri = URI(uriString)
                val cookieStrings = (cookieString as? String)?.split(COOKIE_SEPARATOR) ?: continue

                val cookieList = mutableListOf<HttpCookie>()
                for (cookieStr in cookieStrings) {
                    val cookie = deserializeCookie(cookieStr) ?: continue
                    // Only load non-expired cookies
                    if (!cookie.hasExpired()) {
                        cookieList.add(cookie)
                    }
                }

                if (cookieList.isNotEmpty()) {
                    cookies[uri] = cookieList
                }
            } catch (e: URISyntaxException) {
                // Skip invalid URIs
            }
        }
    }

    private fun saveCookies() {
        val editor = prefs.edit()
        editor.clear()

        for ((uri, cookieList) in cookies) {
            val nonExpiredCookies = cookieList.filter { !it.hasExpired() }
            if (nonExpiredCookies.isNotEmpty()) {
                val cookieStrings = nonExpiredCookies.map { serializeCookie(it) }
                editor.putString(uri.toString(), cookieStrings.joinToString(COOKIE_SEPARATOR))
            }
        }

        editor.apply()
    }

    private fun serializeCookie(cookie: HttpCookie): String {
        return buildString {
            append(cookie.name)
            append(FIELD_SEPARATOR)
            append(cookie.value)
            append(FIELD_SEPARATOR)
            append(cookie.domain ?: "")
            append(FIELD_SEPARATOR)
            append(cookie.path ?: "")
            append(FIELD_SEPARATOR)
            append(cookie.maxAge)
            append(FIELD_SEPARATOR)
            append(cookie.secure)
            append(FIELD_SEPARATOR)
            append(cookie.version)
        }
    }

    private fun deserializeCookie(cookieString: String): HttpCookie? {
        return try {
            val parts = cookieString.split(FIELD_SEPARATOR)
            if (parts.size < 7) return null

            val cookie = HttpCookie(parts[0], parts[1])
            cookie.domain = parts[2].ifEmpty { null }
            cookie.path = parts[3].ifEmpty { null }
            cookie.maxAge = parts[4].toLongOrNull() ?: -1L
            cookie.secure = parts[5].toBoolean()
            cookie.version = parts[6].toIntOrNull() ?: 0

            cookie
        } catch (e: Exception) {
            null
        }
    }

    override fun add(uri: URI, cookie: HttpCookie) {
        // Remove expired cookies
        removeExpired(uri)

        // Add or update cookie
        val cookieList = cookies.getOrPut(uri) { mutableListOf() }

        // Remove existing cookie with same name
        cookieList.removeAll { it.name == cookie.name }

        // Add new cookie
        cookieList.add(cookie)

        // Save to SharedPreferences
        saveCookies()
    }

    override fun get(uri: URI): List<HttpCookie> {
        removeExpired(uri)

        val result = mutableListOf<HttpCookie>()

        // Get cookies that match this URI
        for ((storedUri, cookieList) in cookies) {
            if (uriMatches(storedUri, uri)) {
                result.addAll(cookieList.filter { !it.hasExpired() })
            }
        }

        return result
    }

    override fun getCookies(): List<HttpCookie> {
        val result = mutableListOf<HttpCookie>()
        for (cookieList in cookies.values) {
            result.addAll(cookieList.filter { !it.hasExpired() })
        }
        return result
    }

    override fun getURIs(): List<URI> {
        return cookies.keys.toList()
    }

    override fun remove(uri: URI, cookie: HttpCookie): Boolean {
        val cookieList = cookies[uri] ?: return false
        val removed = cookieList.removeAll { it.name == cookie.name }
        if (removed) {
            saveCookies()
        }
        return removed
    }

    override fun removeAll(): Boolean {
        cookies.clear()
        prefs.edit().clear().apply()
        return true
    }

    private fun removeExpired(uri: URI) {
        val cookieList = cookies[uri] ?: return
        val removed = cookieList.removeAll { it.hasExpired() }
        if (removed) {
            saveCookies()
        }
    }

    private fun uriMatches(stored: URI, request: URI): Boolean {
        // Simple domain matching
        val storedHost = stored.host ?: return false
        val requestHost = request.host ?: return false

        return requestHost.endsWith(storedHost) || storedHost.endsWith(requestHost)
    }

    companion object {
        private const val COOKIE_SEPARATOR = "|COOKIE|"
        private const val FIELD_SEPARATOR = "|FIELD|"
    }
}
