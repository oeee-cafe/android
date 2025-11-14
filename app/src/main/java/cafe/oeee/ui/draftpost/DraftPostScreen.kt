package cafe.oeee.ui.draftpost

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftPostScreen(
    postId: String,
    communityId: String?,
    imageUrl: String,
    parentPostId: String?,
    onNavigateBack: () -> Unit,
    onDeleted: () -> Unit,
    onPublished: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var hashtags by remember { mutableStateOf("") }
    var isSensitive by remember { mutableStateOf(false) }
    var allowRelay by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.draft_publish_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                publishPost(
                                    postId = postId,
                                    title = title,
                                    content = content,
                                    hashtags = hashtags,
                                    isSensitive = isSensitive,
                                    allowRelay = allowRelay,
                                    parentPostId = parentPostId,
                                    onSuccess = { onPublished(postId) },
                                    onError = { errorMessage = it },
                                    setSubmitting = { isSubmitting = it }
                                )
                            }
                        },
                        enabled = title.isNotBlank() && !isSubmitting
                    ) {
                        Text(stringResource(R.string.draft_publish_button))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image preview
                coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.drawing_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )

                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.draft_title_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true
                )

                // Content field
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.draft_description_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    enabled = !isSubmitting,
                    maxLines = 5
                )

                // Hashtags field
                OutlinedTextField(
                    value = hashtags,
                    onValueChange = { hashtags = it },
                    label = { Text(stringResource(R.string.post_hashtags_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true
                )

                // Sensitive content toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.draft_sensitive_content))
                    Switch(
                        checked = isSensitive,
                        onCheckedChange = { isSensitive = it },
                        enabled = !isSubmitting
                    )
                }

                // Allow relay toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.draft_allow_relay))
                    Switch(
                        checked = allowRelay,
                        onCheckedChange = { allowRelay = it },
                        enabled = !isSubmitting
                    )
                }

                // Error message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Loading overlay
            if (isSubmitting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.draft_publishing))
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text(stringResource(R.string.drafts_delete_confirmation_title)) },
                text = { Text(stringResource(R.string.drafts_delete_confirmation_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            scope.launch {
                                deleteDraft(
                                    postId = postId,
                                    onSuccess = onDeleted,
                                    onError = { errorMessage = it },
                                    setSubmitting = { isSubmitting = it }
                                )
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

private suspend fun publishPost(
    postId: String,
    title: String,
    content: String,
    hashtags: String,
    isSensitive: Boolean,
    allowRelay: Boolean,
    parentPostId: String?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setSubmitting: (Boolean) -> Unit
) {
    setSubmitting(true)

    try {
        withContext(Dispatchers.IO) {
            val formBodyBuilder = FormBody.Builder()
                .add("post_id", postId)
                .add("title", title)
                .add("content", content)

            if (hashtags.isNotBlank()) {
                formBodyBuilder.add("hashtags", hashtags)
            }

            if (isSensitive) {
                formBodyBuilder.add("is_sensitive", "on")
            }
            if (allowRelay) {
                formBodyBuilder.add("allow_relay", "on")
            }

            if (parentPostId != null) {
                formBodyBuilder.add("parent_post_id", parentPostId)
            }

            val request = Request.Builder()
                .url("${ApiClient.getBaseUrl()}/posts/publish")
                .post(formBodyBuilder.build())
                .build()

            // Use the shared API client which has cookies configured
            val response = ApiClient.okHttpClient.newCall(request).execute()

            withContext(Dispatchers.Main) {
                setSubmitting(false)
                if (response.isSuccessful || response.code == 303) {
                    onSuccess()
                } else {
                    onError("Failed to publish (Status ${response.code})")
                }
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            setSubmitting(false)
            onError("Network error: ${e.localizedMessage}")
        }
    }
}

private suspend fun deleteDraft(
    postId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setSubmitting: (Boolean) -> Unit
) {
    setSubmitting(true)

    try {
        withContext(Dispatchers.IO) {
            ApiClient.apiService.deletePost(postId)

            withContext(Dispatchers.Main) {
                setSubmitting(false)
                onSuccess()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            setSubmitting(false)
            onError("Failed to delete: ${e.localizedMessage}")
        }
    }
}
