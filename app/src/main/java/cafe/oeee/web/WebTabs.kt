package cafe.oeee.web

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.oeee.BuildConfig

/**
 * The web view the tabs share, and what the app keeps around it: where it was when the
 * system stopped the app ([saveState]), the number on the bell, and starting it over when
 * its renderer dies.
 *
 * One web view rather than one per tab. A tab of its own for each kept a history and a
 * scroll position per section, and cost a navigation that could disagree with itself: the
 * site's toolbar has these same sections in it, so a section could arrive in the wrong tab
 * and had to be moved to its own. The bar picks what the one web view shows, and the web
 * view says which section it turned out to be in ([WebTabController.section]).
 */
class WebTabs(
    private val activity: Activity,
    private val fileChooser: FileChooser,
    private val storagePermission: StoragePermission,
    savedState: Bundle?,
    /** A page said whether someone is signed in (BridgeMessage.Page.signedIn). */
    private val onSignedIn: (Boolean) -> Unit
) {
    /** The saved history, until the web view is made and takes it. */
    private var restored: Bundle? = savedState?.getBundle(STATE_KEY)

    /** Bumped when the web view is recreated, so the screen shows the new one. */
    var generation by mutableIntStateOf(0)
        private set

    /**
     * The number on the site's bell, as the page last shown said it: every page with the
     * toolbar says it, so there is nothing to ask the API for.
     */
    var unreadCount by mutableIntStateOf(0)
        private set

    var controller by mutableStateOf<WebTabController?>(null)
        private set

    init {
        // It is a setting of the whole process, not of any one web view.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }

    /** A page that could not be reached is tried again when the network comes back. */
    private val connectivity = Connectivity(activity) {
        controller?.retryIfUnreachable()
    }.also { it.start() }

    /**
     * Makes the web view, and shows the site's first page. Asked for rather than done on
     * its own, so that whoever is signed in on the web views has been picked up before
     * anything is fetched (WebSession).
     */
    fun start(): WebTabController = controller ?: create()

    private fun create(): WebTabController {
        val made = WebTabController(
            activity = activity,
            fileChooser = fileChooser,
            storagePermission = storagePermission,
            savedState = restored.also { restored = null },
            onPage = { page -> page.signedIn?.let(onSignedIn) },
            onUnread = { unreadCount = it },
            onRenderProcessGone = { recreate() }
        )
        controller = made
        return made
    }

    /** A web view whose renderer is gone can't be used again; starts the site over. */
    private fun recreate() {
        controller?.tearDown()
        controller = null
        create()
        generation++
    }

    /**
     * The app is in front again. A sign-in sent out to a browser (SignInHandoff) may have
     * finished while it was away, so the page asks the site at once rather than waiting
     * for the next turn of its own clock.
     */
    fun resumed() {
        controller?.resumed()
    }

    /**
     * After signing in or out, the page showing was rendered for whoever was signed in
     * before -- unless it is the page that said so, which is where signing in ends and
     * which carries the notice that it worked. Reloading that one would throw the notice
     * away, and it holds the right number for the bell besides.
     */
    fun authenticationChanged(signedIn: Boolean) {
        val controller = controller ?: return
        if (controller.lastSignedIn == signedIn) return
        unreadCount = 0
        controller.reload()
    }

    /** The configuration changed, perhaps the system's font size: the pages follow it. */
    fun textScaleChanged() {
        controller?.showTextScale()
    }

    /**
     * Puts the history in [outState], so that when the system stops the app to free memory
     * and the reader comes back, they are where they were. Before the web view is made,
     * what it was given is passed on.
     */
    fun saveState(outState: Bundle) {
        val state = controller?.saveState() ?: restored
        state?.let { outState.putBundle(STATE_KEY, it) }
    }

    fun tearDown() {
        connectivity.stop()
        controller?.tearDown()
        controller = null
    }

    private companion object {
        const val STATE_KEY = "web_view"
    }
}
