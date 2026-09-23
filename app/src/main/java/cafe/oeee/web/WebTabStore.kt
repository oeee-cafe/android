package cafe.oeee.web

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cafe.oeee.BuildConfig

/**
 * Owns one long-lived web view per tab, so each tab keeps its own history and scroll position
 * -- across the app being stopped and started again by the system, too ([saveState]).
 */
class WebTabStore(
    private val activity: Activity,
    private val fileChooser: FileChooser,
    private val storagePermission: StoragePermission,
    savedState: Bundle?,
    /** A page said whether someone is signed in (BridgeMessage.Page.signedIn). */
    private val onSignedIn: (Boolean) -> Unit
) {
    private val controllers = mutableMapOf<WebTab, WebTabController>()

    /** Read once for every tab, rather than from the assets again for each web view. */
    private val scripts = PageScripts(activity.assets)

    /** Each tab's saved history, until the tab is first shown and takes it. */
    private val restored: MutableMap<WebTab, Bundle> = WebTab.entries
        .mapNotNull { tab -> savedState?.getBundle(stateKey(tab))?.let { tab to it } }
        .toMap().toMutableMap()

    /**
     * The app is in front again. A sign-in sent out to a browser (SignInHandoff) may have
     * finished while it was away, so the pages ask the site at once rather than waiting
     * for the next turn of their own clock.
     */
    fun resumed() {
        for (controller in controllers.values) controller.resumed()
    }

    /** A page that could not be reached is tried again when the network comes back. */
    private val connectivity = Connectivity(activity) {
        for (controller in controllers.values) controller.retryIfUnreachable()
    }.also { it.start() }

    /** Bumped when a tab's web view is recreated, so the tab screen shows the new one. */
    var generation by mutableIntStateOf(0)
        private set

    /**
     * The number on the site's bell, as the page last shown in any tab said it: every page
     * with the toolbar says it, so there is nothing to ask the API for.
     */
    var unreadCount by mutableIntStateOf(0)
        private set

    init {
        // It is a setting of the whole process, not of any one web view.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }

    fun controller(tab: WebTab): WebTabController =
        controllers[tab] ?: create(tab)

    private fun create(tab: WebTab): WebTabController {
        val controller = WebTabController(
            tab = tab,
            activity = activity,
            scripts = scripts,
            fileChooser = fileChooser,
            storagePermission = storagePermission,
            savedState = restored.remove(tab),
            onPage = { page -> page.signedIn?.let(onSignedIn) },
            onUnread = { unreadCount = it },
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
     * Drops the tabs that are no longer shown and reloads the rest, which say the new
     * count on the bell as they come back.
     */
    fun authenticationChanged(visibleTabs: List<WebTab>) {
        unreadCount = 0
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
