package cafe.oeee.web

import android.content.res.AssetManager
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
 * The scripts the app runs in the site's pages, read from the app's assets once, when the
 * tabs are first made, rather than kept as strings in the code.
 */
class PageScripts(assets: AssetManager) {
    /** What every page of the site runs for the web platform features the app fills in. */
    val polyfills: List<String> = listOf("share.js", "download.js").map { assets.read(it) }

    private val textScaleFunction = assets.read("text-scale.js").trim().removeSuffix(";")

    /**
     * The font scale as the site's `--oeee-text-scale`, kept within what its layouts were
     * drawn for, as the iOS app keeps Dynamic Type: the largest sizes stop at twice the default.
     */
    fun textScale(fontScale: Float): String = "$textScaleFunction(${fontScale.coerceIn(0.8f, 2f)});"

    private fun AssetManager.read(name: String): String = open(name).bufferedReader().use { it.readText() }
}

/**
 * The one way the site tells the app things (`oeeeBridge`, app_bridge.jinja in oeee-cafe/web),
 * and the two it asks of it (share and download, [Polyfills]), on one tab's web view. Each
 * message is heard only from the main frame of a page of the site: the listeners are only
 * given to the site's origin, and a frame inside one of its pages is someone else's embed.
 */
class SiteBridge(
    private val webView: WebView,
    private val siteOrigin: String,
    private val scripts: PageScripts,
    private val listener: Listener
) {
    interface Listener {
        fun onMessage(message: BridgeMessage)
        fun onShare(share: Polyfills.Share)
        fun onDownload(file: SiteFile?)
    }

    private var scriptsAtDocumentStart = false
    private var textScaleScript: ScriptHandler? = null
    private var textScale: Float? = null

    init {
        listen(BRIDGE_OBJECT_NAME) { data ->
            BridgeMessage.parse(data)?.let(listener::onMessage)
        }
        listen(Polyfills.SHARE_OBJECT_NAME) { data ->
            Polyfills.share(data)?.let(listener::onShare)
        }
        listen(Polyfills.DOWNLOAD_OBJECT_NAME) { data ->
            listener.onDownload(Polyfills.download(data))
        }
        // The scripts run in every page of the site before its own; or, where the web view
        // can't, once each page has loaded ([pageFinished]).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            for (script in scripts.polyfills) {
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(siteOrigin))
            }
            scriptsAtDocumentStart = true
        }
    }

    /** Hears [name] on the web view, where it can; a web view too old to has none of the bridge. */
    private fun listen(name: String, onData: (String?) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(webView, name, setOf(siteOrigin)) {
                _: WebView, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, _: JavaScriptReplyProxy ->
            if (!isMainFrame || origin(sourceOrigin) != siteOrigin) {
                Log.w(TAG, "Ignored $name from ${if (isMainFrame) sourceOrigin else "a frame"}")
                return@addWebMessageListener
            }
            onData(message.data)
        }
    }

    /** A page of the site finished loading: where scripts can't run first, they run now. */
    fun pageFinished(url: String?) {
        if (scriptsAtDocumentStart || url == null || origin(Uri.parse(url)) != siteOrigin) return
        for (script in scripts.polyfills) webView.evaluateJavascript(script, null)
    }

    /**
     * Puts the reader's font size on every page from its first paint, as `--oeee-text-scale`,
     * which the site's type scale follows (ds.css in oeee-cafe/web) -- text grows, pictures,
     * spacing and the painter's chrome do not. Left to itself the web view would instead zoom
     * every piece of text on the page by the font scale (its default text zoom), so that is
     * turned off; unless the page can't be told before it paints, when the zoom stays.
     */
    fun showTextScale(fontScale: Float) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        if (fontScale == textScale) return
        textScale = fontScale
        webView.settings.textZoom = 100
        val source = scripts.textScale(fontScale)
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
