package cafe.oeee.web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.os.Parcel
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import cafe.oeee.BuildConfig
import cafe.oeee.R
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.service.PushNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

    val rootUrl: String get() = ApiClient.getBaseUrl() + path

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

/**
 * Owns one long-lived web view per tab, so each tab keeps its own history and scroll position
 * -- across the app being stopped and started again by the system, too ([saveState]).
 */
class WebTabStore(
    private val activity: Activity,
    private val fileChooser: FileChooser,
    private val storagePermission: StoragePermission,
    savedState: Bundle?
) {
    private val controllers = mutableMapOf<WebTab, WebTabController>()

    /** Each tab's saved history, until the tab is first shown and takes it. */
    private val restored: MutableMap<WebTab, Bundle> = WebTab.entries
        .mapNotNull { tab -> savedState?.getBundle(stateKey(tab))?.let { tab to it } }
        .toMap().toMutableMap()

    /** A page that could not be reached is tried again when the network comes back. */
    private val connectivity = Connectivity(activity) {
        for (controller in controllers.values) controller.retryIfUnreachable()
    }.also { it.start() }

    /** Bumped when a tab's web view is recreated, so the tab screen shows the new one. */
    var generation by mutableIntStateOf(0)
        private set

    /** Called whenever any tab finishes loading a page. */
    var onPageLoad: (() -> Unit)? = null

    fun controller(tab: WebTab): WebTabController =
        controllers[tab] ?: create(tab)

    private fun create(tab: WebTab): WebTabController {
        val controller = WebTabController(
            tab = tab,
            activity = activity,
            fileChooser = fileChooser,
            storagePermission = storagePermission,
            savedState = restored.remove(tab),
            onPageLoad = { onPageLoad?.invoke() },
            onRenderProcessGone = { recreate(tab) }
        )
        controllers[tab] = controller
        return controller
    }

    /** A web view whose renderer is gone can't be used again; starts the tab over. */
    private fun recreate(tab: WebTab) {
        controllers.remove(tab)?.tearDown()
        create(tab)
        generation++
    }

    /**
     * After signing in or out, every page shown so far was rendered for the other user.
     * Drops the tabs that are no longer shown and reloads the rest.
     */
    fun authenticationChanged(visibleTabs: List<WebTab>) {
        for (tab in controllers.keys.toList()) {
            if (tab !in visibleTabs) controllers.remove(tab)?.tearDown()
        }
        for (controller in controllers.values) {
            controller.reload()
        }
    }

    /** The configuration changed, perhaps the system's font size: every tab's pages follow it. */
    fun textScaleChanged() {
        for (controller in controllers.values) controller.showTextScale()
    }

    /**
     * Puts each tab's history in [outState], so that when the system stops the app to free
     * memory and the reader comes back, every tab is where it was. A tab not yet shown
     * since the app was started again passes on what it was given.
     */
    fun saveState(outState: Bundle) {
        for ((tab, state) in restored) outState.putBundle(stateKey(tab), state)
        for ((tab, controller) in controllers) {
            controller.saveState()?.let { outState.putBundle(stateKey(tab), it) }
        }
    }

    fun tearDown() {
        connectivity.stop()
        for (controller in controllers.values) controller.tearDown()
        controllers.clear()
    }

    private fun stateKey(tab: WebTab) = "web_tab_${tab.name}"
}

@SuppressLint("SetJavaScriptEnabled")
class WebTabController(
    val tab: WebTab,
    private val activity: Activity,
    private val fileChooser: FileChooser,
    private val storagePermission: StoragePermission,
    savedState: Bundle?,
    private val onPageLoad: () -> Unit,
    private val onRenderProcessGone: () -> Unit
) : DrawingActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val siteHost: String? = Uri.parse(tab.rootUrl).host
    private val siteOrigin: String = Uri.parse(tab.rootUrl).let { uri ->
        "${uri.scheme}://${uri.host}" + if (uri.port != -1) ":${uri.port}" else ""
    }
    private var scriptsAtDocumentStart = false
    private var textScaleScript: ScriptHandler? = null
    private var textScale: Float? = null
    private val dialogs = SiteDialogs(activity)

    /** The drawing a finger last landed on, if it landed on one (DrawingMenu.PRESS_SCRIPT). */
    private var pressedDrawing: DrawingMenu.Drawing? = null

    val webView = WebView(activity)

    /** The tab's view: the web view, pulled down to reload. */
    val view = SwipeRefreshLayout(activity)

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
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webView.webViewClient = Client()
        webView.webChromeClient = ChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(url, userAgent, contentDisposition, mimeType)
        }
        webView.setOnLongClickListener { openDrawingMenu() }
        installLogoutHold()
        installEdgeColorsReport()
        installHaptics()
        installDrawingPress()
        installShare()
        installDownloads()
        installPageScripts()
        showTextScale()

        view.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        view.setOnRefreshListener { webView.reload() }
        // Pulling down reloads only at the top of a page, and never on a canvas being drawn on.
        view.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 || isPainterPage() }

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

    fun tearDown() {
        scope.cancel()
        (view.parent as? ViewGroup)?.removeView(view)
        webView.stopLoading()
        webView.destroy()
    }

    private fun isSiteUrl(uri: Uri): Boolean =
        (uri.scheme == "http" || uri.scheme == "https") && uri.host == siteHost

    private fun isPainterPage(): Boolean {
        val path = webView.url?.let { Uri.parse(it).path } ?: return false
        return PAINTER_PATHS.any { path.startsWith(it) } || path.endsWith("/replay")
    }

    /**
     * Another site's page: in the app that claims its links when one is installed, as a
     * link tapped anywhere else would open, and otherwise over the app in a Custom Tab
     * rather than off in the browser -- the iOS app's Safari view. Anything else (mail,
     * the store) goes to whatever handles it.
     */
    private fun openOutside(uri: Uri) {
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
                    .apply { topColor?.let { setToolbarColor(it.toArgb()) } }
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

    /**
     * Holds a sign-out form's submission until the device's push token is unregistered,
     * which needs the session the sign-out is about to end.
     */
    private fun installLogoutHold() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        val origin = siteOrigin
        WebViewCompat.addWebMessageListener(
            webView, LOGOUT_OBJECT_NAME, setOf(origin)
        ) { _, _, _, isMainFrame, replyProxy ->
            if (!isMainFrame) return@addWebMessageListener
            Log.i(TAG, "Signing out, unregistering push device first")
            scope.launch {
                try {
                    PushNotificationService.getInstance(activity).deleteDevice()
                } finally {
                    replyProxy.postMessage("")
                }
            }
        }
    }

    /**
     * Runs the scripts the bridges need in every page of the site, before its own; or, where
     * the web view can't, once each page has loaded.
     */
    private fun installPageScripts() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        for (script in PAGE_SCRIPTS) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(siteOrigin))
        }
        scriptsAtDocumentStart = true
    }

    /** Has the page say what colors its edges are, whenever that may have changed. */
    private fun installEdgeColorsReport() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            webView, EDGE_COLORS_OBJECT_NAME, setOf(siteOrigin)
        ) { _, message, _, isMainFrame, _ ->
            if (!isMainFrame) return@addWebMessageListener
            val colors = message.data?.split("|") ?: return@addWebMessageListener
            parseCssColor(colors.getOrNull(0))?.let { topColor = it }
            parseCssColor(colors.getOrNull(1))?.let { bottomColor = it }
        }
    }

    /**
     * Lets the site's controls be felt (`feel()` in theme_head.jinja, oeee-cafe/web): it posts
     * "light", "medium", "selection", "success", "warning" or "error" to `window.oeeeHaptic`,
     * the names the iOS app plays with its feedback generators.
     */
    private fun installHaptics() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            webView, HAPTIC_OBJECT_NAME, setOf(siteOrigin)
        ) { _, message, _, isMainFrame, _ ->
            if (!isMainFrame) return@addWebMessageListener
            hapticFeedback(message.data)?.let { webView.performHapticFeedback(it) }
        }
    }

    /**
     * Puts the reader's font size on every page from its first paint, as `--oeee-text-scale`,
     * which the site's type scale follows (ds.css in oeee-cafe/web) -- text grows, pictures,
     * spacing and the painter's chrome do not. Left to itself the web view would instead zoom
     * every piece of text on the page by the font scale (its default text zoom), so that is
     * turned off; unless the page can't be told before it paints, when the zoom stays.
     */
    fun showTextScale() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val fontScale = activity.resources.configuration.fontScale
        if (fontScale == textScale) return
        textScale = fontScale
        webView.settings.textZoom = 100
        val source = textScaleSource(fontScale)
        textScaleScript?.remove()
        textScaleScript = WebViewCompat.addDocumentStartJavaScript(webView, source, setOf(siteOrigin))
        if (webView.url?.let { isSiteUrl(Uri.parse(it)) } == true) {
            webView.evaluateJavascript(source, null)
        }
    }

    /** Hears which drawing a finger lands on, for the menu a long press opens. */
    private fun installDrawingPress() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            webView, DrawingMenu.OBJECT_NAME, setOf(siteOrigin)
        ) { _, message, _, isMainFrame, _ ->
            if (!isMainFrame) return@addWebMessageListener
            pressedDrawing = DrawingMenu.drawing(message.data, webView.url)
        }
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

    private fun installShare() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            webView, SiteBridges.SHARE_OBJECT_NAME, setOf(siteOrigin)
        ) { _, message, _, _, _ ->
            val share = SiteBridges.share(message.data) ?: return@addWebMessageListener
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, share.text)
                if (share.title.isNotEmpty()) putExtra(Intent.EXTRA_TITLE, share.title)
            }
            activity.startActivity(Intent.createChooser(send, share.title.ifEmpty { null }))
        }
    }

    private fun installDownloads() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            webView, SiteBridges.DOWNLOAD_OBJECT_NAME, setOf(siteOrigin)
        ) { _, message, _, _, _ ->
            val file = SiteBridges.download(message.data)
            if (file == null) {
                saved(null)
                return@addWebMessageListener
            }
            scope.launch { saved(saveToSharedFolders(file)) }
        }
    }

    /**
     * A file the site links to, rather than makes: to Downloads through the system's download
     * manager, which shows its progress, signed in as the page is.
     */
    private fun download(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(uri)
            .setTitle(name)
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .apply {
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                // Android 9 and older would need the storage permission for this; there the
                // download manager keeps the file itself, still listed in Downloads.
                if (!MediaFiles.needsStoragePermission) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                }
            }
        try {
            activity.getSystemService(DownloadManager::class.java).enqueue(request)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't download $url", e)
            saved(null)
        }
    }

    /** Says where a file went, or that it didn't, and is felt either way. */
    private fun saved(file: SiteFile?) {
        val message = when {
            file == null -> R.string.save_failed
            file.isImage -> R.string.saved_image
            else -> R.string.saved_file
        }
        webView.performHapticFeedback(
            hapticFeedback(if (file == null) "error" else "success") ?: HapticFeedbackConstants.CONTEXT_CLICK
        )
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    /** [file] once it is in Pictures or Downloads; null if it couldn't be put there. */
    private suspend fun saveToSharedFolders(file: SiteFile): SiteFile? =
        file.takeIf { mayWriteSharedFolders() && MediaFiles.save(activity, it) }

    private suspend fun mayWriteSharedFolders(): Boolean {
        if (!MediaFiles.needsStoragePermission) return true
        return suspendCancellableCoroutine { continuation ->
            storagePermission.request { granted -> if (continuation.isActive) continuation.resume(granted) }
        }
    }

    override fun save(drawing: DrawingMenu.Drawing) {
        scope.launch {
            saved(drawing.file(scope)?.let { saveToSharedFolders(it) })
        }
    }

    override fun copy(drawing: DrawingMenu.Drawing) {
        scope.launch {
            val file = drawing.file(scope) ?: return@launch saved(null)
            MediaFiles.copy(activity, file)
            webView.performHapticFeedback(hapticFeedback("success") ?: HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    override fun share(drawing: DrawingMenu.Drawing) {
        scope.launch {
            val file = drawing.file(scope) ?: return@launch saved(null)
            MediaFiles.share(activity, file, drawing.link, activity.getString(R.string.share_title))
        }
    }

    override fun copyLink(drawing: DrawingMenu.Drawing) {
        val link = drawing.link ?: return
        activity.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newRawUri(link, Uri.parse(link)))
        webView.performHapticFeedback(hapticFeedback("success") ?: HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            // Other sites (and mailto: etc.) open outside the app; embeds in frames load as usual.
            if (request.isForMainFrame && !isSiteUrl(uri) && uri.scheme !in IN_PAGE_SCHEMES) {
                openOutside(uri)
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            isUnreachable = false
            pressedDrawing = null
        }

        override fun onPageFinished(view: WebView, url: String?) {
            this@WebTabController.view.isRefreshing = false
            canGoBack = view.canGoBack()
            if (!scriptsAtDocumentStart && url != null && isSiteUrl(Uri.parse(url))) {
                for (script in PAGE_SCRIPTS) view.evaluateJavascript(script, null)
            }
            WebSession.cookiesMayHaveChanged()
            onPageLoad()
        }

        // Pages the site swaps in without a full load (htmx) come through here.
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            canGoBack = view.canGoBack()
            WebSession.cookiesMayHaveChanged()
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

    private inner class ChromeClient : WebChromeClient() {
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

    companion object {
        private const val TAG = "WebTab"
        private const val USER_AGENT_SUFFIX = "OeeeCafeAndroid"
        private const val LOGOUT_OBJECT_NAME = "oeeeLogout"
        private const val EDGE_COLORS_OBJECT_NAME = "oeeeEdgeColors"
        private const val HAPTIC_OBJECT_NAME = "oeeeHaptic"
        private const val STATE_URL = "url"
        /** A quarter of what the system will carry for the whole app, per tab. */
        private const val MAX_STATE_BYTES = 128 * 1024
        private val UNREACHABLE_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO
        )
        private val IN_PAGE_SCHEMES = setOf("about", "blob", "data", "javascript")
        private val PAINTER_PATHS = listOf("/draw", "/banners/draw", "/collaborate")

        private val LOGOUT_SCRIPT = """
            (function () {
              if (window.__oeeeLogoutHold) return;
              window.__oeeeLogoutHold = true;
              window.addEventListener('submit', function (event) {
                var form = event.target;
                var bridge = window.$LOGOUT_OBJECT_NAME;
                if (!bridge || !(form instanceof HTMLFormElement) || form.dataset.oeeeDeviceReleased) return;
                var action = new URL(form.getAttribute('action') || '', location.href);
                if (action.origin !== location.origin || action.pathname !== '/logout') return;
                event.preventDefault();
                event.stopImmediatePropagation();
                var submitter = event.submitter;
                var resumed = false;
                function resume() {
                  if (resumed) return;
                  resumed = true;
                  form.dataset.oeeeDeviceReleased = '1';
                  if (submitter) { form.requestSubmit(submitter); } else { form.requestSubmit(); }
                }
                bridge.onmessage = resume;
                bridge.postMessage('');
                setTimeout(resume, 10000);
              }, true);
            })();
        """.trimIndent()

        /**
         * Reports the background colors at the page's top and bottom edges, as
         * `<top>|<bottom>`: on load, after the site swaps in a page (htmx), and when its
         * light/dark theme changes.
         */
        private val EDGE_COLORS_SCRIPT = """
            (function () {
              if (window.__oeeeEdgeColors) return;
              window.__oeeeEdgeColors = true;
              var bridge = window.$EDGE_COLORS_OBJECT_NAME;
              if (!bridge) return;
              var last = null;
              function opaque(color) {
                return color && color !== 'transparent' && !/^rgba\(.*,\s*0\)$/.test(color);
              }
              function colorAt(y) {
                for (var el = document.elementFromPoint(1, y); el; el = el.parentElement) {
                  var background = getComputedStyle(el).backgroundColor;
                  if (opaque(background)) return background;
                }
                if (document.body) {
                  var body = getComputedStyle(document.body).backgroundColor;
                  if (opaque(body)) return body;
                }
                return '';
              }
              function report() {
                var colors = colorAt(1) + '|' + colorAt(window.innerHeight - 2);
                if (colors !== last) {
                  last = colors;
                  bridge.postMessage(colors);
                }
              }
              // Theme changes fade in, so look again once they have settled.
              function soon() { report(); setTimeout(report, 350); }
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', soon);
              } else {
                soon();
              }
              window.addEventListener('pageshow', soon);
              document.addEventListener('htmx:afterSettle', soon);
              new MutationObserver(soon).observe(document.documentElement, { attributes: true });
              window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', soon);
            })();
        """.trimIndent()

        /**
         * The font scale as the site's `--oeee-text-scale`, kept within what its layouts were
         * drawn for, as the iOS app keeps Dynamic Type: the largest sizes stop at twice the default.
         */
        internal fun textScaleSource(fontScale: Float): String {
            val scale = fontScale.coerceIn(0.8f, 2f)
            return "document.documentElement.style.setProperty('--oeee-text-scale', '$scale');"
        }

        /** The feedback closest to what each of the site's names plays on iOS; null for others. */
        internal fun hapticFeedback(name: String?): Int? = when (name) {
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

        /** What every page of the site runs for the app's bridges. */
        private val PAGE_SCRIPTS by lazy {
            listOf(
                LOGOUT_SCRIPT,
                EDGE_COLORS_SCRIPT,
                DrawingMenu.PRESS_SCRIPT,
                SiteBridges.SHARE_SCRIPT,
                SiteBridges.DOWNLOAD_SCRIPT
            )
        }

        /** `rgb(r, g, b)` or `rgba(r, g, b, a)`, as `getComputedStyle` gives colors. */
        internal fun parseCssColor(css: String?): Color? {
            val match = CSS_COLOR.matchEntire(css?.trim() ?: return null) ?: return null
            val (r, g, b) = match.destructured
            return Color(r.toFloat().toInt(), g.toFloat().toInt(), b.toFloat().toInt())
        }

        private val CSS_COLOR = Regex("""rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+).*\)""")
    }
}
