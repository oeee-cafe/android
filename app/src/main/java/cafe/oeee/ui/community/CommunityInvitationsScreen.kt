package cafe.oeee.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.data.model.UserInvitation
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityInvitationsScreen(
    onNavigateBack: () -> Unit,
    onInvitationCountChanged: () -> Unit = {},
    viewModel: CommunityInvitationsViewModel = viewModel(
        factory = CommunityInvitationsViewModelFactory()
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadInvitations()
    }

    // Update invitation count when invitations list changes
    LaunchedEffect(uiState.invitations.size) {
        if (uiState.invitations.isNotEmpty() || !uiState.isLoading) {
            onInvitationCountChanged()
        }
    }

    if (uiState.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text(stringResource(R.string.dialog_error)) },
            text = { Text(uiState.errorMessage ?: stringResource(R.string.dialog_error_unknown)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.community_invitations_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            when {
                uiState.isLoading && uiState.invitations.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.invitations.isEmpty() -> {
                    EmptyInvitationsState(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.invitations) { invitation ->
                            InvitationCard(
                                invitation = invitation,
                                onAccept = { viewModel.acceptInvitation(invitation.id) },
                                onReject = { viewModel.rejectInvitation(invitation.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInvitationsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MailOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.community_invitations_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.community_invitations_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InvitationCard(
    invitation: UserInvitation,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Community Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invitation.community.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "@${invitation.community.slug}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                VisibilityBadge(visibility = invitation.community.visibility)
            }

            if (invitation.community.description.isNotEmpty()) {
                Text(
                    text = invitation.community.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            HorizontalDivider()

            // Inviter Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.community_invitation_invited_by),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = invitation.inviter.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "@${invitation.inviter.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.community_invitation_decline))
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.community_invitation_accept))
                }
            }
        }
    }
}

@Composable
private fun VisibilityBadge(visibility: String) {
    val iconAndColor: Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (visibility) {
        "public" -> Pair(Icons.Default.Public, MaterialTheme.colorScheme.primary)
        "unlisted" -> Pair(Icons.Default.Link, MaterialTheme.colorScheme.tertiary)
        "private" -> Pair(Icons.Default.Lock, MaterialTheme.colorScheme.secondary)
        else -> Pair(Icons.Default.HelpOutline, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val icon = iconAndColor.first
    val color = iconAndColor.second

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Text(
                text = visibility.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

data class CommunityInvitationsUiState(
    val invitations: List<UserInvitation> = emptyList(),
    val isLoading: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String? = null
)

class CommunityInvitationsViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(CommunityInvitationsUiState())
    val uiState: StateFlow<CommunityInvitationsUiState> = _uiState

    fun loadInvitations() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            try {
                val response = apiClient.apiService.getUserInvitations()
                _uiState.value = _uiState.value.copy(
                    invitations = response.invitations,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showError = true,
                    errorMessage = e.message ?: "Failed to load invitations"
                )
            }
        }
    }

    fun acceptInvitation(id: String) {
        viewModelScope.launch {
            try {
                apiClient.apiService.acceptInvitation(id)
                loadInvitations()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showError = true,
                    errorMessage = e.message ?: "Failed to accept invitation"
                )
            }
        }
    }

    fun rejectInvitation(id: String) {
        viewModelScope.launch {
            try {
                apiClient.apiService.rejectInvitation(id)
                loadInvitations()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showError = true,
                    errorMessage = e.message ?: "Failed to reject invitation"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(showError = false, errorMessage = null)
    }
}

class CommunityInvitationsViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CommunityInvitationsViewModel::class.java)) {
            return CommunityInvitationsViewModel(ApiClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
