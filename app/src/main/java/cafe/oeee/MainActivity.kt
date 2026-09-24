package cafe.oeee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import cafe.oeee.data.service.PushNotificationService
import cafe.oeee.ui.theme.OeeeCafeTheme
import cafe.oeee.web.Connectivity
import cafe.oeee.web.FileChooser
import cafe.oeee.web.Site
import cafe.oeee.web.StoragePermission
import cafe.oeee.web.WebController
import cafe.oeee.web.WebScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /**
     * The app's one web view, with the site's own toolbar the only way around it. A new one
     * when the renderer behind it dies, so the screen shows whichever is current.
     */
    private lateinit var web: MutableState<WebController>

    /** A page that could not be reached is tried again when the network comes back. */
    private lateinit var connectivity: Connectivity

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    /** Asks for notifications; the push token is fetched either way (someoneSignedIn). */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingFileCallback?.onReceiveValue(
            android.webkit.WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        pendingFileCallback = null
    }

    private val fileChooser = FileChooser { callback, params ->
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = callback
        try {
            fileChooserLauncher.launch(params.createIntent())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open a file chooser", e)
            pendingFileCallback = null
            false
        }
    }

    private var pendingStorageResult: ((Boolean) -> Unit)? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingStorageResult?.invoke(granted)
        pendingStorageResult = null
    }

    private val storagePermission = StoragePermission { onResult ->
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
        } else {
            pendingStorageResult?.invoke(false)
            pendingStorageResult = onResult
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The navigation bar clear, with no scrim: WebScreen paints the site's ground
        // under it and picks icons that read on that. Left to decide, a three-button bar
        // is washed over with a translucent white or grey "for contrast", and never
        // matches the page.
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        // It is a setting of the whole process, not of any one web view.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        // Where the reader was when the system stopped the app, or else the site's first page.
        web = mutableStateOf(makeWebView(savedInstanceState?.getBundle(STATE_KEY)))
        connectivity = Connectivity(this) {
            if (!isDestroyed) web.value.retryIfUnreachable()
        }.also { it.start() }

        // Open a notification or link the app was opened from (cold start). Not again when
        // restored, or when reopened from recents, which hands back the intent it was first started with.
        if (savedInstanceState == null) intent?.let { openPageFrom(it) }

        setContent {
            OeeeCafeTheme {
                WebScreen(web.value)
            }
        }
    }

    private fun makeWebView(savedState: Bundle?): WebController = WebController(
        activity = this,
        fileChooser = fileChooser,
        storagePermission = storagePermission,
        savedState = savedState,
        onSignedIn = ::pageSaid,
        onRenderProcessGone = {
            // A web view whose renderer is gone can't be used again; starts the site over.
            web.value.tearDown()
            web.value = makeWebView(null)
        }
    )

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle a notification or link (warm/hot start)
        setIntent(intent)
        openPageFrom(intent)
    }

    private fun openPageFrom(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val path = OpenedFrom.path(intent) ?: return
        web.value.load(Site.BASE_URL + path)
    }

    /**
     * Puts the history in [outState], so that when the system stops the app to free memory
     * and the reader comes back, they are where they were.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.value.saveState()?.let { outState.putBundle(STATE_KEY, it) }
    }

    /** The configuration changed, perhaps the system's font size: the pages follow it. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        web.value.showTextScale()
    }

    /**
     * The app is in front again. A sign-in sent out to a browser (SignInHandoff) may have
     * finished while it was away, so the page asks the site at once rather than waiting
     * for the next turn of its own clock.
     */
    override fun onResume() {
        super.onResume()
        web.value.resumed()
    }

    // The web views' cookie store writes itself out now and then; the session should not
    // wait for that when the app may be stopped.
    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        connectivity.stop()
        web.value.tearDown()
        super.onDestroy()
    }

    /**
     * Whether the pages last said someone is signed in. The activity's, not the web view's,
     * so that a new web view after its renderer died is not taken for a new sign-in.
     */
    private var signedIn = false

    private fun pageSaid(signedIn: Boolean) {
        if (signedIn == this.signedIn) return
        this.signedIn = signedIn
        if (signedIn) someoneSignedIn()
    }

    /**
     * Someone has signed in: notifications are asked for the first time, and this device's
     * push token is fetched for the pages to register (PushNotificationService). The token
     * is fetched whether or not notifications are allowed: FCM hands it over on install
     * either way, and the pages have always been given it, so a device is already
     * registered when they are allowed later in the system's settings. Signing out needs
     * nothing of the app: the site's sign-out unregisters the device the page registered.
     */
    private fun someoneSignedIn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch { PushNotificationService.fetchToken() }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val STATE_KEY = "web_view"
    }
}
