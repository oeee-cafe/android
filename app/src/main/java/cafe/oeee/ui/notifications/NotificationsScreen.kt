package cafe.oeee.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.R
import cafe.oeee.ui.components.ErrorState
import cafe.oeee.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onPostClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    onInvitationsClick: () -> Unit = {},
    invitationCountFlow: kotlinx.coroutines.flow.MutableStateFlow<Int>? = null,
    unreadCountFlow: kotlinx.coroutines.flow.MutableStateFlow<Long>? = null
) {
    val context = LocalContext.current
    val viewModel: NotificationsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return NotificationsViewModel(context) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Detect when we reach the end for pagination
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index >= totalItems - 3 && uiState.hasMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    // Load initial data
    LaunchedEffect(Unit) {
        // Clear notification badge when viewing notifications screen
        try {
            val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
            notificationManager?.cancelAll()
            android.util.Log.d("NotificationsScreen", "Cleared notification badge")
        } catch (e: Exception) {
            android.util.Log.w("NotificationsScreen", "Failed to clear notification badge", e)
        }

        viewModel.loadInitial()
    }

    // Refresh invitation count when screen reappears
    DisposableEffect(Unit) {
        onDispose {
            // Refresh count when leaving this screen so it's fresh when we come back
            viewModel.updateInvitationCount()
        }
    }

    // Auto-mark all notifications as read when viewing the notifications screen
    var hasAutoMarkedRead by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.unreadCount, uiState.isLoading) {
        if (!hasAutoMarkedRead && !uiState.isLoading && uiState.unreadCount > 0) {
            android.util.Log.d("NotificationsScreen", "Auto-marking ${uiState.unreadCount} unread notifications as read")
            viewModel.markAllAsRead()
            hasAutoMarkedRead = true
        }
    }

    // Update invitation count in parent flow (only after data has been loaded)
    LaunchedEffect(uiState.invitationCount, uiState.hasLoadedData) {
        if (uiState.hasLoadedData) {
            invitationCountFlow?.value = uiState.invitationCount
        }
    }

    // Update unread count in parent flow (only after data has been loaded)
    LaunchedEffect(uiState.unreadCount, uiState.hasLoadedData) {
        if (uiState.hasLoadedData) {
            unreadCountFlow?.value = uiState.unreadCount
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Community Invitations button with badge
                    IconButton(onClick = onInvitationsClick) {
                        BadgedBox(
                            badge = {
                                if (uiState.invitationCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = stringResource(R.string.notifications_invitations)
                            )
                        }
                    }

                    // Mark all read button
                    if (uiState.notifications.isNotEmpty()) {
                        IconButton(onClick = { viewModel.markAllAsRead() }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.notifications_mark_all_read)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            when {
                uiState.isLoading && uiState.notifications.isEmpty() -> {
                    LoadingState()
                }
                uiState.error != null && uiState.notifications.isEmpty() -> {
                    ErrorState(
                        error = uiState.error ?: stringResource(R.string.unknown_error),
                        onRetry = { viewModel.loadInitial() }
                    )
                }
                uiState.notifications.isEmpty() -> {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.notifications_empty),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.notifications_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 0.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.notifications,
                            key = { it.id }
                        ) { notification ->
                            NotificationCard(
                                notification = notification,
                                onNotificationClick = {
                                    // Mark as read when clicked
                                    if (!notification.isRead) {
                                        viewModel.markAsRead(notification)
                                    }

                                    // Navigate based on notification type
                                    when {
                                        notification.postId != null -> {
                                            onPostClick(notification.postId)
                                        }
                                        notification.notificationType == cafe.oeee.data.model.notification.NotificationType.FOLLOW &&
                                        notification.actorLoginName != null -> {
                                            // Navigate to follower's profile (local users only)
                                            onProfileClick(notification.actorLoginName)
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                        }

                        // Loading more indicator
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
