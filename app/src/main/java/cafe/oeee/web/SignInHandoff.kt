package cafe.oeee.web

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * Signing in with Apple, which cannot happen in this web view.
 *
 * Apple ships no Android SDK, and its page opened from here would be opened in a browser of
 * the app's own, where the answer comes back without the web view's session. So the site
 * hands the sign-in out and takes it back: the page starts a handoff, the app opens a
 * browser at the URL it is given, and the page asks the site until the browser has
 * finished (assets/sign-in-handoff.js, src/handoff.rs in oeee-cafe/web).
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
    siteOrigin: String,
    script: String,
    private val openInBrowser: (Uri) -> Boolean
) {
    init {
        // The page's half, in every page of the site before its own scripts run.
        WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(siteOrigin))
        WebViewCompat.addWebMessageListener(webView, OBJECT_NAME, setOf(siteOrigin)) {
                _: WebView, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean,
                _: JavaScriptReplyProxy ->
            if (!isMainFrame || SiteBridge.origin(sourceOrigin) != siteOrigin) {
                Log.w(TAG, "Ignored $OBJECT_NAME from ${if (isMainFrame) sourceOrigin else "a frame"}")
                return@addWebMessageListener
            }
            open(urlOf(message.data))
        }
    }

    /** Stops the link and starts the sign-in the page will carry, going on to `next`. */
    fun begin(url: Uri, provider: String) {
        val next = url.getQueryParameter("next")
        webView.evaluateJavascript(
            "window.oeeeHandoffAuth && window.oeeeHandoffAuth.begin(" +
                "${JSONObject.quote(provider)}, ${if (next == null) "null" else JSONObject.quote(next)});",
            null
        )
    }

    /** Back in front, probably from the browser: the page asks the site again at once. */
    fun resume() {
        webView.evaluateJavascript(
            "window.oeeeHandoffAuth && window.oeeeHandoffAuth.resume();",
            null
        )
    }

    private fun open(url: Uri?) {
        if (url == null || !openInBrowser(url)) {
            Log.w(TAG, "No browser to sign in with")
            webView.evaluateJavascript(
                "window.oeeeHandoffAuth && window.oeeeHandoffAuth.failed();",
                null
            )
        }
    }

    /** The site's own URL to send the browser to; anything else is not opened. */
    private fun urlOf(message: String?): Uri? = try {
        val url = JSONObject(message ?: "").optString("url")
        Uri.parse(url).takeIf { it.scheme == "https" && it.host == Uri.parse(webView.url ?: "").host }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val TAG = "SignInHandoff"

        /** What the page asks the app to open a browser through. */
        const val OBJECT_NAME = "oeeeHandoff"

        /**
         * Whether this build and this web view can do it at all: a web view without the
         * two halves of the bridge cannot, and Android System WebView updates apart from
         * the app, so the same build can differ from one device to the next.
         */
        fun isAvailable(): Boolean =
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

        /** Whether [request] is the site's link to sign in with Apple. */
        fun isAppleSignInLink(request: WebResourceRequest, navigation: Navigation): Boolean =
            request.isForMainFrame &&
                request.method.equals("GET", ignoreCase = true) &&
                navigation.isSiteUrl(request.url) &&
                request.url.path == "/auth/apple"
    }
}
