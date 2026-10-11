package cafe.oeee.web

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView

/**
 * Signing in with Apple, which cannot happen in this web view.
 *
 * Apple ships no Android SDK, and its page opened from here would be opened in a browser of
 * the app's own, where the answer comes back without the web view's session. So the site
 * hands the sign-in out and takes it back: the page starts a handoff, asks the app to open a
 * browser at the URL it is given (BridgeMessage.Browse), and asks the site until the browser
 * has finished (app_sign_in.jinja and src/handoff.rs in oeee-cafe/web). The page starts it
 * itself, on the press of the site's button; the app never sees the link.
 *
 * The app's part is opening a browser and, when the site sends the browser back to
 * oeee-cafe://handoff/done?code=..., passing that code to the page as it stands, if it is
 * the 64 lowercase hex digits the site makes (PageScripts.signInResume). It never holds the
 * session and never sends anything to the site itself; the page does every request, so each
 * carries the page's cookie and origin.
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
    /** The code the site's way back said ([returned]), until the app is in front to hand it over. */
    private var returnedCode: String? = null

    /**
     * The browser came back by oeee-cafe://handoff/done, saying [code] or nothing. The
     * activity is resumed right after, and [resume] hands it over then, once.
     */
    fun returned(code: String?) {
        returnedCode = code
    }

    /**
     * Back in front, probably from the browser: the page asks the site again at once, with
     * the code the site's way back said if it said one ([PageScripts.signInResume]). Back
     * any other way, the Custom Tab closed by hand, say, the page is asked with nothing.
     */
    fun resume() {
        val code = returnedCode
        returnedCode = null
        webView.evaluateJavascript(PageScripts.signInResume(code), null)
    }

    /** The page's `browse`: the site's own URL, opened in a browser, or the page told it was not. */
    fun browse(url: String) {
        val uri = siteUrl(url)
        if (uri == null || !openInBrowser(uri)) {
            Log.w(TAG, if (uri == null) "Not opening a browser at somewhere other than the site" else "No browser to sign in with")
            webView.evaluateJavascript(PageScripts.SIGN_IN_UNOPENED, null)
        }
    }

    /** The site's own URL to send the browser to; anything else is not opened. */
    private fun siteUrl(url: String): Uri? =
        Uri.parse(url).takeIf { it.scheme == "https" && it.host == Uri.parse(webView.url ?: "").host }

    companion object {
        private const val TAG = "SignInHandoff"

        /**
         * Whether [intent] is the browser coming back by the site's way back,
         * oeee-cafe://handoff/done; if it is, the `code` it says, as said (null for none),
         * for [PageScripts.signInResume] to take or leave.
         */
        fun returnedFrom(intent: Intent): Returned? {
            if (intent.action != Intent.ACTION_VIEW) return null
            val uri = intent.data ?: return null
            if (uri.scheme != "oeee-cafe" || uri.host != "handoff" || uri.path != "/done") return null
            return Returned(uri.getQueryParameter("code"))
        }
    }

    /** The browser came back by oeee-cafe://handoff/done, saying [code] or not. */
    class Returned(val code: String?)
}
