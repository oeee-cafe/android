package cafe.oeee.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.data.model.InviteUserRequest
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteUserScreen(
    slug: String,
    onNavigateBack: () -> Unit,
    onInviteSuccess: () -> Unit,
    viewModel: InviteUserViewModel = viewModel(
        factory = InviteUserViewModelFactory(slug)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.inviteSucceeded) {
        if (uiState.inviteSucceeded) {
            onInviteSuccess()
        }
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
                title = { Text(stringResource(R.string.community_invite_user_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.inviteUser() },
                        enabled = uiState.username.isNotBlank() && !uiState.isInviting
                    ) {
                        if (uiState.isInviting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.community_invite_user_invite))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.community_invite_user_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = { viewModel.updateUsername(it) },
                label = { Text(stringResource(R.string.community_invite_user_username)) },
                placeholder = { Text(stringResource(R.string.community_invite_user_username_hint)) },
                prefix = { Text(stringResource(R.string.prefix_at_symbol)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isInviting
            )
        }
    }
}

data class InviteUserUiState(
    val username: String = "",
    val isInviting: Boolean = false,
    val inviteSucceeded: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String? = null
)

class InviteUserViewModel(
    private val slug: String,
    private val apiClient: ApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(InviteUserUiState())
    val uiState: StateFlow<InviteUserUiState> = _uiState

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun inviteUser() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInviting = true)
            try {
                val request = InviteUserRequest(loginName = _uiState.value.username)
                apiClient.apiService.inviteUser(slug, request)
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    inviteSucceeded = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInviting = false,
                    showError = true,
                    errorMessage = e.message ?: "Failed to invite user"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(showError = false, errorMessage = null)
    }
}

class InviteUserViewModelFactory(
    private val slug: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InviteUserViewModel::class.java)) {
            return InviteUserViewModel(slug, ApiClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
