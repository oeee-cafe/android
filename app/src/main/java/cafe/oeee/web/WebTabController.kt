package cafe.oeee.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import cafe.oeee.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** One tab's web view: its settings, its life, and what its pages say to the app. */
@SuppressLint("SetJavaScriptEnabled")
class WebTabController(
    val tab: WebTab,
    private val activity: Activity,
    scripts: PageScripts,
    fileChooser: FileChooser,
    storagePermission: StoragePermission,
    savedState: Bundle?,
    private val onPage: (BridgeMessage.Page) -> Unit,
    private val onUnread: (Int) -> Unit,
    private val onRenderProcessGone: () -> Unit
) : DrawingActions, SiteBridge.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val siteOrigin: String = SiteBridge.origin(Uri.parse(tab.rootUrl))
    private val navigation = Navigation(activity, Uri.parse(tab.rootUrl).host)
    private val dialogs = SiteDialogs(activity)

    /** The drawing a finger last landed on, if it landed on one (BridgeMessage.Pressed). */
    private var pressedDrawing: DrawingMenu.Drawing? = null

    /** Whether the page shown may be reloaded by pulling it down (BridgeMessage.Page.refreshable). */
    private var refreshable = true

    val webView = WebView(activity)

    /** The tab's view: the web view, pulled down to reload. */
    val view = SwipeRefreshLayout(activity)

    private val downloads = Downloads(activity, webView, storagePermission)
    private val bridge: SiteBridge

    /**
     * Signing in with Google, which Google will not do in a web view; null in a build that
     * cannot ([GoogleSignIn.isAvailable]), whose pages are not offered it either.
     */
    private val googleSignIn: GoogleSignIn?

    /**
     * Signing in with Apple, which Apple has no Android SDK for: it goes out to a browser
     * and the answer comes back through a handoff. Null in a web view too old for the
     * bridge it needs, whose pages are shown no Apple button either.
     */
    private val signInHandoff: SignInHandoff?

    /**
     * The colors at the top and bottom edges of the page shown, for the status bar above it
     * and the tab bar below; null until a page has said.
     */
    var topColor by mutableStateOf<Color?>(null)
        private set
    var bottomColor by mutableStateOf<Color?>(null)
        private set

    /** Whether the web view has loaded anything (the search tab waits for a search). */
    var hasLoaded by mutableStateOf(false)
        private set

    /** Whether the page asked for could not be reached (Unreachable.kt shows so over it). */
    var isUnreachable by mutableStateOf(false)
        private set

    /** Whether the tab's own history has a page to go back to, which Back does first. */
    var canGoBack by mutableStateOf(false)
        private set

    /** The drawing whose menu is open (DrawingSheet); null when none is. */
    var drawingMenu by mutableStateOf<DrawingMenu.Drawing?>(null)

    /** For work that outlives a composable, such as fetching the drawing its menu shows. */
    val coroutineScope: CoroutineScope get() = scope

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "$userAgentString $USER_AGENT_SUFFIX"
        }
        webView.webViewClient = Client()
        webView.webChromeClient = ChromeClient(fileChooser)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloads.download(url, userAgent, contentDisposition, mimeType)
        }
        webView.setOnLongClickListener { openDrawingMenu() }
        bridge = SiteBridge(webView, siteOrigin, scripts, this)
        googleSignIn = if (GoogleSignIn.isAvailable()) {
            GoogleSignIn(activity, webView, siteOrigin, scripts.googleSignIn, scope)
        } else {
            null
        }
        signInHandoff = if (SignInHandoff.isAvailable()) {
            SignInHandoff(webView, siteOrigin, scripts.signInHandoff) { url ->
                navigation.openInBrowser(url, topColor)
            }
        } else {
            null
        }
        showTextScale()

        view.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        view.setOnRefreshListener { webView.reload() }
        // Pulling down reloads only at the top of a page, and never one that says it may
        // not be: a canvas being drawn on, or a replay playing.
        view.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 || !refreshable }

        // Where the tab was when the system stopped the app, or else its own page. Search
        // shows nothing until something is searched for.
        if (!restore(savedState) && tab != WebTab.SEARCH) {
            load(tab.rootUrl)
        }
    }

    /** Takes the tab back to the history [saveState] kept; false when there is none. */
    private fun restore(state: Bundle?): Boolean {
        if (state == null) return false
        state.getString(STATE_URL)?.let {
            load(it)
            return true
        }
        if (webView.restoreState(state) == null) return false
        hasLoaded = true
        return true
    }

    /**
     * The tab's history, for the system to keep while the app is stopped. The web view's
     * own state holds every page of it; when that is too large to be kept alongside the
     * other tabs' -- a bundle the system refuses takes the whole app down -- only the page
     * shown is.
     */
    fun saveState(): Bundle? {
        if (!hasLoaded) return null
        val state = Bundle()
        if (webView.saveState(state) != null && sizeOf(state) <= MAX_STATE_BYTES) return state
        val url = webView.url ?: return null
        return Bundle().apply { putString(STATE_URL, url) }
    }

    private fun sizeOf(bundle: Bundle): Int {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    /** Tries the page that could not be reached again. */
    fun retry() {
        isUnreachable = false
        webView.reload()
    }

    fun retryIfUnreachable() {
        if (isUnreachable) retry()
    }

    /**
     * The tab is in front again. Somebody may have just come back from signing in in a
     * browser, so the page asks the site now instead of waiting for its next turn.
     */
    fun resumed() {
        signInHandoff?.resume()
    }

    fun load(url: String) {
        hasLoaded = true
        webView.loadUrl(url)
    }

    fun reload() {
        if (hasLoaded) webView.reload()
    }

    /** Shows the site's results for [query] (`/search?q=`). */
    fun search(query: String) {
        load(Uri.parse(tab.rootUrl).buildUpon().appendQueryParameter("q", query).build().toString())
    }

    /** Tapping the selected tab again: scroll to the top, or go back to the tab's own page. */
    fun reselect() {
        if (webView.scrollY > 0) {
            webView.evaluateJavascript("window.scrollTo({ top: 0, behavior: 'smooth' })", null)
        } else if (hasLoaded && webView.url?.let { Uri.parse(it).path } != tab.path) {
            load(tab.rootUrl)
        }
    }

    /** The reader's font size, on every page of the tab (SiteBridge.showTextScale). */
    fun showTextScale() {
        bridge.showTextScale(activity.resources.configuration.fontScale)
    }

    fun tearDown() {
        scope.cancel()
        (view.parent as? ViewGroup)?.removeView(view)
        webView.stopLoading()
        webView.destroy()
    }

    override fun onMessage(message: BridgeMessage) {
        when (message) {
            is BridgeMessage.Page -> {
                refreshable = message.refreshable
                onPage(message)
            }
            is BridgeMessage.Unread -> onUnread(message.count)
            is BridgeMessage.Theme -> {
                message.top?.let { topColor = it }
                message.bottom?.let { bottomColor = it }
            }
            is BridgeMessage.Haptic -> hapticFeedback(message.name)?.let { webView.performHapticFeedback(it) }
            is BridgeMessage.Pressed -> pressedDrawing = message.drawing?.let {
                DrawingMenu.Drawing(it, referrer = webView.url, userAgent = webView.settings.userAgentString)
            }
            // The painter can be driven now; nothing in this app drives it yet.
            is BridgeMessage.Painter -> Unit
        }
    }

    override fun onShare(share: Polyfills.Share) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, share.text)
            if (share.title.isNotEmpty()) putExtra(Intent.EXTRA_TITLE, share.title)
        }
        activity.startActivity(Intent.createChooser(send, share.title.ifEmpty { null }))
    }

    override fun onDownload(file: SiteFile?) {
        scope.launch { downloads.save(file) }
    }

    /**
     * A long press on a drawing opens its menu. The web view's own hit test has to agree
     * that the press is on an image, so a finger that landed on a drawing and then moved
     * away to press something else opens nothing.
     */
    private fun openDrawingMenu(): Boolean {
        val drawing = pressedDrawing ?: return false
        val hit = webView.hitTestResult.type
        if (hit != WebView.HitTestResult.IMAGE_TYPE && hit != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return false
        pressedDrawing = null
        webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        drawing.load(scope)
        drawingMenu = drawing
        return true
    }

    override fun save(drawing: DrawingMenu.Drawing) {
        scope.launch { downloads.save(drawing.file(scope)) }
    }

    override fun copy(drawing: DrawingMenu.Drawing) {
        scope.launch {
            val file = drawing.file(scope) ?: return@launch downloads.saved(null)
            MediaFiles.copy(activity, file)
            downloads.feel("success")
        }
    }

    override fun share(drawing: DrawingMenu.Drawing) {
        scope.launch {
            val file = drawing.file(scope) ?: return@launch downloads.saved(null)
            MediaFiles.share(activity, file, drawing.link, activity.getString(R.string.share_title))
        }
    }

    override fun copyLink(drawing: DrawingMenu.Drawing) {
        val link = drawing.link ?: return
        activity.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newRawUri(link, Uri.parse(link)))
        downloads.feel("success")
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // The site's link to Google's sign-in page, which Google refuses in a web
            // view: the page stays where it is and Credential Manager signs in instead.
            val google = googleSignIn
            if (google != null && GoogleSignIn.isSignInLink(request, navigation)) {
                google.begin(request.url)
                return true
            }
            // Apple's, which has no Android sheet to open: out to a browser, and back
            // through a handoff (SignInHandoff).
            val handoff = signInHandoff
            if (handoff != null && SignInHandoff.isAppleSignInLink(request, navigation)) {
                handoff.begin(request.url, "apple")
                return true
            }
            return navigation.openedOutside(request, topColor)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            isUnreachable = false
            pressedDrawing = null
            // [refreshable] is left as the last page said until the new one says: a moment in
            // which a painter could be pulled down is worse than one in which a feed can't be.
        }

        override fun onPageFinished(view: WebView, url: String?) {
            this@WebTabController.view.isRefreshing = false
            canGoBack = view.canGoBack()
            bridge.pageFinished(url)
        }

        // Pages the site swaps in without a full load (htmx) come through here.
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            canGoBack = view.canGoBack()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                this@WebTabController.view.isRefreshing = false
                Log.w(TAG, "${tab.name}: Failed to load ${request.url} - ${error.description}")
                // No network, no answer: said in the app's words, over the web view's own page.
                if (error.errorCode in UNREACHABLE_ERRORS) isUnreachable = true
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "${tab.name}: Web content process gone, starting the tab over")
            onRenderProcessGone()
            return true
        }
    }

    private inner class ChromeClient(private val fileChooser: FileChooser) : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean = fileChooser.show(filePathCallback, fileChooserParams)

        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
            dialogs.alert(message, result)

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
            dialogs.confirm(message, result)

        override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
            dialogs.beforeUnload(result)
    }

    private companion object {
        const val TAG = "WebTab"
        /** What the site looks for to know it is in this app (`data-app="android"`). */
        const val USER_AGENT_SUFFIX = "OeeeCafeAndroid"
        const val STATE_URL = "url"
        /** A quarter of what the system will carry for the whole app, per tab. */
        const val MAX_STATE_BYTES = 128 * 1024
        val UNREACHABLE_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO
        )
    }
}
