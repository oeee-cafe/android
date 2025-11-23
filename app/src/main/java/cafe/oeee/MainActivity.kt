package cafe.oeee

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.service.AuthService
import cafe.oeee.data.service.PushNotificationService
import cafe.oeee.ui.community.CommunityDetailScreen
import cafe.oeee.ui.community.CommunityInvitationsScreen
import cafe.oeee.ui.community.CommunityMembersScreen
import cafe.oeee.ui.community.InviteUserScreen
import cafe.oeee.data.service.NotificationService
import cafe.oeee.ui.bannermanagement.BannerManagementScreen
import cafe.oeee.ui.bannermanagement.BannerDrawWebViewScreen
import cafe.oeee.ui.drafts.DraftsScreen
import cafe.oeee.ui.home.HomeScreen
import cafe.oeee.ui.login.LoginScreen
import cafe.oeee.ui.notifications.NotificationsScreen
import cafe.oeee.ui.signup.SignupScreen
import cafe.oeee.ui.postdetail.PostDetailScreen
import cafe.oeee.ui.postdetail.ReplayWebViewScreen
import cafe.oeee.ui.profile.ProfileScreen
import cafe.oeee.ui.search.SearchScreen
import cafe.oeee.ui.settings.SettingsScreen
import cafe.oeee.ui.theme.OeeeCafeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        // Shared navigation event flow for handling deep links from notifications
        val navigationEventFlow = MutableStateFlow<String?>(null)
    }

    // Modern permission launcher
    private val pushNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pushService = PushNotificationService.getInstance(this)
        lifecycleScope.launch {
            pushService.handlePermissionResult(granted)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize ApiClient with persistent cookie store
        ApiClient.initialize(this)

        // Check auth status on app launch
        val authService = AuthService.getInstance(this)
        if (authService.hasStoredAuthState()) {
            lifecycleScope.launch {
                authService.checkAuthStatus()
            }
        }

        // Handle notification deep link (cold start)
        intent?.let { handleNotificationIntent(it) }

        setContent {
            OeeeCafeTheme {
                AppNavigation(
                    pushNotificationPermissionLauncher = pushNotificationPermissionLauncher
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle notification deep link (warm/hot start)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        // Extract notification data from intent extras
        val notificationType = intent.getStringExtra("notification_type")
        if (notificationType == null) {
            android.util.Log.d("MainActivity", "No notification_type in intent, skipping deep link")
            return
        }

        android.util.Log.i("MainActivity", "Handling notification tap: type=$notificationType")

        // Map notification type to navigation route
        val route = when (notificationType) {
            "Comment", "Mention", "CommentReply", "PostReply", "CommunityPost", "Reaction" -> {
                // Navigate to post detail
                val postId = intent.getStringExtra("post_id")
                if (postId != null) {
                    "post/$postId"
                } else {
                    android.util.Log.w("MainActivity", "Missing post_id for $notificationType")
                    null
                }
            }

            "Follow", "GuestbookEntry", "GuestbookReply" -> {
                // Navigate to actor's profile
                val actorLoginName = intent.getStringExtra("actor_login_name")
                if (actorLoginName != null) {
                    "profile/$actorLoginName"
                } else {
                    android.util.Log.w("MainActivity", "Missing actor_login_name for $notificationType")
                    null
                }
            }

            "community_invite" -> {
                // Navigate to community invitations screen
                "community-invitations"
            }

            "invitation_accepted", "invitation_declined" -> {
                // Navigate to community members screen
                val communitySlug = intent.getStringExtra("community_slug")
                if (communitySlug != null) {
                    "community/$communitySlug/members"
                } else {
                    android.util.Log.w("MainActivity", "Missing community_slug for $notificationType")
                    null
                }
            }

            else -> {
                android.util.Log.w("MainActivity", "Unknown notification type: $notificationType")
                null
            }
        }

        route?.let {
            android.util.Log.i("MainActivity", "Emitting navigation event: $it")
            navigationEventFlow.value = it
        }
    }

    private fun clearNotificationBadge() {
        try {
            // Cancel all notifications to clear the badge
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.cancelAll()
        } catch (e: Exception) {
            // Silently fail if badge clearing doesn't work
            android.util.Log.w("MainActivity", "Failed to clear notification badge", e)
        }
    }
}

@Composable
fun AppNavigation(
    pushNotificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val context = LocalContext.current
    val authService = AuthService.getInstance(context)
    val notificationService = NotificationService.getInstance(context)
    val pushService = PushNotificationService.getInstance(context)
    val isCheckingAuth by authService.isCheckingAuth.collectAsState()
    val isAuthenticated by authService.isAuthenticated.collectAsState()
    val currentUser by authService.currentUser.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val unreadCount = MutableStateFlow(0L)
    val invitationCount = MutableStateFlow(0)
    val draftCount = MutableStateFlow(0)

    // Handle deep link navigation from push notification
    val navigationEvent by MainActivity.navigationEventFlow.collectAsState()
    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { route ->
            android.util.Log.i("AppNavigation", "Handling navigation event: $route")
            navController.navigate(route) {
                // Don't clear back stack - let user navigate back normally
                launchSingleTop = true
            }
            // Clear the event after handling
            MainActivity.navigationEventFlow.value = null
        }
    }

    // Refresh counts when returning to main tabs
    LaunchedEffect(currentRoute) {
        if (isAuthenticated && currentRoute in listOf("home", "communities", "drafts", "notifications", "myprofile")) {
            launch {
                try {
                    val response = ApiClient.apiService.getUserInvitations()
                    invitationCount.value = response.invitations.size
                } catch (e: Exception) {
                    // Ignore error
                }
            }
            launch {
                try {
                    val response = ApiClient.apiService.getDraftPosts()
                    draftCount.value = response.drafts.size
                } catch (e: Exception) {
                    // Ignore error
                }
            }
        }
    }

    // Request push notification permissions after login
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Check if permission is already granted
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                    // Already granted, just register
                    pushService.handlePermissionResult(true)
                } else {
                    // Request permission using the launcher
                    pushNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                // For Android 12 and below, notifications are enabled by default
                pushService.handlePermissionResult(true)
            }
        }
    }

    // Update counts when authenticated
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            launch {
                notificationService.getUnreadCount().fold(
                    onSuccess = { count -> unreadCount.value = count },
                    onFailure = { }
                )
            }
            launch {
                try {
                    val response = ApiClient.apiService.getUserInvitations()
                    invitationCount.value = response.invitations.size
                } catch (e: Exception) {
                    // Ignore error
                }
            }
            launch {
                try {
                    val response = ApiClient.apiService.getDraftPosts()
                    draftCount.value = response.drafts.size
                } catch (e: Exception) {
                    // Ignore error
                }
            }
        } else {
            // Clear counts when logged out
            unreadCount.value = 0L
            invitationCount.value = 0
            draftCount.value = 0
        }
    }

    // Show loading while checking auth
    if (isCheckingAuth) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Determine if we should show bottom navigation
    val showBottomNav = currentRoute in listOf("home", "communities", "drafts", "notifications", "myprofile")

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTabIndex == 0,
                        onClick = {
                            selectedTabIndex = 0
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selectedTabIndex == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = stringResource(R.string.home_title)
                            )
                        },
                        label = { Text(stringResource(R.string.home_title)) },
                        alwaysShowLabel = true
                    )

                    NavigationBarItem(
                        selected = selectedTabIndex == 1,
                        onClick = {
                            selectedTabIndex = 1
                            navController.navigate("communities") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selectedTabIndex == 1) Icons.Filled.Group else Icons.Outlined.Group,
                                contentDescription = stringResource(R.string.communities_title)
                            )
                        },
                        label = { Text(stringResource(R.string.communities_title)) },
                        alwaysShowLabel = true
                    )

                    if (isAuthenticated) {
                        val drafts by draftCount.collectAsState()
                        NavigationBarItem(
                            selected = selectedTabIndex == 2,
                            onClick = {
                                selectedTabIndex = 2
                                navController.navigate("drafts") {
                                    popUpTo("home") { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (drafts > 0) {
                                            Badge {
                                                Text(drafts.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTabIndex == 2) Icons.Filled.Description else Icons.Outlined.Description,
                                        contentDescription = stringResource(R.string.drafts_title)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.drafts_title)) },
                            alwaysShowLabel = true
                        )

                        val unread by unreadCount.collectAsState()
                        val invitations by invitationCount.collectAsState()
                        val totalBadgeCount = unread + invitations
                        NavigationBarItem(
                            selected = selectedTabIndex == 3,
                            onClick = {
                                selectedTabIndex = 3
                                navController.navigate("notifications") {
                                    popUpTo("home") { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (totalBadgeCount > 0) {
                                            Badge {
                                                Text(totalBadgeCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTabIndex == 3) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                        contentDescription = stringResource(R.string.notifications_title)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.notifications_title)) },
                            alwaysShowLabel = true
                        )

                        NavigationBarItem(
                            selected = selectedTabIndex == 4,
                            onClick = {
                                selectedTabIndex = 4
                                navController.navigate("myprofile") {
                                    popUpTo("home") { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTabIndex == 4) Icons.Filled.Person else Icons.Outlined.Person,
                                    contentDescription = stringResource(R.string.profile_title)
                                )
                            },
                            label = { Text(stringResource(R.string.profile_title)) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
        composable("home") {
            selectedTabIndex = 0
            HomeScreen(
                onPostClick = { postId ->
                    navController.navigate("post/$postId")
                },
                onCommunityClick = { slug ->
                    navController.navigate("community/$slug")
                },
                onProfileClick = { loginName ->
                    navController.navigate("profile/$loginName")
                },
                onLoginClick = {
                    navController.navigate("login")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onDrawClick = {
                    navController.navigate("dimensionpicker")
                },
                onSearchClick = {
                    navController.navigate("search")
                }
            )
        }

        composable("search") {
            SearchScreen(
                onPostClick = { postId ->
                    navController.navigate("post/$postId")
                },
                onProfileClick = { loginName ->
                    navController.navigate("profile/$loginName")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable("communities") {
            selectedTabIndex = 1
            cafe.oeee.ui.communities.CommunitiesScreen(
                onCommunityClick = { slug ->
                    navController.navigate("community/$slug")
                },
                onPostClick = { postId ->
                    navController.navigate("post/$postId")
                },
                onCreateCommunityClick = {
                    navController.navigate("create-community")
                }
            )
        }

        composable("create-community") {
            cafe.oeee.ui.community.CreateCommunityScreen(
                onNavigateBack = { navController.popBackStack() },
                onCommunityCreated = { slug ->
                    navController.popBackStack()
                    navController.navigate("community/$slug")
                }
            )
        }

        composable("drafts") {
            selectedTabIndex = 2
            DraftsScreen(
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onDraftClick = { postId, communityId, imageUrl ->
                    val encodedImageUrl = java.net.URLEncoder.encode(imageUrl, "UTF-8")
                    val communityIdParam = communityId ?: "none"
                    navController.navigate("draftpost/$postId/$communityIdParam/$encodedImageUrl")
                }
            )
        }

        composable("notifications") {
            selectedTabIndex = 3
            NotificationsScreen(
                onPostClick = { postId ->
                    navController.navigate("post/$postId")
                },
                onProfileClick = { loginName ->
                    navController.navigate("profile/$loginName")
                },
                onInvitationsClick = {
                    navController.navigate("community-invitations")
                },
                invitationCountFlow = invitationCount,
                unreadCountFlow = unreadCount
            )
        }

        composable("myprofile") {
            selectedTabIndex = 4
            currentUser?.let { user ->
                ProfileScreen(
                    loginName = user.loginName,
                    currentUserLoginName = user.loginName,
                    onNavigateBack = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onPostClick = { postId ->
                        navController.navigate("post/$postId")
                    },
                    onProfileClick = { loginName ->
                        navController.navigate("profile/$loginName")
                    },
                    onFollowingListClick = { followingLoginName ->
                        navController.navigate("followinglist/$followingLoginName")
                    }
                )
            }
        }

        composable("login") {
            LoginScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignupClick = { navController.navigate("signup") }
            )
        }

        composable("signup") {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBannerManagement = { navController.navigate("bannerManagement") }
            )
        }

        composable("bannerManagement") { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val reloadTrigger = savedStateHandle.getStateFlow("reload_trigger", 0)
                .collectAsState()

            BannerManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDrawBanner = { navController.navigate("bannerDraw") },
                reloadTrigger = reloadTrigger.value
            )
        }

        composable("bannerDraw") {
            BannerDrawWebViewScreen(
                onNavigateBack = { navController.popBackStack() },
                onBannerComplete = { bannerId, imageUrl ->
                    // Trigger reload in the previous screen
                    navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                        val currentValue = handle.get<Int>("reload_trigger") ?: 0
                        handle["reload_trigger"] = currentValue + 1
                    }
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "dimensionpicker?parentPostId={parentPostId}&communityId={communityId}&backgroundColor={backgroundColor}&foregroundColor={foregroundColor}",
            arguments = listOf(
                navArgument("parentPostId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("communityId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("backgroundColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("foregroundColor") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val parentPostId = backStackEntry.arguments?.getString("parentPostId")
            val communityId = backStackEntry.arguments?.getString("communityId")
            val backgroundColor = backStackEntry.arguments?.getString("backgroundColor")
            val foregroundColor = backStackEntry.arguments?.getString("foregroundColor")
            cafe.oeee.ui.components.CanvasDimensionPicker(
                onDimensionsSelected = { dimensions ->
                    var route = "draw/${dimensions.width}/${dimensions.height}/${dimensions.tool.value}"
                    val params = mutableListOf<String>()
                    if (parentPostId != null) params.add("parentPostId=$parentPostId")
                    if (communityId != null) params.add("communityId=$communityId")
                    if (params.isNotEmpty()) {
                        route += "?" + params.joinToString("&")
                    }
                    navController.navigate(route) {
                        popUpTo("dimensionpicker?parentPostId={parentPostId}&communityId={communityId}&backgroundColor={backgroundColor}&foregroundColor={foregroundColor}") { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
                backgroundColor = backgroundColor,
                foregroundColor = foregroundColor
            )
        }

        composable(
            route = "orientationpicker?parentPostId={parentPostId}&communityId={communityId}",
            arguments = listOf(
                navArgument("parentPostId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("communityId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val parentPostId = backStackEntry.arguments?.getString("parentPostId")
            val communityId = backStackEntry.arguments?.getString("communityId")
            cafe.oeee.ui.components.OrientationPickerScreen(
                onOrientationSelected = { width, height ->
                    var route = "draw/$width/$height/neo-cucumber-offline"
                    val params = mutableListOf<String>()
                    if (parentPostId != null) params.add("parentPostId=$parentPostId")
                    if (communityId != null) params.add("communityId=$communityId")
                    if (params.isNotEmpty()) {
                        route += "?" + params.joinToString("&")
                    }
                    navController.navigate(route) {
                        popUpTo("orientationpicker?parentPostId={parentPostId}&communityId={communityId}") { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = "draw/{width}/{height}/{tool}?parentPostId={parentPostId}&communityId={communityId}",
            arguments = listOf(
                navArgument("width") { type = NavType.IntType },
                navArgument("height") { type = NavType.IntType },
                navArgument("tool") { type = NavType.StringType },
                navArgument("parentPostId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("communityId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val width = backStackEntry.arguments?.getInt("width") ?: 300
            val height = backStackEntry.arguments?.getInt("height") ?: 300
            val tool = backStackEntry.arguments?.getString("tool") ?: "neo"
            val parentPostId = backStackEntry.arguments?.getString("parentPostId")
            val communityId = backStackEntry.arguments?.getString("communityId")
            cafe.oeee.ui.draw.DrawWebViewScreen(
                width = width,
                height = height,
                tool = tool,
                communityId = communityId,
                parentPostId = parentPostId,
                onNavigateBack = { navController.popBackStack() },
                onDrawingComplete = { postId, returnedCommunityId, imageUrl ->
                    // Navigate to draft post form after drawing completion
                    val encodedImageUrl = java.net.URLEncoder.encode(imageUrl, "UTF-8")
                    // Use placeholder "none" for null communityId (personal posts)
                    val communityIdParam = returnedCommunityId ?: "none"
                    val parentPostIdParam = if (parentPostId != null) "?parentPostId=$parentPostId" else ""
                    navController.navigate("draftpost/$postId/$communityIdParam/$encodedImageUrl$parentPostIdParam") {
                        popUpTo("draw/{width}/{height}/{tool}?parentPostId={parentPostId}&communityId={communityId}") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "draftpost/{postId}/{communityId}/{imageUrl}?parentPostId={parentPostId}",
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType },
                navArgument("communityId") { type = NavType.StringType },
                navArgument("imageUrl") { type = NavType.StringType },
                navArgument("parentPostId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            val communityIdParam = backStackEntry.arguments?.getString("communityId") ?: return@composable
            // Convert "none" placeholder or empty string to null for personal posts
            val communityId = if (communityIdParam.isEmpty() || communityIdParam == "none") null else communityIdParam
            val encodedImageUrl = backStackEntry.arguments?.getString("imageUrl") ?: return@composable
            val imageUrl = java.net.URLDecoder.decode(encodedImageUrl, "UTF-8")
            val parentPostId = backStackEntry.arguments?.getString("parentPostId")
            cafe.oeee.ui.draftpost.DraftPostScreen(
                postId = postId,
                communityId = communityId,
                imageUrl = imageUrl,
                parentPostId = parentPostId,
                onNavigateBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onPublished = { publishedPostId ->
                    navController.navigate("post/$publishedPostId") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = "post/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            PostDetailScreen(
                postId = postId,
                currentUserId = currentUser?.id,
                currentUserLoginName = currentUser?.loginName,
                onNavigateBack = { navController.popBackStack() },
                onProfileClick = { loginName ->
                    navController.navigate("profile/$loginName")
                },
                onCommunityClick = { slug ->
                    navController.navigate("community/$slug")
                },
                onReplyClick = { backgroundColor, foregroundColor, communityId ->
                    if (backgroundColor != null && foregroundColor != null && communityId != null) {
                        // Two-tone community: show orientation picker
                        navController.navigate("orientationpicker?communityId=$communityId&parentPostId=$postId")
                    } else {
                        navController.navigate("dimensionpicker?parentPostId=$postId")
                    }
                },
                onReplayClick = {
                    if (isAuthenticated) {
                        navController.navigate("post/$postId/replay")
                    } else {
                        navController.navigate("login")
                    }
                }
            )
        }

        composable(
            route = "post/{postId}/replay",
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            ReplayWebViewScreen(
                postId = postId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "profile/{loginName}",
            arguments = listOf(navArgument("loginName") { type = NavType.StringType })
        ) { backStackEntry ->
            val loginName = backStackEntry.arguments?.getString("loginName") ?: return@composable
            ProfileScreen(
                loginName = loginName,
                currentUserLoginName = currentUser?.loginName,
                onNavigateBack = { navController.popBackStack() },
                onPostClick = { postId ->
                    navController.navigate("post/$postId")
                },
                onProfileClick = { otherLoginName ->
                    navController.navigate("profile/$otherLoginName")
                },
                onFollowingListClick = { followingLoginName ->
                    navController.navigate("followinglist/$followingLoginName")
                }
            )
        }

        composable(
            route = "followinglist/{loginName}",
            arguments = listOf(navArgument("loginName") { type = NavType.StringType })
        ) { backStackEntry ->
            val loginName = backStackEntry.arguments?.getString("loginName") ?: return@composable

            cafe.oeee.ui.followinglist.FollowingListScreen(
                loginName = loginName,
                onNavigateBack = { navController.popBackStack() },
                onProfileClick = { profileLoginName ->
                    navController.navigate("profile/$profileLoginName")
                }
            )
        }

        composable(
            route = "community/{slug}",
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            CommunityDetailScreen(
                slug = slug,
                onNavigateBack = { navController.popBackStack() },
                onPostClick = { postId ->
                    navController.navigate("post/$postId")
                },
                onProfileClick = { loginName ->
                    navController.navigate("profile/$loginName")
                },
                onDrawClick = { communityId, backgroundColor, foregroundColor ->
                    // If community has defined colors, show orientation picker
                    if (backgroundColor != null && foregroundColor != null) {
                        navController.navigate("orientationpicker?communityId=$communityId")
                    } else {
                        var route = "dimensionpicker?communityId=$communityId"
                        if (backgroundColor != null) route += "&backgroundColor=$backgroundColor"
                        if (foregroundColor != null) route += "&foregroundColor=$foregroundColor"
                        navController.navigate(route)
                    }
                },
                onMembersClick = {
                    navController.navigate("community/$slug/members")
                },
                onSettingsClick = {
                    navController.navigate("community/$slug/edit")
                }
            )
        }

        composable(
            route = "community/{slug}/members",
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            val context = androidx.compose.ui.platform.LocalContext.current
            val authService = remember { AuthService.getInstance(context) }
            val currentUser by authService.currentUser.collectAsState()

            // Fetch community detail to determine ownership
            val detailViewModel: cafe.oeee.ui.community.CommunityDetailViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return cafe.oeee.ui.community.CommunityDetailViewModel(slug) as T
                    }
                }
            )
            val detailUiState by detailViewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                detailViewModel.loadCommunity()
            }

            // Determine if user is owner
            val user = currentUser
            val communityDetail = detailUiState.communityDetail
            val isOwner = user != null && communityDetail != null &&
                user.id == communityDetail.community.ownerId

            // Check moderator role from member list
            var isOwnerOrModerator by remember { androidx.compose.runtime.mutableStateOf(isOwner) }

            LaunchedEffect(user, communityDetail) {
                if (user != null && communityDetail != null) {
                    // Start with owner check (covers public/unlisted communities)
                    isOwnerOrModerator = isOwner

                    // For private communities, also check moderator role
                    if (communityDetail.community.visibility == "private") {
                        try {
                            val membersResponse = ApiClient.apiService.getCommunityMembers(slug)
                            val userMember = membersResponse.members.find { it.userId == user.id }
                            isOwnerOrModerator = userMember?.role in listOf("owner", "moderator")
                        } catch (e: Exception) {
                            // If we can't fetch members, fall back to owner check
                            isOwnerOrModerator = isOwner
                        }
                    }
                }
            }

            if (detailUiState.communityDetail != null) {
                CommunityMembersScreen(
                    slug = slug,
                    isOwner = isOwner,
                    isOwnerOrModerator = isOwnerOrModerator,
                    currentUserId = user?.id,
                    onNavigateBack = { navController.popBackStack() },
                    onInviteUser = {
                        navController.navigate("community/$slug/invite")
                    },
                    onLeaveCommunity = {
                        // Pop all the way back to communities list
                        navController.popBackStack("communities", inclusive = false)
                    }
                )
            } else {
                // Show loading state while fetching community info
                Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
        }

        composable(
            route = "community/{slug}/invite",
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            InviteUserScreen(
                slug = slug,
                onNavigateBack = { navController.popBackStack() },
                onInviteSuccess = { navController.popBackStack() }
            )
        }

        composable("community-invitations") {
            CommunityInvitationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onInvitationCountChanged = {
                    // Update invitation count when an invitation is accepted/rejected
                    scope.launch {
                        try {
                            val response = ApiClient.apiService.getUserInvitations()
                            invitationCount.value = response.invitations.size
                        } catch (e: Exception) {
                            // Ignore error
                        }
                    }
                }
            )
        }

        composable(
            route = "community/{slug}/edit",
            arguments = listOf(navArgument("slug") { type = NavType.StringType })
        ) { backStackEntry ->
            val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
            // Fetch community info to pass to EditCommunityScreen
            val editViewModel: cafe.oeee.ui.community.CommunityDetailViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return cafe.oeee.ui.community.CommunityDetailViewModel(slug) as T
                    }
                }
            )
            val editUiState by editViewModel.uiState.collectAsState()

            LaunchedEffect(Unit) {
                editViewModel.loadCommunity()
            }

            if (editUiState.communityDetail != null) {
                val communityInfo = cafe.oeee.data.model.CommunityInfo(
                    id = editUiState.communityDetail!!.community.id,
                    name = editUiState.communityDetail!!.community.name,
                    slug = editUiState.communityDetail!!.community.slug,
                    description = editUiState.communityDetail!!.community.description,
                    visibility = editUiState.communityDetail!!.community.visibility,
                    ownerId = editUiState.communityDetail!!.community.ownerId,
                    backgroundColor = editUiState.communityDetail!!.community.backgroundColor,
                    foregroundColor = editUiState.communityDetail!!.community.foregroundColor
                )

                cafe.oeee.ui.community.EditCommunityScreen(
                    slug = slug,
                    communityInfo = communityInfo,
                    onNavigateBack = {
                        // Navigate to communities list after deletion
                        navController.navigate("communities") {
                            popUpTo("communities") { inclusive = false }
                        }
                    },
                    onCommunityUpdated = { navController.popBackStack() }
                )
            } else {
                // Show loading state while fetching community info
                Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
        }
        }
    }
}
