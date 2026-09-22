package cafe.oeee.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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

/** Owns one long-lived web view per tab, so each tab keeps its own history and scroll position. */
class WebTabStore(private val activity: Activity, private val fileChooser: FileChooser) {
    private val controllers = mutableMapOf<WebTab, WebTabController>()

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

    fun tearDown() {
        for (controller in controllers.values) controller.tearDown()
        controllers.clear()
    }
}

@SuppressLint("SetJavaScriptEnabled")
class WebTabController(
    val tab: WebTab,
    private val activity: Activity,
    private val fileChooser: FileChooser,
    private val onPageLoad: () -> Unit,
    private val onRenderProcessGone: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val siteHost: String? = Uri.parse(tab.rootUrl).host
    private val siteOrigin: String = Uri.parse(tab.rootUrl).let { uri ->
        "${uri.scheme}://${uri.host}" + if (uri.port != -1) ":${uri.port}" else ""
    }
    private var scriptsAtDocumentStart = false
    private var textScaleScript: ScriptHandler? = null
    private var textScale: Float? = null

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
        installLogoutHold()
        installEdgeColorsReport()
        installHaptics()
        showTextScale()

        view.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        view.setOnRefreshListener { webView.reload() }
        // Pulling down reloads only at the top of a page, and never on a canvas being drawn on.
        view.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 || isPainterPage() }

        // Search shows nothing until something is searched for.
        if (tab != WebTab.SEARCH) {
            load(tab.rootUrl)
        }
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

    private fun openOutside(uri: Uri) {
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
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, LOGOUT_SCRIPT, setOf(origin))
            WebViewCompat.addDocumentStartJavaScript(webView, EDGE_COLORS_SCRIPT, setOf(origin))
            scriptsAtDocumentStart = true
        }
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

        override fun onPageFinished(view: WebView, url: String?) {
            this@WebTabController.view.isRefreshing = false
            if (!scriptsAtDocumentStart && url != null && isSiteUrl(Uri.parse(url))) {
                view.evaluateJavascript(LOGOUT_SCRIPT, null)
                view.evaluateJavascript(EDGE_COLORS_SCRIPT, null)
            }
            WebSession.cookiesMayHaveChanged()
            onPageLoad()
        }

        // Pages the site swaps in without a full load (htmx) come through here.
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            WebSession.cookiesMayHaveChanged()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                this@WebTabController.view.isRefreshing = false
                Log.w(TAG, "${tab.name}: Failed to load ${request.url} - ${error.description}")
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
    }

    companion object {
        private const val TAG = "WebTab"
        private const val USER_AGENT_SUFFIX = "OeeeCafeAndroid"
        private const val LOGOUT_OBJECT_NAME = "oeeeLogout"
        private const val EDGE_COLORS_OBJECT_NAME = "oeeeEdgeColors"
        private const val HAPTIC_OBJECT_NAME = "oeeeHaptic"
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

        /** `rgb(r, g, b)` or `rgba(r, g, b, a)`, as `getComputedStyle` gives colors. */
        internal fun parseCssColor(css: String?): Color? {
            val match = CSS_COLOR.matchEntire(css?.trim() ?: return null) ?: return null
            val (r, g, b) = match.destructured
            return Color(r.toFloat().toInt(), g.toFloat().toInt(), b.toFloat().toInt())
        }

        private val CSS_COLOR = Regex("""rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+).*\)""")
    }
}
