package cafe.oeee

import android.content.Intent
import android.net.Uri
import android.util.Log
import cafe.oeee.web.WebTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Turns a tapped push notification or a link to the site into a page, and the tab to open it in. */
object NavigationCoordinator {
    private const val TAG = "NavigationCoordinator"
    private const val LINK_HOST = "oeee.cafe"

    /** What only a notification's intent carries: its page, and the ids every push has. */
    private val NOTIFICATION_KEYS = listOf("url", "notification_id", "notification_type")

    data class PendingNavigation(val tab: WebTab, val path: String)

    private val _pendingNavigation = MutableStateFlow<PendingNavigation?>(null)
    val pendingNavigation: StateFlow<PendingNavigation?> = _pendingNavigation.asStateFlow()

    private fun navigate(path: String, tab: WebTab) {
        Log.d(TAG, "Navigating to $path in ${tab.name}")
        _pendingNavigation.value = PendingNavigation(tab, path)
    }

    /**
     * Handles the data of a push notification the app was opened from, if any: the page its
     * `url` names, a path on the site the server chose for it; or, from a server that did not
     * say, the notifications page, where every notification can be found anyway.
     */
    fun handleNotificationIntent(intent: Intent) {
        val extras = intent.extras ?: return
        if (NOTIFICATION_KEYS.none { extras.containsKey(it) }) return
        // Only a path on the site: a notification is no reason to leave it.
        val url = intent.getStringExtra("url")?.takeIf { it.startsWith("/") && !it.startsWith("//") }
        val path = url ?: WebTab.NOTIFICATIONS.path
        Log.i(TAG, "Handling notification tap")
        navigate(path, tabFor(Uri.parse(path).path ?: path))
    }

    /** Handles a link to the site the app was opened with, if any. */
    fun handleLinkIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme != "https" || uri.host != LINK_HOST) return
        Log.i(TAG, "Handling link: $uri")

        val path = uri.encodedPath?.takeIf { it.isNotEmpty() } ?: "/"
        val pathAndMore = path +
            (uri.encodedQuery?.let { "?$it" } ?: "") +
            (uri.encodedFragment?.let { "#$it" } ?: "")
        navigate(pathAndMore, tabFor(path))
    }

    /** The tab whose own section of the site [path] is in; anything else opens on the home tab. */
    private fun tabFor(path: String): WebTab =
        WebTab.entries.firstOrNull { tab ->
            tab != WebTab.HOME && (path == tab.path || path.startsWith(tab.path + "/"))
        } ?: WebTab.HOME

    fun clearPendingNavigation() {
        _pendingNavigation.value = null
    }
}
