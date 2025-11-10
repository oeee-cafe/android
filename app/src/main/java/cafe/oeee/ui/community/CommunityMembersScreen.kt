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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.data.model.CommunityInvitation
import cafe.oeee.data.model.CommunityMember
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityMembersScreen(
    slug: String,
    isOwner: Boolean,
    isOwnerOrModerator: Boolean,
    currentUserId: String? = null,
    onNavigateBack: () -> Unit,
    onInviteUser: () -> Unit,
    onLeaveCommunity: () -> Unit = {},
    viewModel: CommunityMembersViewModel = viewModel(
        factory = CommunityMembersViewModelFactory(slug, isOwner, isOwnerOrModerator)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var memberToRemove by remember { mutableStateOf<CommunityMember?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadMembers()
    }

    // Handle successful leave - navigate back
    LaunchedEffect(uiState.leftCommunity) {
        if (uiState.leftCommunity) {
            onLeaveCommunity()
        }
    }

    // Refresh member list when returning from other screens (like invite)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadMembers()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    if (memberToRemove != null) {
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text(stringResource(R.string.community_member_remove_title)) },
            text = { Text(stringResource(R.string.community_member_remove_message, memberToRemove?.displayName ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeMember(memberToRemove!!.userId)
                        memberToRemove = null
                    }
                ) {
                    Text(stringResource(R.string.community_member_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.community_leave_title)) },
            text = { Text(stringResource(R.string.community_leave_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.leaveCommunity()
                        showLeaveDialog = false
                    }
                ) {
                    Text(stringResource(R.string.community_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text(stringResource(R.string.dialog_error)) },
            text = { Text(uiState.errorMessage ?: stringResource(R.string.dialog_error_generic)) },
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
                title = { Text(stringResource(R.string.community_members_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isOwner) {
                        IconButton(onClick = onInviteUser) {
                            Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.community_invite_user_title))
                        }
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
            if (uiState.isLoading && uiState.members.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Members section
                    items(uiState.members) { member ->
                        MemberCard(
                            member = member,
                            canManage = isOwnerOrModerator && member.role != "owner",
                            isCurrentUser = currentUserId != null && member.userId == currentUserId,
                            onRemove = { memberToRemove = member },
                            onLeave = { showLeaveDialog = true }
                        )
                    }

                    // Pending invitations section (only owners can manage invitations)
                    if (isOwner && uiState.invitations.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.community_members_pending_invitations),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(uiState.invitations) { invitation ->
                            InvitationCard(
                                invitation = invitation,
                                onRetract = { viewModel.retractInvitation(invitation.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: CommunityMember,
    canManage: Boolean,
    isCurrentUser: Boolean,
    onRemove: () -> Unit,
    onLeave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "@${member.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleBadge(role = member.role)

                if (canManage) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.community_member_remove),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (isCurrentUser && member.role != "owner") {
                    TextButton(onClick = onLeave) {
                        Text(
                            text = stringResource(R.string.community_leave),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val text = when (role) {
        "owner" -> stringResource(R.string.community_role_owner)
        "moderator" -> stringResource(R.string.community_role_moderator)
        else -> stringResource(R.string.community_role_member)
    }
    val color = when (role) {
        "owner" -> MaterialTheme.colorScheme.primary
        "moderator" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InvitationCard(
    invitation: CommunityInvitation,
    onRetract: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = invitation.invitee.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "@${invitation.invitee.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.community_invitation_invited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onRetract) {
                    Text(stringResource(R.string.community_invitation_retract), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

data class CommunityMembersUiState(
    val members: List<CommunityMember> = emptyList(),
    val invitations: List<CommunityInvitation> = emptyList(),
    val isLoading: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String? = null,
    val leftCommunity: Boolean = false
)

class CommunityMembersViewModel(
    private val slug: String,
    private val isOwner: Boolean,
    private val isOwnerOrModerator: Boolean,
    private val apiClient: ApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(CommunityMembersUiState())
    val uiState: StateFlow<CommunityMembersUiState> = _uiState

    fun loadMembers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val membersResponse = apiClient.apiService.getCommunityMembers(slug)
                val members = membersResponse.members

                // Load invitations for owners (they can see pending invitations)
                val invitations: List<CommunityInvitation> = if (isOwner) {
                    try {
                        apiClient.apiService.getCommunityInvitations(slug).invitations
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                _uiState.value = _uiState.value.copy(
                    members = members,
                    invitations = invitations,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showError = true,
                    errorMessage = e.message ?: "Failed to load members"
                )
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            try {
                apiClient.apiService.removeMember(slug, userId)
                loadMembers()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showError = true,
                    errorMessage = e.message ?: "Failed to remove member"
                )
            }
        }
    }

    fun retractInvitation(invitationId: String) {
        viewModelScope.launch {
            try {
                apiClient.apiService.retractInvitation(slug, invitationId)
                loadMembers()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showError = true,
                    errorMessage = e.message ?: "Failed to retract invitation"
                )
            }
        }
    }

    fun leaveCommunity() {
        viewModelScope.launch {
            try {
                val response = apiClient.apiService.leaveCommunity(slug)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(leftCommunity = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        showError = true,
                        errorMessage = "Failed to leave community"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showError = true,
                    errorMessage = e.message ?: "Failed to leave community"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(showError = false, errorMessage = null)
    }
}

class CommunityMembersViewModelFactory(
    private val slug: String,
    private val isOwner: Boolean,
    private val isOwnerOrModerator: Boolean
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CommunityMembersViewModel::class.java)) {
            return CommunityMembersViewModel(slug, isOwner, isOwnerOrModerator, ApiClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
