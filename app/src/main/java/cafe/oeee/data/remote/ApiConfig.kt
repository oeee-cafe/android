package cafe.oeee.data.remote

import android.content.Context

object ApiConfig {
    private const val PREFS_NAME = "api_config"
    private const val KEY_BASE_URL = "api_base_url"
    const val DEFAULT_BASE_URL = "https://oeee.cafe"

    /** The site the app shows: oeee.cafe, or a server set up in an earlier version's developer mode. */
    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, null)?.trimEnd('/') ?: DEFAULT_BASE_URL
    }
}
