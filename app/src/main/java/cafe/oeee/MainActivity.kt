package cafe.oeee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.service.AuthService
import cafe.oeee.data.service.PushNotificationService
import cafe.oeee.ui.theme.OeeeCafeTheme
import cafe.oeee.web.FileChooser
import cafe.oeee.web.StoragePermission
import cafe.oeee.web.WebSession
import cafe.oeee.web.WebTab
import cafe.oeee.web.WebTabStore
import cafe.oeee.web.WebTabsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var webTabs: WebTabStore
    private val badges = BadgeCounts()
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

        ApiClient.initialize(this)
        webTabs = WebTabStore(this, fileChooser, storagePermission, savedInstanceState)
        webTabs.onPageLoad = {
            if (AuthService.isAuthenticated.value) lifecycleScope.launch { badges.refresh() }
        }

        // Handle a notification or link the app was opened from (cold start). Not again when
        // restored, or when reopened from recents, which hands back the intent it was first started with.
        if (savedInstanceState == null) intent?.let { handleNavigationIntent(it) }

        // Coming back to the app may mean new notifications.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (AuthService.isAuthenticated.value) badges.refresh()
            }
        }

        setContent {
            OeeeCafeTheme {
                var isReady by remember { mutableStateOf(false) }
                var selectedTab by rememberSaveable { mutableStateOf(WebTab.HOME) }
                val isAuthenticated by AuthService.isAuthenticated.collectAsState()
                val visibleTabs = WebTab.visible(isAuthenticated)
                val unreadCount by badges.unreadNotifications.collectAsState()
                val invitationCount by badges.invitations.collectAsState()

                LaunchedEffect(Unit) {
                    // Picks up whoever is signed in on the web views before showing any tab.
                    WebSession.start(this@MainActivity)
                    isReady = true

                    var wasAuthenticated: Boolean? = null
                    AuthService.isAuthenticated.collect { authenticated ->
                        if (wasAuthenticated != null) {
                            val tabs = WebTab.visible(authenticated)
                            webTabs.authenticationChanged(tabs)
                            if (selectedTab !in tabs) selectedTab = WebTab.HOME
                        }
                        wasAuthenticated = authenticated
                        authenticationChanged(authenticated)
                    }
                }

                val pending by NavigationCoordinator.pendingNavigation.collectAsState()
                LaunchedEffect(isReady, pending) {
                    val navigation = pending ?: return@LaunchedEffect
                    if (!isReady) return@LaunchedEffect
                    NavigationCoordinator.clearPendingNavigation()
                    // A page in a tab that isn't shown (signed in or out) opens on the home tab.
                    val tab = navigation.tab.takeIf { it in WebTab.visible(AuthService.isAuthenticated.value) }
                        ?: WebTab.HOME
                    selectedTab = tab
                    webTabs.controller(tab).load(ApiClient.getBaseUrl() + navigation.path)
                }

                if (isReady) {
                    WebTabsScreen(
                        store = webTabs,
                        visibleTabs = visibleTabs,
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        badgeCount = { tab ->
                            if (tab == WebTab.NOTIFICATIONS) unreadCount + invitationCount else 0L
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

    override fun onDestroy() {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        webTabs.tearDown()
        super.onDestroy()
    }

    private suspend fun authenticationChanged(isAuthenticated: Boolean) {
        if (isAuthenticated) {
            // Registers this device's push token for the signed-in user (asking for
            // permission the first time).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                registerForPush()
            }
            badges.refresh()
        } else {
            badges.clear()
        }
    }

    private fun registerForPush() {
        lifecycleScope.launch {
            PushNotificationService.getInstance(this@MainActivity).registerFcmToken()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

/** Counts shown on the tab bar, read from the API as the signed-in user. */
class BadgeCounts {
    private val _unreadNotifications = MutableStateFlow(0L)
    val unreadNotifications = _unreadNotifications.asStateFlow()

    private val _invitations = MutableStateFlow(0L)
    val invitations = _invitations.asStateFlow()

    suspend fun refresh() {
        val api = ApiClient.apiService
        try {
            _unreadNotifications.value = api.getUnreadNotificationCount().count
        } catch (e: Exception) {
            // Keep the last count
        }
        try {
            _invitations.value = api.getUserInvitations().invitations.size.toLong()
        } catch (e: Exception) {
            // Keep the last count
        }
    }

    fun clear() {
        _unreadNotifications.value = 0
        _invitations.value = 0
    }
}
