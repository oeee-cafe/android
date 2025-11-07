package cafe.oeee.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Patterns

object ApiConfig {
    private const val PREFS_NAME = "api_config"
    private const val KEY_BASE_URL = "api_base_url"
    private const val KEY_DEVELOPER_MODE = "developer_mode_enabled"
    private const val DEFAULT_BASE_URL = "https://oeee.cafe"

    private lateinit var prefs: SharedPreferences
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isInitialized = true
        }
    }

    fun getBaseUrl(context: Context): String {
        init(context)
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun isDeveloperModeEnabled(context: Context): Boolean {
        init(context)
        return prefs.getBoolean(KEY_DEVELOPER_MODE, false)
    }

    fun setDeveloperMode(context: Context, enabled: Boolean) {
        init(context)
        prefs.edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply()
    }

    fun setBaseUrl(context: Context, url: String): Result<Unit> {
        init(context)

        // Validate URL
        if (!isValidUrl(url)) {
            return Result.failure(
                IllegalArgumentException("Invalid URL format. Please enter a valid URL starting with http:// or https://")
            )
        }

        // Clear auth state and cookies when changing URL
        clearAuthState(context)
        clearCookies(context)

        prefs.edit().putString(KEY_BASE_URL, url).apply()
        return Result.success(Unit)
    }

    fun resetToDefault(context: Context) {
        init(context)

        // Clear auth state and cookies
        clearAuthState(context)
        clearCookies(context)

        prefs.edit().remove(KEY_BASE_URL).apply()
    }

    private fun isValidUrl(urlString: String): Boolean {
        // Check if URL starts with http:// or https://
        if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
            return false
        }

        // Use Android's URL pattern matcher
        return Patterns.WEB_URL.matcher(urlString).matches()
    }

    private fun clearAuthState(context: Context) {
        // Clear encrypted SharedPreferences used by AuthService
        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "auth_prefs_encrypted",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            encryptedPrefs.edit().clear().apply()
        } catch (e: Exception) {
            // If encrypted preferences fail, try fallback
            context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }

    private fun clearCookies(context: Context) {
        // Clear persistent cookie store
        val cookieStore = PersistentCookieStore(context)
        cookieStore.removeAll()
    }
}
