package cafe.oeee.web

import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * The one way the site tells the app things (`oeeeBridge`, app_bridge.jinja in oeee-cafe/web),
 * on the app's web view: what it shows, and what it asks of the app -- a sign-in, a share, a
 * download. Each message is heard only from the main frame of a page of the site: the
 * listener is only given to the site's origin, and a frame inside one of its pages is
 * someone else's embed.
 */
class SiteBridge(
    private val webView: WebView,
    private val siteOrigin: String,
    private val listener: Listener
) {
    fun interface Listener {
        fun onMessage(message: BridgeMessage)
    }

    private var textScaleScript: ScriptHandler? = null
    private var textScale: Float? = null

    init {
        listen()
    }

    /**
     * Hears the bridge on the web view, where it can. Android System WebView updates apart
     * from the app, so the same build can differ from one device to the next; one too old
     * has no bridge at all, and the page, finding no one to post to, asks nothing of the app.
     */
    private fun listen() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(webView, BRIDGE_OBJECT_NAME, setOf(siteOrigin)) {
                _: WebView, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, _: JavaScriptReplyProxy ->
            if (!isMainFrame || origin(sourceOrigin) != siteOrigin) {
                Log.w(TAG, "Ignored $BRIDGE_OBJECT_NAME from ${if (isMainFrame) sourceOrigin else "a frame"}")
                return@addWebMessageListener
            }
            BridgeMessage.parse(message.data)?.let(listener::onMessage)
        }
    }

    /**
     * Puts the reader's font size on every page from its first paint, as `--oeee-text-scale`,
     * which the site's type scale follows (ds.css in oeee-cafe/web) -- text grows, pictures,
     * spacing and the painter's chrome do not. The scale is the system's own, unclamped: the
     * site keeps it within what its layouts were drawn for, so every app says the same thing
     * and the limits live in one place. Left to itself the web view would instead zoom every
     * piece of text on the page by the font scale (its default text zoom), so that is turned
     * off; unless the page can't be told before it paints, when the zoom stays.
     */
    fun showTextScale(fontScale: Float) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        if (fontScale == textScale) return
        textScale = fontScale
        webView.settings.textZoom = 100
        val source = "document.documentElement.style.setProperty('--oeee-text-scale', '$fontScale');"
        textScaleScript?.remove()
        textScaleScript = WebViewCompat.addDocumentStartJavaScript(webView, source, setOf(siteOrigin))
        if (webView.url?.let { origin(Uri.parse(it)) } == siteOrigin) {
            webView.evaluateJavascript(source, null)
        }
    }

    companion object {
        private const val TAG = "SiteBridge"
        const val BRIDGE_OBJECT_NAME = "oeeeBridge"

        /** `scheme://host[:port]`, as a web message listener's allowed origins are written. */
        fun origin(uri: Uri): String =
            "${uri.scheme}://${uri.host}" + if (uri.port != -1) ":${uri.port}" else ""
    }
}

/** The feedback closest to what each of the site's names plays on iOS; null for others. */
fun hapticFeedback(name: String?): Int? = when (name) {
    "light" -> HapticFeedbackConstants.KEYBOARD_TAP
    "medium" -> HapticFeedbackConstants.CONTEXT_CLICK
    "selection" ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    "success" ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK
    "warning", "error" ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
    else -> null
}
