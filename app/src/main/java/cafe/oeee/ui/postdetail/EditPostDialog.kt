package cafe.oeee.ui.postdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cafe.oeee.R
import cafe.oeee.data.model.EditPostRequest
import cafe.oeee.data.remote.ApiService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPostDialog(
    postId: String,
    initialTitle: String,
    initialContent: String,
    initialHashtags: String,
    initialIsSensitive: Boolean,
    initialAllowRelay: Boolean,
    apiService: ApiService,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    var hashtags by remember { mutableStateOf(initialHashtags) }
    var isSensitive by remember { mutableStateOf(initialIsSensitive) }
    var allowRelay by remember { mutableStateOf(initialAllowRelay) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isSubmitting,
            dismissOnClickOutside = !isSubmitting,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.post_edit_post),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.common_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(stringResource(R.string.drafts_description_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        enabled = !isSubmitting,
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hashtags
                    OutlinedTextField(
                        value = hashtags,
                        onValueChange = { hashtags = it },
                        label = { Text(stringResource(R.string.post_hashtags_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sensitive Content Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.drafts_sensitive_content))
                        Switch(
                            checked = isSensitive,
                            onCheckedChange = { isSensitive = it },
                            enabled = !isSubmitting
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Allow Relay Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.drafts_allow_relay))
                        Switch(
                            checked = allowRelay,
                            onCheckedChange = { allowRelay = it },
                            enabled = !isSubmitting
                        )
                    }

                    // Error message
                    errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMessage = "Title is required"
                                return@Button
                            }

                            isSubmitting = true
                            errorMessage = null

                            coroutineScope.launch {
                                try {
                                    val request = EditPostRequest(
                                        title = title,
                                        content = content,
                                        hashtags = if (hashtags.isBlank()) null else hashtags,
                                        isSensitive = isSensitive,
                                        allowRelay = allowRelay
                                    )
                                    apiService.editPost(postId, request)
                                    onSuccess()
                                    onDismiss()
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Failed to edit post"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        enabled = !isSubmitting && title.isNotBlank()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }
}
