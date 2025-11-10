package cafe.oeee

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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

        // Manual edge-to-edge setup to avoid deprecated APIs
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Configure display cutout mode (non-deprecated API)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        // Configure system bars appearance
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            // Make system bars transparent
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Initialize ApiClient with persistent cookie store
        ApiClient.initialize(this)

        // Check auth status on app launch
        val authService = AuthService.getInstance(this)
        if (authService.hasStoredAuthState()) {
            lifecycleScope.launch {
                authService.checkAuthStatus()
            }
        }

        setContent {
            OeeeCafeTheme {
                AppNavigation(pushNotificationPermissionLauncher)
            }
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
    val unreadCount = MutableStateFlow(0L)
    val draftCount = MutableStateFlow(0)

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
                    val response = ApiClient.apiService.getDraftPosts()
                    draftCount.value = response.drafts.size
                } catch (e: Exception) {
                    // Ignore error
                }
            }
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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

                        val count by unreadCount.collectAsState()
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
                                        if (count > 0) {
                                            Badge {
                                                Text(count.toString())
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
                }
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "dimensionpicker?parentPostId={parentPostId}&communityId={communityId}",
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
                        popUpTo("dimensionpicker?parentPostId={parentPostId}&communityId={communityId}") { inclusive = true }
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
                    navController.navigate("draftpost/$postId/$communityIdParam/$encodedImageUrl") {
                        popUpTo("draw/{width}/{height}/{tool}?parentPostId={parentPostId}&communityId={communityId}") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "draftpost/{postId}/{communityId}/{imageUrl}",
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType },
                navArgument("communityId") { type = NavType.StringType },
                navArgument("imageUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            val communityIdParam = backStackEntry.arguments?.getString("communityId") ?: return@composable
            // Convert "none" placeholder or empty string to null for personal posts
            val communityId = if (communityIdParam.isEmpty() || communityIdParam == "none") null else communityIdParam
            val encodedImageUrl = backStackEntry.arguments?.getString("imageUrl") ?: return@composable
            val imageUrl = java.net.URLDecoder.decode(encodedImageUrl, "UTF-8")
            cafe.oeee.ui.draftpost.DraftPostScreen(
                postId = postId,
                communityId = communityId,
                imageUrl = imageUrl,
                onNavigateBack = { navController.popBackStack() },
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
                onReplyClick = {
                    navController.navigate("dimensionpicker?parentPostId=$postId")
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
                onDrawClick = { communityId ->
                    navController.navigate("dimensionpicker?communityId=$communityId")
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
                    onNavigateBack = { navController.popBackStack() },
                    onInviteUser = {
                        navController.navigate("community/$slug/invite")
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
                onNavigateBack = { navController.popBackStack() }
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
