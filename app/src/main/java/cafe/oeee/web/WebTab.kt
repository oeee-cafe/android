package cafe.oeee.web

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import cafe.oeee.R
import cafe.oeee.data.remote.ApiClient

/** A tab of the native tab bar, each showing its own page of the site. */
enum class WebTab(
    val path: String,
    @StringRes val title: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    HOME("/", R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    COMMUNITIES("/communities", R.string.tab_communities, Icons.Filled.Group, Icons.Outlined.Group),
    NOTIFICATIONS("/notifications", R.string.tab_notifications, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    LOGIN("/login", R.string.tab_login, Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
    SEARCH("/search", R.string.tab_search, Icons.Filled.Search, Icons.Outlined.Search);

    val rootUrl: String get() = ApiClient.BASE_URL + path

    companion object {
        fun visible(isAuthenticated: Boolean): List<WebTab> =
            if (isAuthenticated) listOf(HOME, COMMUNITIES, NOTIFICATIONS, SEARCH)
            else listOf(HOME, COMMUNITIES, LOGIN, SEARCH)

        /**
         * The tab a page of the site belongs in: the sections with a tab of their own carry
         * everything below them, and home is the site's own front page.
         *
         * Null for a page that is nobody's section -- a drawing, a profile, what somebody
         * wrote, the pages the site's toolbar has and the tab bar does not. Those are read
         * in whichever section they were opened from, and leave the bar where it is.
         */
        fun showing(path: String): WebTab? {
            val here = path.ifEmpty { HOME.path }
            if (here == HOME.path) return HOME
            return entries.firstOrNull { it != HOME && here.startsWith(it.path) }
        }
    }
}

/** Lets the site's file inputs pick files, through the activity. */
fun interface FileChooser {
    fun show(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
}

/** Asks, through the activity, to write to the shared folders (Android 9 and older only). */
fun interface StoragePermission {
    fun request(onResult: (Boolean) -> Unit)
}
