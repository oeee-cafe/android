package cafe.oeee.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.R
import cafe.oeee.data.service.AuthService
import cafe.oeee.ui.components.CommunityCard
import cafe.oeee.ui.components.ErrorState
import cafe.oeee.ui.components.LoadingState
import cafe.oeee.ui.components.PostGridItem
import cafe.oeee.ui.components.RecentCommentCard

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onPostClick: (String) -> Unit = {},
    onCommunityClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    onLoginClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDrawClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val authService = AuthService.getInstance(context)
    val isAuthenticated by authService.isAuthenticated.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    // Pagination logic - detect when we reach the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index >= totalItems - 6 && uiState.hasMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
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
                    // Draw button - only for authenticated users
                    if (isAuthenticated) {
                        IconButton(onClick = onDrawClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Draw"
                            )
                        }
                    }
                    if (!isAuthenticated) {
                        IconButton(onClick = onLoginClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = stringResource(R.string.login_button)
                            )
                        }
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.search_title)
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
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
            isRefreshing = uiState.isLoading && uiState.posts.isNotEmpty(),
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            when {
                uiState.isLoading && uiState.posts.isEmpty() -> {
                    LoadingState()
                }
                uiState.error != null && uiState.posts.isEmpty() -> {
                    ErrorState(
                        error = uiState.error ?: stringResource(R.string.unknown_error),
                        onRetry = { viewModel.loadInitial() }
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Active Communities Section
                        if (uiState.communities.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.active_communities),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            items(
                                items = uiState.communities,
                                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }
                            ) { community ->
                                CommunityCard(
                                    community = community,
                                    onCommunityClick = { onCommunityClick(community.slug) },
                                    onPostClick = onPostClick,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // Recent Comments Section
                        if (uiState.comments.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.recent_comments),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            items(
                                items = uiState.comments,
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

                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // Recent Posts Section
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Text(
                                text = stringResource(R.string.recent_posts),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        // Posts Grid
                        items(uiState.posts) { post ->
                            PostGridItem(
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
