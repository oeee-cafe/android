package cafe.oeee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.service.AuthService
import cafe.oeee.data.service.PushNotificationService
import cafe.oeee.ui.theme.OeeeCafeTheme
import cafe.oeee.web.FileChooser
import cafe.oeee.web.StoragePermission
import cafe.oeee.web.WebSession
import cafe.oeee.web.WebTab
import cafe.oeee.web.WebTabs
import cafe.oeee.web.WebTabsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var webTabs: WebTabs
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) registerForPush()
    }

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
        enableEdgeToEdge()

        webTabs = WebTabs(this, fileChooser, storagePermission, savedInstanceState, AuthService::pageSaid)

        // Handle a notification or link the app was opened from (cold start). Not again when
        // restored, or when reopened from recents, which hands back the intent it was first started with.
        if (savedInstanceState == null) intent?.let { handleNavigationIntent(it) }

        setContent {
            OeeeCafeTheme {
                val isAuthenticated by AuthService.isAuthenticated.collectAsState()
                val visibleTabs = WebTab.visible(isAuthenticated)
                // Read so that a recreated web view is picked up.
                webTabs.generation
                val controller = webTabs.controller

                LaunchedEffect(Unit) {
                    // Picks up whoever is signed in on the web views before anything is fetched.
                    WebSession.start(this@MainActivity)
                    webTabs.start()

                    var wasAuthenticated: Boolean? = null
                    AuthService.isAuthenticated.collect { authenticated ->
                        if (wasAuthenticated != null) webTabs.authenticationChanged(authenticated)
                        authenticationChanged(authenticated, wasSignedIn = wasAuthenticated == true)
                        wasAuthenticated = authenticated
                    }
                }

                val pending by NavigationCoordinator.pendingNavigation.collectAsState()
                LaunchedEffect(controller, pending) {
                    val navigation = pending ?: return@LaunchedEffect
                    val shown = controller ?: return@LaunchedEffect
                    NavigationCoordinator.clearPendingNavigation()
                    // The web view shows it, and the bar draws whichever section it turns
                    // out to be in (WebTabController.section).
                    shown.load(ApiClient.BASE_URL + navigation.path)
                }

                if (controller != null) {
                    WebTabsScreen(
                        controller = controller,
                        visibleTabs = visibleTabs,
                        badgeCount = { tab ->
                            if (tab == WebTab.NOTIFICATIONS && isAuthenticated) webTabs.unreadCount.toLong() else 0L
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle a notification or link (warm/hot start)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        NavigationCoordinator.handleNotificationIntent(intent)
        NavigationCoordinator.handleLinkIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webTabs.saveState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webTabs.textScaleChanged()
    }

    // The web views' cookie store writes itself out now and then; the session, and the
    // device cookie beside it, should not wait for that when the app may be stopped.
    override fun onResume() {
        super.onResume()
        // Back from a browser, perhaps: the tabs' pages ask the site whether a sign-in
        // sent out there has finished (WebTabs.resumed).
        webTabs.resumed()
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        webTabs.tearDown()
        super.onDestroy()
    }

    /**
     * Registers this device's push token for whoever signed in (asking for permission the
     * first time). Signing out on the site has unregistered it there already (POST /logout
     * reads the device cookie), so all that is left is to forget which token it was.
     */
    private fun authenticationChanged(isAuthenticated: Boolean, wasSignedIn: Boolean) {
        if (isAuthenticated) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                registerForPush()
            }
        } else if (wasSignedIn) {
            PushNotificationService.forgetDevice()
        }
    }

    private fun registerForPush() {
        lifecycleScope.launch { PushNotificationService.registerFcmToken() }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
