package cafe.oeee.ui.postdetail

import android.Manifest
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.R
import cafe.oeee.data.model.ChildPost
import cafe.oeee.data.model.Comment
import cafe.oeee.data.model.PostDetail
import cafe.oeee.data.model.ReactionCount
import cafe.oeee.data.model.reaction.Reactor
import cafe.oeee.ui.components.ErrorState
import cafe.oeee.ui.components.LoadingState
import cafe.oeee.util.ImageSaver
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    currentUserId: String? = null,
    currentUserLoginName: String? = null,
    onNavigateBack: () -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    onCommunityClick: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    onReplyClick: (String?, String?, String?) -> Unit = { _, _, _ -> },
    onReplayClick: () -> Unit = {}
) {
    val viewModel: PostDetailViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PostDetailViewModel(postId) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showCommentDeleteConfirmation by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var isSavingImage by remember { mutableStateOf(false) }
    var showSaveSuccessSnackbar by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingImageUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Permission launcher for storage access (Android 9 and below)
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with saving
            pendingImageUrl?.let { url ->
                isSavingImage = true
                coroutineScope.launch {
                    ImageSaver.saveImage(
                        context = context,
                        imageUrl = url,
                        onSuccess = {
                            isSavingImage = false
                            showSaveSuccessSnackbar = true
                        },
                        onError = { error ->
                            isSavingImage = false
                            saveErrorMessage = error
                        }
                    )
                }
            }
            pendingImageUrl = null
        } else {
            // Permission denied
            isSavingImage = false
            saveErrorMessage = context.getString(R.string.post_image_permission_denied)
            pendingImageUrl = null
        }
    }

    // Navigate back when post is deleted
    LaunchedEffect(uiState.postDeleted) {
        if (uiState.postDeleted) {
            onNavigateBack()
        }
    }

    // Show save success snackbar
    LaunchedEffect(showSaveSuccessSnackbar) {
        if (showSaveSuccessSnackbar) {
            snackbarHostState.showSnackbar(context.getString(R.string.post_image_saved))
            showSaveSuccessSnackbar = false
        }
    }

    // Show save error snackbar
    LaunchedEffect(saveErrorMessage) {
        saveErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            saveErrorMessage = null
        }
    }

    // Show post delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.post_delete_title)) },
            text = { Text(stringResource(R.string.post_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deletePost()
                    }
                ) {
                    Text(stringResource(R.string.post_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Show comment delete confirmation dialog
    if (showCommentDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showCommentDeleteConfirmation = false
                commentToDelete = null
            },
            title = { Text(stringResource(R.string.comment_delete_title)) },
            text = { Text(stringResource(R.string.comment_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        commentToDelete?.let { comment ->
                            viewModel.deleteComment(comment)
                        }
                        showCommentDeleteConfirmation = false
                        commentToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.comment_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCommentDeleteConfirmation = false
                    commentToDelete = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Show move post dialog
    if (showMoveDialog) {
        MovePostDialog(
            postId = postId,
            apiService = cafe.oeee.data.remote.ApiClient.apiService,
            onDismiss = { showMoveDialog = false },
            onSuccess = {
                // Refresh post after successful move
                viewModel.loadPostDetail()
                showMoveDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.post_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Share button - visible to all users
                    if (uiState.post != null) {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            val post = uiState.post!!
                            val slug = post.community?.slug ?: post.author.loginName
                            val shareUrl = "https://oeee.cafe/@${slug}/${post.id}"
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

                    // Save Image button - visible to all users
                    if (uiState.post != null) {
                        IconButton(
                            onClick = {
                                val post = uiState.post!!
                                val imageUrl = post.image.url

                                // Check if we need permission (API 28 and below)
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                    if (ImageSaver.hasStoragePermission(context)) {
                                        // Permission already granted
                                        isSavingImage = true
                                        coroutineScope.launch {
                                            ImageSaver.saveImage(
                                                context = context,
                                                imageUrl = imageUrl,
                                                onSuccess = {
                                                    isSavingImage = false
                                                    showSaveSuccessSnackbar = true
                                                },
                                                onError = { error ->
                                                    isSavingImage = false
                                                    saveErrorMessage = error
                                                }
                                            )
                                        }
                                    } else {
                                        // Request permission
                                        pendingImageUrl = imageUrl
                                        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                } else {
                                    // API 29+, no permission needed
                                    isSavingImage = true
                                    coroutineScope.launch {
                                        ImageSaver.saveImage(
                                            context = context,
                                            imageUrl = imageUrl,
                                            onSuccess = {
                                                isSavingImage = false
                                                showSaveSuccessSnackbar = true
                                            },
                                            onError = { error ->
                                                isSavingImage = false
                                                saveErrorMessage = error
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = !isSavingImage
                        ) {
                            if (isSavingImage) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = stringResource(R.string.post_save_image)
                                )
                            }
                        }
                    }

                    // Replay button - hide for neo-cucumber posts
                    if (uiState.post != null && uiState.post?.image?.tool != "neo-cucumber") {
                        IconButton(onClick = onReplayClick) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = stringResource(R.string.replay_title)
                            )
                        }
                    }

                    // Reply button - visible to all users
                    if (uiState.post != null) {
                        IconButton(onClick = {
                            onReplyClick(
                                uiState.post?.community?.backgroundColor,
                                uiState.post?.community?.foregroundColor,
                                uiState.post?.community?.id
                            )
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = stringResource(R.string.post_reply)
                            )
                        }
                    }

                    // Show move button only if current user is the author
                    if (currentUserId != null && uiState.post?.author?.id == currentUserId) {
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = stringResource(R.string.post_move_to_community)
                            )
                        }
                    }

                    // Show delete button only if current user is the author
                    if (currentUserId != null && uiState.post?.author?.id == currentUserId) {
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !uiState.isDeleting
                        ) {
                            if (uiState.isDeleting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.post_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
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
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.post == null -> {
                    LoadingState()
                }
                uiState.error != null && uiState.post == null -> {
                    ErrorState(
                        error = uiState.error ?: stringResource(R.string.unknown_error),
                        onRetry = { viewModel.loadPostDetail() }
                    )
                }
                uiState.post != null -> {
                    PostDetailContent(
                        post = uiState.post!!,
                        parentPost = uiState.parentPost,
                        comments = uiState.comments,
                        childPosts = uiState.childPosts,
                        reactions = uiState.reactions,
                        commentText = uiState.commentText,
                        replyingToComment = uiState.replyingToComment,
                        isPostingComment = uiState.isPostingComment,
                        isAuthenticated = currentUserId != null,
                        currentUserLoginName = currentUserLoginName,
                        onProfileClick = onProfileClick,
                        onCommunityClick = onCommunityClick,
                        onPostClick = onPostClick,
                        onReactionClick = { emoji ->
                            if (currentUserId != null) {
                                viewModel.toggleReaction(emoji)
                            }
                        },
                        onReactionLongPress = { emoji ->
                            viewModel.loadReactors(emoji)
                        },
                        onCommentTextChange = { viewModel.updateCommentText(it) },
                        onReplyClick = { comment -> viewModel.setReplyTarget(comment) },
                        onCancelReply = { viewModel.cancelReply() },
                        onPostComment = { viewModel.postComment() },
                        onCommentDelete = { comment ->
                            commentToDelete = comment
                            showCommentDeleteConfirmation = true
                        }
                    )

                    // Show reactors dialog
                    val selectedEmoji = uiState.selectedReactionEmoji
                    if (selectedEmoji != null) {
                        ReactorsDialog(
                            emoji = selectedEmoji,
                            reactors = uiState.reactors,
                            isLoading = uiState.isLoadingReactors,
                            onDismiss = { viewModel.clearReactors() },
                            onProfileClick = onProfileClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostDetailContent(
    post: PostDetail,
    parentPost: ChildPost?,
    comments: List<Comment>,
    childPosts: List<ChildPost>,
    reactions: List<ReactionCount>,
    commentText: String,
    replyingToComment: Comment?,
    isPostingComment: Boolean,
    isAuthenticated: Boolean = true,
    currentUserLoginName: String? = null,
    onProfileClick: (String) -> Unit,
    onCommunityClick: (String) -> Unit,
    onPostClick: (String) -> Unit,
    onReactionClick: (String) -> Unit,
    onReactionLongPress: (String) -> Unit,
    onCommentTextChange: (String) -> Unit,
    onReplyClick: (Comment) -> Unit,
    onCancelReply: () -> Unit,
    onPostComment: () -> Unit,
    onCommentDelete: (Comment) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Parent Post (Replying To)
        if (parentPost != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.post_replying_to),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    ChildPostCard(
                        childPost = parentPost,
                        modifier = Modifier,
                        onPostClick = onPostClick
                    )
                }
            }
        }

        // Image
        item {
            AsyncImage(
                model = post.image.url,
                contentDescription = post.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        // Details
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                if (!post.title.isNullOrEmpty()) {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Content
                if (!post.content.isNullOrEmpty()) {
                    Text(
                        text = post.content,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Hashtags
                if (post.hashtags.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(post.hashtags.size) { index ->
                            androidx.compose.material3.AssistChip(
                                onClick = { },
                                label = { Text("#${post.hashtags[index]}") },
                                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Author
                OutlinedButton(
                    onClick = { onProfileClick(post.author.loginName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(post.author.loginName)
                }

                // Community (only show if post has community)
                post.community?.let { community ->
                    OutlinedButton(
                        onClick = { onCommunityClick(community.slug) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_view),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(community.name)
                    }
                }

                HorizontalDivider()

                // Stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_recent_history),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = post.image.paintDuration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_view),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${post.viewerCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Published date
                post.publishedAtUtc?.let { date ->
                    Text(
                        text = stringResource(R.string.post_published, formatRelativeTime(context, date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Reactions
                if (reactions.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        reactions.forEach { reaction ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (reaction.reactedByUser) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            if (isAuthenticated) {
                                                onReactionClick(reaction.emoji)
                                            }
                                        },
                                        onLongClick = {
                                            if (reaction.count > 0) {
                                                onReactionLongPress(reaction.emoji)
                                            }
                                        }
                                    )
                                    .alpha(if (!isAuthenticated && reaction.count == 0) 0.5f else 1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = reaction.emoji,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (reaction.count > 0) {
                                        Text(
                                            text = "${reaction.count}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (reaction.reactedByUser) FontWeight.Bold else FontWeight.Normal,
                                            color = if (reaction.reactedByUser) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Child Posts (Replies)
        if (childPosts.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.post_replies, childPosts.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(childPosts) { childPost ->
                ChildPostCard(
                    childPost = childPost,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onPostClick = onPostClick
                )
            }
        }

        // Comments
        if (comments.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.post_comments, comments.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(comments) { comment ->
                CommentCard(
                    comment = comment,
                    currentUserLoginName = currentUserLoginName,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onReply = onReplyClick,
                    onDelete = onCommentDelete,
                    onProfileClick = onProfileClick
                )
            }
        }

        // Comment Input Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider()

                // Reply indicator
                replyingToComment?.let { replyingTo ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.post_replying_to, replyingTo.actorName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = onCancelReply,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.comment_cancel_reply),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Comment input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = onCommentTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.comment_write_placeholder)) },
                        minLines = 2,
                        maxLines = 6
                    )

                    IconButton(
                        onClick = onPostComment,
                        enabled = commentText.trim().isNotEmpty() && !isPostingComment,
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (isPostingComment) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.comment_post),
                                tint = if (commentText.trim().isEmpty()) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChildPostCard(
    childPost: ChildPost,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    onPostClick: (String) -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onPostClick(childPost.id) }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = childPost.image.url,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Crop
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = childPost.author.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (!childPost.title.isNullOrEmpty()) {
                        Text(
                            text = childPost.title,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (!childPost.content.isNullOrEmpty()) {
                        Text(
                            text = childPost.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // Render children with increased depth
        if (childPost.children.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                childPost.children.forEach { child ->
                    ChildPostCard(
                        childPost = child,
                        modifier = Modifier,
                        depth = depth + 1,
                        onPostClick = onPostClick
                    )
                }
            }
        }
    }
}

@Composable
fun CommentCard(
    comment: Comment,
    currentUserLoginName: String? = null,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    onReply: ((Comment) -> Unit)? = null,
    onDelete: ((Comment) -> Unit)? = null,
    onProfileClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isOwnComment = currentUserLoginName != null &&
                       comment.isLocal &&
                       comment.actorLoginName == currentUserLoginName
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main comment
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (comment.isLocal && comment.actorLoginName != null) {
                        Modifier.clickable {
                            onProfileClick?.invoke(comment.actorLoginName)
                        }
                    } else {
                        Modifier
                    }
                ) {
                    Text(
                        text = comment.actorName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = comment.actorHandle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = comment.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (comment.isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontStyle = if (comment.isDeleted) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                )

                Text(
                    text = formatRelativeTime(context, comment.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Action buttons (hidden for deleted comments)
                if (!comment.isDeleted) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Reply button
                        onReply?.let { onReplyCallback ->
                            TextButton(onClick = { onReplyCallback(comment) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = stringResource(R.string.post_reply),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.post_reply), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Delete button (only for own comments)
                        if (isOwnComment) {
                            onDelete?.let { onDeleteCallback ->
                                TextButton(onClick = { onDeleteCallback(comment) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.comment_delete),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.comment_delete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Nested replies
        if (comment.children.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                comment.children.forEach { childComment ->
                    ThreadedCommentCard(
                        comment = childComment,
                        currentUserLoginName = currentUserLoginName,
                        depth = depth + 1,
                        onReply = onReply,
                        onDelete = onDelete,
                        onProfileClick = onProfileClick
                    )
                }
            }
        }
    }
}

@Composable
fun ThreadedCommentCard(
    comment: Comment,
    currentUserLoginName: String? = null,
    depth: Int = 0,
    onReply: ((Comment) -> Unit)? = null,
    onDelete: ((Comment) -> Unit)? = null,
    onProfileClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isOwnComment = currentUserLoginName != null &&
                       comment.isLocal &&
                       comment.actorLoginName == currentUserLoginName
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main comment with thread indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thread line indicator
            Surface(
                modifier = Modifier
                    .width(2.dp)
                    .height(IntrinsicSize.Min),
                color = MaterialTheme.colorScheme.outlineVariant
            ) {
                Spacer(modifier = Modifier.fillMaxHeight())
            }

            // Comment content
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (comment.isLocal && comment.actorLoginName != null) {
                            Modifier.clickable {
                                onProfileClick?.invoke(comment.actorLoginName)
                            }
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = comment.actorName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = comment.actorHandle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = comment.displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (comment.isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontStyle = if (comment.isDeleted) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                    )

                    Text(
                        text = formatRelativeTime(context, comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Action buttons (hidden for deleted comments)
                    if (!comment.isDeleted) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Reply button
                            onReply?.let { onReplyCallback ->
                                TextButton(
                                    onClick = { onReplyCallback(comment) },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = stringResource(R.string.post_reply),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.post_reply), style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // Delete button (only for own comments)
                            if (isOwnComment) {
                                onDelete?.let { onDeleteCallback ->
                                    TextButton(
                                        onClick = { onDeleteCallback(comment) },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.comment_delete),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.comment_delete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Nested replies
        if (comment.children.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                comment.children.forEach { childComment ->
                    ThreadedCommentCard(
                        comment = childComment,
                        currentUserLoginName = currentUserLoginName,
                        depth = depth + 1,
                        onReply = onReply,
                        onDelete = onDelete,
                        onProfileClick = onProfileClick
                    )
                }
            }
        }
    }
}

@Composable
fun ReactorsDialog(
    emoji: String,
    reactors: List<Reactor>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${reactors.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    reactors.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.reactors_none),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(reactors) { reactor ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    onClick = {
                                        onDismiss()
                                        onProfileClick(reactor.actorHandle)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = reactor.actorName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = reactor.actorHandle,
                                            style = MaterialTheme.typography.bodyMedium,
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
}

private fun formatRelativeTime(context: android.content.Context, date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30
    val years = days / 365

    return when {
        years > 0 -> context.getString(R.string.time_years_ago, years)
        months > 0 -> context.getString(R.string.time_months_ago, months)
        weeks > 0 -> context.getString(R.string.time_weeks_ago, weeks)
        days > 0 -> context.getString(R.string.time_days_ago, days)
        hours > 0 -> context.getString(R.string.time_hours_ago, hours)
        minutes > 0 -> context.getString(R.string.time_minutes_ago, minutes)
        seconds > 5 -> context.getString(R.string.time_seconds_ago, seconds)
        else -> context.getString(R.string.time_just_now)
    }
}
