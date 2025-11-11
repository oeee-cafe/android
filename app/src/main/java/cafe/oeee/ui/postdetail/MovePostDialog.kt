package cafe.oeee.ui.postdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import cafe.oeee.R
import cafe.oeee.data.model.MovableCommunity
import cafe.oeee.data.model.MovableCommunitiesResponse
import cafe.oeee.data.model.MoveCommunityRequest
import cafe.oeee.data.remote.ApiService
import kotlinx.coroutines.launch

@Composable
fun MovePostDialog(
    postId: String,
    apiService: ApiService,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var communities by remember { mutableStateOf<List<MovableCommunity>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isMoving by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Computed properties for filtering and grouping
    val filteredCommunities = remember(communities, searchQuery) {
        if (searchQuery.isBlank()) {
            communities
        } else {
            communities.filter { community ->
                community.name.contains(searchQuery, ignoreCase = true) ||
                (community.slug?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    val personalPostOption = remember(filteredCommunities) {
        filteredCommunities.firstOrNull { it.isPersonalPost }
    }

    val unlistedCommunities = remember(filteredCommunities) {
        filteredCommunities
            .filter { it.visibility == "unlisted" }
            .sortedBy { it.name.lowercase() }
    }

    val publicParticipatedCommunities = remember(filteredCommunities) {
        filteredCommunities
            .filter { it.visibility == "public" && it.hasParticipated == true }
            .sortedBy { it.name.lowercase() }
    }

    val publicOtherCommunities = remember(filteredCommunities) {
        filteredCommunities
            .filter { it.visibility == "public" && it.hasParticipated == false }
            .sortedBy { it.name.lowercase() }
    }

    LaunchedEffect(postId) {
        isLoading = true
        errorMessage = null
        try {
            val response = apiService.getMovableCommunities(postId)
            communities = response.communities
        } catch (e: Exception) {
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = { if (!isMoving) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isMoving,
            dismissOnClickOutside = !isMoving
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
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
                        text = stringResource(R.string.post_move_to_community),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isMoving
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.post_move_loading))
                            }
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.post_move_error),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = errorMessage ?: stringResource(R.string.common_error),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    communities.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.post_move_cannot_move_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.post_move_cannot_move_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Search field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                placeholder = { Text(stringResource(R.string.move_search_placeholder)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true
                            )

                            // Communities list with sections
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Personal Post Option
                                personalPostOption?.let { community ->
                                    item {
                                        CommunityItem(
                                            community = community,
                                            enabled = !isMoving,
                                            onClick = {
                                                coroutineScope.launch {
                                                    isMoving = true
                                                    try {
                                                        apiService.movePostToCommunity(
                                                            postId,
                                                            MoveCommunityRequest(community.id)
                                                        )
                                                        onSuccess()
                                                        onDismiss()
                                                    } catch (e: Exception) {
                                                        errorMessage = e.message
                                                    } finally {
                                                        isMoving = false
                                                    }
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                // Unlisted Communities Section
                                if (unlistedCommunities.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.move_section_unlisted),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    items(unlistedCommunities) { community ->
                                        CommunityItem(
                                            community = community,
                                            enabled = !isMoving,
                                            onClick = {
                                                coroutineScope.launch {
                                                    isMoving = true
                                                    try {
                                                        apiService.movePostToCommunity(
                                                            postId,
                                                            MoveCommunityRequest(community.id)
                                                        )
                                                        onSuccess()
                                                        onDismiss()
                                                    } catch (e: Exception) {
                                                        errorMessage = e.message
                                                    } finally {
                                                        isMoving = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                // Public Communities (Participated) Section
                                if (publicParticipatedCommunities.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.move_section_public_participated),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    items(publicParticipatedCommunities) { community ->
                                        CommunityItem(
                                            community = community,
                                            enabled = !isMoving,
                                            onClick = {
                                                coroutineScope.launch {
                                                    isMoving = true
                                                    try {
                                                        apiService.movePostToCommunity(
                                                            postId,
                                                            MoveCommunityRequest(community.id)
                                                        )
                                                        onSuccess()
                                                        onDismiss()
                                                    } catch (e: Exception) {
                                                        errorMessage = e.message
                                                    } finally {
                                                        isMoving = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                // Public Communities (Other) Section
                                if (publicOtherCommunities.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.move_section_public_other),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    items(publicOtherCommunities) { community ->
                                        CommunityItem(
                                            community = community,
                                            enabled = !isMoving,
                                            onClick = {
                                                coroutineScope.launch {
                                                    isMoving = true
                                                    try {
                                                        apiService.movePostToCommunity(
                                                            postId,
                                                            MoveCommunityRequest(community.id)
                                                        )
                                                        onSuccess()
                                                        onDismiss()
                                                    } catch (e: Exception) {
                                                        errorMessage = e.message
                                                    } finally {
                                                        isMoving = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Loading overlay
                if (isMoving) {
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

@Composable
private fun CommunityItem(
    community: MovableCommunity,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Community icon
        if (community.isPersonalPost) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            val backgroundColor = community.backgroundColor?.let { hex ->
                try {
                    Color(("#$hex").toColorInt())
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.primaryContainer
                }
            } ?: MaterialTheme.colorScheme.primaryContainer

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = community.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (community.isPersonalPost) stringResource(R.string.post_move_personal_post) else community.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            if (!community.isPersonalPost && community.ownerDisplayName != null && community.ownerLoginName != null) {
                Text(
                    text = stringResource(R.string.common_by, "${community.ownerDisplayName} (@${community.ownerLoginName})"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            community.visibility?.let { visibility ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (visibility) {
                            "public" -> Icons.Default.Public
                            "unlisted" -> Icons.Outlined.Link
                            "private" -> Icons.Default.Lock
                            else -> Icons.Default.Public
                        },
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = visibility.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
