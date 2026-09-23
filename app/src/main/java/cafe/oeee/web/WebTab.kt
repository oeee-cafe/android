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

    val rootUrl: String get() = Site.BASE_URL + path

    companion object {
        fun visible(isAuthenticated: Boolean): List<WebTab> =
            if (isAuthenticated) listOf(HOME, COMMUNITIES, NOTIFICATIONS, SEARCH)
            else listOf(HOME, COMMUNITIES, LOGIN, SEARCH)
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
