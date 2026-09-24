package cafe.oeee

import android.content.Intent
import android.util.Log

/** The page of the site a tapped push notification or a link to the site opens. */
object OpenedFrom {
    private const val TAG = "OpenedFrom"
    private const val LINK_HOST = "oeee.cafe"

    /** The path on the site [intent] opens, or null when it opens none. */
    fun path(intent: Intent): String? {
        val path = link(intent) ?: notification(intent) ?: return null
        Log.d(TAG, "Navigating to $path")
        return path
    }

    /**
     * The page of a push notification the app was opened from, if any: the page its `url`
     * names, a path on the site the server chose for it. Every push the site sends says one.
     */
    private fun notification(intent: Intent): String? {
        // Only a path on the site: a notification is no reason to leave it.
        val url = intent.getStringExtra("url")?.takeIf { it.startsWith("/") && !it.startsWith("//") }
            ?: return null
        Log.i(TAG, "Handling notification tap")
        return url
    }

    /** A link to the site the app was opened with, if any. */
    private fun link(intent: Intent): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme != "https" || uri.host != LINK_HOST) return null
        Log.i(TAG, "Handling link: $uri")

        val path = uri.encodedPath?.takeIf { it.isNotEmpty() } ?: "/"
        return path +
            (uri.encodedQuery?.let { "?$it" } ?: "") +
            (uri.encodedFragment?.let { "#$it" } ?: "")
    }
}
