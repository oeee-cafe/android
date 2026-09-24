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
import androidx.compose.ui.graphics.toArgb
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import cafe.oeee.R
import cafe.oeee.data.service.PushNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** The app's one web view: its settings, its life, and what its pages say to the app. */
@SuppressLint("SetJavaScriptEnabled")
class WebController(
    private val activity: Activity,
    fileChooser: FileChooser,
    storagePermission: StoragePermission,
    savedState: Bundle?,
    private val onPage: (BridgeMessage.Page) -> Unit,
    private val onRenderProcessGone: () -> Unit
) : DrawingActions, SiteBridge.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val siteOrigin: String = SiteBridge.origin(Uri.parse(Site.BASE_URL))
    private val navigation = Navigation(activity, Uri.parse(Site.BASE_URL).host)
    private val dialogs = SiteDialogs(activity) { words }

    /** The drawing a finger last landed on, if it landed on one (BridgeMessage.Pressed). */
    private var pressedDrawing: DrawingMenu.Drawing? = null

    /** Whether the page shown may be reloaded by pulling it down (BridgeMessage.Page.refreshable). */
    private var refreshable = true

    /** Who the page shown last said is signed in, or null until one could tell. */
    private var lastSignedIn: Boolean? = null

    val webView = WebView(activity)

    /** The view shown: the web view, pulled down to reload. */
    val view = SwipeRefreshLayout(activity)

    private val downloads = Downloads(activity, webView, storagePermission) { words }
    private val bridge: SiteBridge

    /**
     * Signing in with Google, which Google will not do in a web view. The page asks for it
     * over the bridge, so a web view too old for the bridge is never asked: the page's post
     * finds no one listening and it leaves the link to go where it goes.
     */
    private val googleSignIn: GoogleSignIn

    /**
     * Signing in with Apple, which Apple has no Android SDK for: it goes out to a browser
     * and the answer comes back through a handoff. Asked for over the bridge, as Google is.
     */
    private val signInHandoff: SignInHandoff

    /**
     * The site's ground and the grid ruled on it (--ds-ground and --ds-grid), for what the
     * app draws where the page does not reach; null until a page has said, and then the last
     * that said, since a page without the design system says nothing about it.
     */
    var ground by mutableStateOf<Color?>(null)
        private set
    var grid by mutableStateOf<Color?>(null)
        private set

    /**
     * What the app says over the page, in the page's language (BridgeMessage.Words); null
     * until a page has said, when the app's own English stands in (SiteDialogs.word).
     */
    var words by mutableStateOf<BridgeMessage.Words?>(null)
        private set

    /** Whether the web view has loaded anything yet. */
    var hasLoaded by mutableStateOf(false)
        private set

    /** Whether the page asked for could not be reached (Unreachable.kt shows so over it). */
    var isUnreachable by mutableStateOf(false)
        private set

    /** Whether there is a page to go back to, which Back does first. */
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
            userAgentString = "$userAgentString ${Site.USER_AGENT_SUFFIX}"
        }
        webView.webViewClient = Client()
        webView.webChromeClient = ChromeClient(fileChooser)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloads.download(url, userAgent, contentDisposition, mimeType)
        }
        webView.setOnLongClickListener { openDrawingMenu() }
        bridge = SiteBridge(webView, siteOrigin, this)
        googleSignIn = GoogleSignIn(activity, webView, siteOrigin, scope)
        signInHandoff = SignInHandoff(webView) { url -> navigation.openInBrowser(url, ground) }
        showTextScale()
        // A token that arrives while a signed-in page is showing goes to it at once.
        scope.launch { PushNotificationService.token.collect { handPushToken() } }

        view.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        view.setOnRefreshListener { webView.reload() }
        // Pulling down reloads only at the top of a page, and never one that says it may
        // not be: a canvas being drawn on, or a replay playing.
        view.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 || !refreshable }

        // Where the reader was when the system stopped the app, or else the site's first page.
        if (!restore(savedState)) {
            load(Site.BASE_URL + "/")
        }
    }

    /** Takes the web view back to the history [saveState] kept; false when there is none. */
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
     * The history, for the system to keep while the app is stopped. The web view's own
     * state holds every page of it; when that is too large to keep -- a bundle the system
     * refuses takes the whole app down -- only the page shown is.
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
     * The app is in front again. Somebody may have just come back from signing in in a
     * browser, so the page asks the site now instead of waiting for its next turn.
     */
    fun resumed() {
        signInHandoff.resume()
    }

    fun load(url: String) {
        hasLoaded = true
        webView.loadUrl(url)
    }

    /** The reader's font size, on every page (SiteBridge.showTextScale). */
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
                message.signedIn?.let { lastSignedIn = it }
                onPage(message)
                handPushToken()
            }
            is BridgeMessage.Theme -> {
                message.ground?.let {
                    ground = it
                    // What shows before a page paints, in place of the web view's white.
                    webView.setBackgroundColor(it.toArgb())
                }
                message.grid?.let { grid = it }
            }
            is BridgeMessage.Words -> words = message
            is BridgeMessage.Haptic -> hapticFeedback(message.name)?.let { webView.performHapticFeedback(it) }
            is BridgeMessage.Pressed -> pressedDrawing = message.drawing?.let {
                DrawingMenu.Drawing(it, referrer = webView.url, userAgent = webView.settings.userAgentString)
            }
            is BridgeMessage.SignIn -> googleSignIn.signIn(message.nonce)
            is BridgeMessage.Browse -> signInHandoff.browse(message.url)
            is BridgeMessage.Share -> shareText(message)
            is BridgeMessage.Download -> scope.launch { downloads.save(Polyfills.file(message)) }
        }
    }

    /**
     * Hands the page this device's push token when it says someone is signed in, for it to
     * register for them (PushNotificationService). Every such page is handed it, so a new
     * document or a new token is never missed; the page ignores a token it has registered.
     */
    private fun handPushToken() {
        if (lastSignedIn != true) return
        val token = PushNotificationService.token.value ?: return
        if (webView.url?.let { SiteBridge.origin(Uri.parse(it)) } != siteOrigin) return
        webView.evaluateJavascript(PageScripts.pushToken(token), null)
    }

    /** `navigator.share`, as the system's share sheet (app_polyfills.jinja). */
    private fun shareText(share: BridgeMessage.Share) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, share.text)
            if (share.title.isNotEmpty()) putExtra(Intent.EXTRA_TITLE, share.title)
        }
        activity.startActivity(Intent.createChooser(send, share.title.ifEmpty { null }))
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
            // The site's sign-in buttons never get here: the page takes the press itself
            // and asks for Credential Manager or a browser on the bridge (app_sign_in.jinja
            // in oeee-cafe/web).
            return navigation.openedOutside(request, ground)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            isUnreachable = false
            pressedDrawing = null
            // [refreshable] is left as the last page said until the new one says: a moment in
            // which a painter could be pulled down is worse than one in which a feed can't be.
        }

        override fun onPageFinished(view: WebView, url: String?) {
            this@WebController.view.isRefreshing = false
            canGoBack = view.canGoBack()
        }

        // Pages the site swaps in without a full load (htmx) come through here.
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            canGoBack = view.canGoBack()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                this@WebController.view.isRefreshing = false
                Log.w(TAG, "Failed to load ${request.url} - ${error.description}")
                // No network, no answer: said in the app's words, over the web view's own page.
                if (error.errorCode in UNREACHABLE_ERRORS) isUnreachable = true
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "Web content process gone, starting the site over")
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

        // The site asks its questions in its own dialog (confirm_dialog.jinja in
        // oeee-cafe/web) and calls neither alert() nor confirm(); only a page's asking
        // before it is left, which no page can draw, is the app's.
        //
        // The page puts its loading bar up at the press for every other load, but not for
        // one it asks about, since a bar up before a Stay would hang there and only the app
        // hears the answer. So a Leave tells the page it is being left, as every app does
        // (oeeeApp.leaving, app_bridge.jinja), and the bar goes up; the page stays painted
        // until the next one arrives. The load is already the web view's to carry on with,
        // so the promise the call returns is not waited for.
        override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
            dialogs.beforeUnload(result) {
                view.evaluateJavascript(PageScripts.LEAVING, null)
            }
    }

    private companion object {
        const val TAG = "WebController"
        const val STATE_URL = "url"
        /** A quarter of what the system will carry for the whole app. */
        const val MAX_STATE_BYTES = 128 * 1024
        val UNREACHABLE_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO
        )
    }
}
