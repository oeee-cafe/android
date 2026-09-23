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
 * The app's one web view, and what the app keeps around it: where it was when the system
 * stopped the app ([saveState]), and starting it over when its renderer dies. The site's own
 * toolbar is the only way around it.
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
     * its own, so that the web views take cookies before anything is fetched (MainActivity).
     */
    fun start(): WebTabController = controller ?: create()

    private fun create(): WebTabController {
        val made = WebTabController(
            activity = activity,
            fileChooser = fileChooser,
            storagePermission = storagePermission,
            savedState = restored.also { restored = null },
            onPage = { page -> page.signedIn?.let(onSignedIn) },
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
