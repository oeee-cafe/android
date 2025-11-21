package cafe.oeee.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.R
import cafe.oeee.data.model.ProfileDetail
import cafe.oeee.data.model.ProfileFollowing
import cafe.oeee.data.model.ProfileLink
import cafe.oeee.data.model.ProfilePost
import cafe.oeee.ui.components.ErrorState
import cafe.oeee.ui.components.LoadingState
import cafe.oeee.ui.components.PostGridItem
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    loginName: String,
    currentUserLoginName: String? = null,
    onNavigateBack: () -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    onFollowingListClick: (String) -> Unit = {}
) {
    val viewModel: ProfileViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ProfileViewModel(loginName) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    var showReportDialog by remember { mutableStateOf(false) }
    var reportDescription by remember { mutableStateOf("") }

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
                title = { Text(uiState.profileDetail?.user?.displayName ?: loginName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Show follow/unfollow button and more menu if user is authenticated and viewing another user's profile
                    val isAuthenticated = currentUserLoginName != null
                    val isOtherUser = currentUserLoginName != loginName
                    if (isAuthenticated && isOtherUser && uiState.profileDetail != null) {
                        val isFollowing = uiState.profileDetail?.user?.isFollowing ?: false
                        FilledTonalButton(
                            onClick = { viewModel.toggleFollow() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (isFollowing)
                                    stringResource(R.string.profile_unfollow)
                                else
                                    stringResource(R.string.profile_follow)
                            )
                        }

                        IconButton(onClick = { showReportDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = stringResource(R.string.profile_report),
                                tint = MaterialTheme.colorScheme.error
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
            isRefreshing = uiState.isLoading && uiState.profileDetail != null,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            when {
                uiState.isLoading && uiState.profileDetail == null -> {
                    LoadingState()
                }
                uiState.error != null && uiState.profileDetail == null -> {
                    ErrorState(
                        error = uiState.error ?: stringResource(R.string.unknown_error),
                        onRetry = { viewModel.loadProfile() }
                    )
                }
                uiState.profileDetail != null -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Banner
                        uiState.profileDetail!!.banner?.let { banner ->
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                AsyncImage(
                                    model = banner.imageUrl,
                                    contentDescription = stringResource(R.string.profile_banner),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                )
                            }
                        }

                        // Profile Header
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = uiState.profileDetail!!.user.displayName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${uiState.profileDetail!!.user.loginName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Links Section
                        if (uiState.profileDetail!!.links.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                LinksSection(
                                    links = uiState.profileDetail!!.links,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }

                        // Followings Section
                        if (uiState.profileDetail!!.followings.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                FollowingsSection(
                                    followings = uiState.profileDetail!!.followings,
                                    totalFollowings = uiState.profileDetail!!.totalFollowings,
                                    onProfileClick = onProfileClick,
                                    onSeeAllClick = { onFollowingListClick(uiState.profileDetail!!.user.loginName) },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }

                        // Posts Section Title
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Text(
                                text = stringResource(R.string.profile_posts),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        // Posts Grid
                        items(uiState.posts) { post ->
                            ProfilePostItem(
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

    // Show report profile dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = {
                showReportDialog = false
                reportDescription = ""
            },
            title = { Text(stringResource(R.string.profile_report_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_report_description_label))
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        placeholder = { Text(stringResource(R.string.profile_report_description_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (reportDescription.isNotBlank()) {
                            viewModel.reportProfile(reportDescription)
                            showReportDialog = false
                            reportDescription = ""
                        }
                    },
                    enabled = reportDescription.isNotBlank()
                ) {
                    Text(stringResource(R.string.profile_report_submit))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReportDialog = false
                    reportDescription = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Show report result dialog
    uiState.reportResult?.let { result ->
        val message = when (result) {
            is cafe.oeee.ui.profile.ProfileReportResult.Success -> stringResource(R.string.profile_report_success)
            is cafe.oeee.ui.profile.ProfileReportResult.Error -> result.message
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearReportResult() },
            title = { Text(stringResource(R.string.profile_report)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearReportResult() }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            }
        )
    }
}

@Composable
fun LinksSection(
    links: List<ProfileLink>,
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
                text = stringResource(R.string.profile_links),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            links.forEach { link ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = link.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                    link.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FollowingsSection(
    followings: List<ProfileFollowing>,
    totalFollowings: Int,
    onProfileClick: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (followings.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_followings, totalFollowings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Grid of users with banners
        val usersWithBanners = followings.filter { it.bannerImageUrl != null }
        if (usersWithBanners.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                usersWithBanners.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { following ->
                            AsyncImage(
                                model = following.bannerImageUrl,
                                contentDescription = stringResource(R.string.profile_user_banner, following.displayName),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(
                                        if (following.bannerImageWidth != null && following.bannerImageHeight != null) {
                                            following.bannerImageWidth.toFloat() / following.bannerImageHeight.toFloat()
                                        } else {
                                            16f / 9f
                                        }
                                    )
                                    .clickable { onProfileClick(following.loginName) }
                            )
                        }
                        // Add spacer for odd number of items in the last row
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // See All button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clickable(onClick = onSeeAllClick),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.profile_see_all_following),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProfilePostItem(
    post: ProfilePost,
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
