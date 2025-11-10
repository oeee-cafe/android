package cafe.oeee.ui.communities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.R
import cafe.oeee.data.service.AuthService
import cafe.oeee.ui.components.CommunityCard
import cafe.oeee.ui.components.ErrorState
import cafe.oeee.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
    viewModel: CommunitiesViewModel = viewModel(),
    onCommunityClick: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onCreateCommunityClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val authService = AuthService.getInstance(context)
    val isAuthenticated by authService.isAuthenticated.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Refresh communities when returning from other screens
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.communities_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (isAuthenticated) {
                        IconButton(onClick = onCreateCommunityClick) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.create_community)
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
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            if (uiState.isLoading && uiState.myCommunities.isEmpty() && uiState.publicCommunities.isEmpty()) {
                LoadingState(
                    message = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.error != null && uiState.myCommunities.isEmpty() && uiState.publicCommunities.isEmpty()) {
                ErrorState(
                    error = uiState.error ?: stringResource(R.string.communities_error),
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val listState = rememberLazyListState()

                // Detect when user scrolls near the bottom (only for browse mode, not search)
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                        val totalItems = listState.layoutInfo.totalItemsCount

                        // Load more when user is within 3 items of the bottom
                        // Only load more in browse mode (empty search query)
                        lastVisibleItem != null &&
                        lastVisibleItem.index >= totalItems - 3 &&
                        uiState.hasMore &&
                        !uiState.isLoadingMore &&
                        uiState.searchQuery.isEmpty()
                    }
                }

                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) {
                        viewModel.loadMorePublicCommunities()
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 0.dp)
                ) {
                    // Search box
                    item(key = "search_box") {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = {
                                Text(stringResource(R.string.community_search_placeholder))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search)
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.clear)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { /* Do nothing - filtering is live */ }),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            shape = MaterialTheme.shapes.medium
                        )
                    }

                    // Private Communities Section
                    if (isAuthenticated && uiState.filteredPrivateCommunities.isNotEmpty()) {
                        item(key = "private_communities_header") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.private_communities),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        items(
                            items = uiState.filteredPrivateCommunities,
                            key = { "private_${it.id}" }
                        ) { community ->
                            CommunityCard(
                                community = community,
                                onCommunityClick = { onCommunityClick(community.slug) },
                                onPostClick = onPostClick,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Unlisted Communities Section
                    if (isAuthenticated && uiState.filteredUnlistedCommunities.isNotEmpty()) {
                        item(key = "unlisted_communities_header") {
                            Spacer(modifier = Modifier.height(if (isAuthenticated && uiState.filteredPrivateCommunities.isNotEmpty()) 24.dp else 16.dp))
                            Text(
                                text = stringResource(R.string.unlisted_communities),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        items(
                            items = uiState.filteredUnlistedCommunities,
                            key = { "unlisted_${it.id}" }
                        ) { community ->
                            CommunityCard(
                                community = community,
                                onCommunityClick = { onCommunityClick(community.slug) },
                                onPostClick = onPostClick,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // My Public Communities Section
                    if (isAuthenticated && uiState.filteredPublicMyCommunities.isNotEmpty()) {
                        item(key = "my_public_communities_header") {
                            val hasAnySectionAbove = uiState.filteredPrivateCommunities.isNotEmpty() || uiState.filteredUnlistedCommunities.isNotEmpty()
                            Spacer(modifier = Modifier.height(if (isAuthenticated && hasAnySectionAbove) 24.dp else 16.dp))
                            Text(
                                text = stringResource(R.string.my_public_communities),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        items(
                            items = uiState.filteredPublicMyCommunities,
                            key = { "my_public_${it.id}" }
                        ) { community ->
                            CommunityCard(
                                community = community,
                                onCommunityClick = { onCommunityClick(community.slug) },
                                onPostClick = onPostClick,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Other Public Communities Section
                    val allPublicCommunities = if (uiState.searchQuery.isNotEmpty()) {
                        uiState.searchResults
                    } else {
                        uiState.filteredPublicCommunities
                    }
                    // Filter out communities that are already in My Communities to avoid duplicates
                    val myCommunityIds = uiState.filteredMyCommunities.map { it.id }.toSet()
                    val publicCommunities = allPublicCommunities.filter { it.id !in myCommunityIds }

                    if (publicCommunities.isNotEmpty()) {
                        item(key = "public_communities_header") {
                            val hasAnySectionAbove = isAuthenticated && (
                                uiState.filteredPrivateCommunities.isNotEmpty() ||
                                uiState.filteredUnlistedCommunities.isNotEmpty() ||
                                uiState.filteredPublicMyCommunities.isNotEmpty()
                            )
                            Spacer(modifier = Modifier.height(if (hasAnySectionAbove) 24.dp else 16.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) {
                                    stringResource(R.string.search_results)
                                } else {
                                    stringResource(R.string.other_public_communities)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        items(
                            items = publicCommunities,
                            key = { "public_${it.id}" }
                        ) { community ->
                            CommunityCard(
                                community = community,
                                onCommunityClick = { onCommunityClick(community.slug) },
                                onPostClick = onPostClick,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Loading more or searching indicator
                        if (uiState.isLoadingMore || uiState.isSearching) {
                            item(key = "loading_more") {
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

                    // Empty state
                    val showEmptyState = if (uiState.searchQuery.isNotEmpty()) {
                        uiState.filteredMyCommunities.isEmpty() && uiState.searchResults.isEmpty() && !uiState.isSearching
                    } else {
                        uiState.filteredMyCommunities.isEmpty() && uiState.filteredPublicCommunities.isEmpty() && !uiState.isLoading
                    }

                    if (showEmptyState) {
                        item(key = "empty_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        modifier = Modifier.padding(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (uiState.searchQuery.isEmpty()) {
                                            stringResource(R.string.no_communities)
                                        } else {
                                            stringResource(R.string.no_search_results)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
