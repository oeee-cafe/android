package cafe.oeee.web

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import org.json.JSONObject

/**
 * Signing in with Apple, which cannot happen in this web view.
 *
 * Apple ships no Android SDK, and its page opened from here would be opened in a browser of
 * the app's own, where the answer comes back without the web view's session. So the site
 * hands the sign-in out and takes it back: the page starts a handoff, asks the app to open a
 * browser at the URL it is given (BridgeMessage.Browse), and asks the site until the browser
 * has finished (`window.oeeeApp.signIn.browser`, app_sign_in.jinja and src/handoff.rs in
 * oeee-cafe/web).
 *
 * The app's whole part is opening a browser. It never holds the session and never sees the
 * secret that claims the handoff; the page does every request, so each carries the page's
 * cookie and origin.
 *
 * The browser has to be a Custom Tab. `assetlinks.json` asks for `handle_all_urls` and the
 * manifest claims every oeee.cafe URL with no path filter, so a plain ACTION_VIEW for the
 * handoff URL would be caught by this app, open in this web view, and hand the sign-in
 * back to the one place that could not do it.
 */
class SignInHandoff(
    private val webView: WebView,
    private val openInBrowser: (Uri) -> Boolean
) {
    /** Stops the link and starts the sign-in the page will carry, going on to `next`. */
    fun begin(url: Uri, provider: String) {
        val next = url.getQueryParameter("next")
        webView.evaluateJavascript(
            "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.browser(" +
                "${JSONObject.quote(provider)}, ${if (next == null) "null" else JSONObject.quote(next)});",
            null
        )
    }

    /** Back in front, probably from the browser: the page asks the site again at once. */
    fun resume() {
        webView.evaluateJavascript("window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.resume();", null)
    }

    /** The page's `browse`: the site's own URL, opened in a browser, or the page told it was not. */
    fun browse(url: String) {
        val uri = siteUrl(url)
        if (uri == null || !openInBrowser(uri)) {
            Log.w(TAG, if (uri == null) "Not opening a browser at somewhere other than the site" else "No browser to sign in with")
            webView.evaluateJavascript("window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.unopened();", null)
        }
    }

    /** The site's own URL to send the browser to; anything else is not opened. */
    private fun siteUrl(url: String): Uri? =
        Uri.parse(url).takeIf { it.scheme == "https" && it.host == Uri.parse(webView.url ?: "").host }

    companion object {
        private const val TAG = "SignInHandoff"

        /** Whether this web view can do it at all: one without the bridge cannot. */
        fun isAvailable(): Boolean = SiteBridge.isAvailable()

        /** Whether [request] is the site's link to sign in with Apple. */
        fun isAppleSignInLink(request: WebResourceRequest, navigation: Navigation): Boolean =
            request.isForMainFrame &&
                request.method.equals("GET", ignoreCase = true) &&
                navigation.isSiteUrl(request.url) &&
                request.url.path == "/auth/apple"
    }
}
