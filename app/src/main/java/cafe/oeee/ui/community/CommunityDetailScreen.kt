package cafe.oeee.ui.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.R
import cafe.oeee.data.model.CommunityDetail
import cafe.oeee.data.model.CommunityDetailPost
import cafe.oeee.data.model.RecentComment
import cafe.oeee.data.service.AuthService
import cafe.oeee.ui.components.ErrorState
import cafe.oeee.ui.components.LoadingState
import cafe.oeee.ui.components.RecentCommentCard
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
    slug: String,
    onNavigateBack: () -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    onDrawClick: (String) -> Unit = {},
    onMembersClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authService = remember { AuthService.getInstance(context) }
    val currentUser by authService.currentUser.collectAsState()
    val isAuthenticated by authService.isAuthenticated.collectAsState()
    val viewModel: CommunityDetailViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CommunityDetailViewModel(slug) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    // Refresh community when returning from other screens (like edit)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (uiState.communityDetail != null) {
                    viewModel.refreshCommunity()
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // Pagination logic
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index >= totalItems - 6 && uiState.hasMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMorePosts()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.communityDetail?.community?.name ?: "Community") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Members button - visible to authenticated users in private communities
                    if (uiState.communityDetail != null && isAuthenticated) {
                        val isPrivate = uiState.communityDetail!!.community.visibility == "private"
                        if (isPrivate) {
                            IconButton(onClick = onMembersClick) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = stringResource(R.string.community_members)
                                )
                            }
                        }
                    }

                    // Settings button - visible to owner only
                    if (uiState.communityDetail != null && currentUser != null) {
                        val isOwner = currentUser!!.id == uiState.communityDetail!!.community.ownerId
                        if (isOwner) {
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings_title)
                                )
                            }
                        }
                    }

                    // Share button - visible for public communities only
                    if (uiState.communityDetail != null) {
                        val isPrivate = uiState.communityDetail!!.community.visibility == "private"
                        if (!isPrivate) {
                            IconButton(onClick = {
                                val community = uiState.communityDetail!!.community
                                val shareUrl = "https://oeee.cafe/communities/@${community.slug}"
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                                    type = "text/plain"
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(shareIntent, null)
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.post_share)
                                )
                            }
                        }
                    }

                    // New post button
                    if (uiState.communityDetail != null) {
                        IconButton(onClick = { onDrawClick(uiState.communityDetail!!.community.id) }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.new_post)
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
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.communityDetail != null,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.communityDetail == null -> {
                    LoadingState()
                }
                uiState.error != null && uiState.communityDetail == null -> {
                    ErrorState(
                        error = uiState.error ?: stringResource(R.string.unknown_error),
                        onRetry = { viewModel.loadCommunity() }
                    )
                }
                uiState.communityDetail != null -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Community Header
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            CommunityHeader(
                                detail = uiState.communityDetail!!,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        // Stats Card
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            StatsCard(
                                detail = uiState.communityDetail!!,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        // Recent Comments
                        if (uiState.communityDetail!!.comments.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Text(
                                    text = stringResource(R.string.recent_comments),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            items(
                                items = uiState.communityDetail!!.comments,
                                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }
                            ) { comment ->
                                RecentCommentCard(
                                    comment = comment,
                                    onPostClick = { onPostClick(comment.postId) },
                                    onProfileClick = if (comment.isLocal && comment.actorLoginName != null) {
                                        { onProfileClick(comment.actorLoginName) }
                                    } else null,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }

                        // Recent Posts
                        if (uiState.posts.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Text(
                                    text = stringResource(R.string.recent_posts),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            items(uiState.posts) { post ->
                                CommunityPostItem(
                                    post = post,
                                    onClick = { onPostClick(post.id) }
                                )
                            }

                            // Loading indicator for pagination
                            if (uiState.isLoadingMore) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
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
}

@Composable
fun CommunityHeader(
    detail: CommunityDetail,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = detail.community.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "@${detail.community.slug}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        detail.community.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatsCard(
    detail: CommunityDetail,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.community_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.community_stat_posts),
                    value = detail.stats.totalPosts.toString()
                )
                StatItem(
                    label = stringResource(R.string.community_stat_contributors),
                    value = detail.stats.totalContributors.toString()
                )
                StatItem(
                    label = stringResource(R.string.community_stat_comments),
                    value = detail.stats.totalComments.toString()
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CommunityPostItem(
    post: CommunityDetailPost,
    onClick: () -> Unit
) {
    AsyncImage(
        model = post.imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    )
}
