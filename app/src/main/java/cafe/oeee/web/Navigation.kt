package cafe.oeee.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.WebResourceRequest
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Where a link tapped in the web view goes: the site stays in it, and every other place leaves it. */
class Navigation(private val activity: Activity, private val siteHost: String?) {
    fun isSiteUrl(uri: Uri): Boolean =
        (uri.scheme == "http" || uri.scheme == "https") && uri.host == siteHost

    /**
     * Whether [request] was sent outside the app instead of loading in the web view. Other sites
     * (and mailto: etc.) open outside the app; embeds in frames load as usual.
     */
    fun openedOutside(request: WebResourceRequest, toolbarColor: Color?): Boolean {
        val uri = request.url
        if (!request.isForMainFrame || isSiteUrl(uri) || uri.scheme in IN_PAGE_SCHEMES) return false
        openOutside(uri, toolbarColor)
        return true
    }

    /**
     * A page of the site, opened in a browser rather than in the app: a Custom Tab, never
     * the app that claims the link, because for oeee.cafe that app is this one. Signing in
     * through a browser (SignInHandoff) is the only thing that wants this -- the point is
     * to be somewhere the web view is not. False when there is no browser at all.
     */
    fun openInBrowser(uri: Uri, toolbarColor: Color?): Boolean {
        if (uri.scheme != "https" && uri.scheme != "http") return false
        return try {
            val colors = CustomTabColorSchemeParams.Builder()
                .apply { toolbarColor?.let { setToolbarColor(it.toArgb()) } }
                .build()
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setDefaultColorSchemeParams(colors)
                .build()
                .launchUrl(activity, uri)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No browser to open $uri")
            false
        }
    }

    /**
     * Another site's page: in the app that claims its links when one is installed, as a
     * link tapped anywhere else would open, and otherwise over the app in a Custom Tab
     * rather than off in the browser -- the iOS app's Safari view. Anything else (mail,
     * the store) goes to whatever handles it.
     */
    private fun openOutside(uri: Uri, toolbarColor: Color?) {
        if (uri.scheme == "http" || uri.scheme == "https") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val app = Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
                try {
                    activity.startActivity(app)
                    return
                } catch (e: ActivityNotFoundException) {
                    // No app claims it: a Custom Tab, below.
                }
            }
            try {
                val colors = CustomTabColorSchemeParams.Builder()
                    .apply { toolbarColor?.let { setToolbarColor(it.toArgb()) } }
                    .build()
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setDefaultColorSchemeParams(colors)
                    .build()
                    .launchUrl(activity, uri)
                return
            } catch (e: ActivityNotFoundException) {
                // No browser at all; the plain intent below says so the same way.
            }
        }
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app to open $uri")
        }
    }

    private companion object {
        const val TAG = "Navigation"
        val IN_PAGE_SCHEMES = setOf("about", "blob", "data", "javascript")
    }
}
