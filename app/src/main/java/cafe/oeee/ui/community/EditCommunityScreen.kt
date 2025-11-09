package cafe.oeee.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.oeee.data.model.CommunityInfo
import cafe.oeee.data.model.UpdateCommunityRequest
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCommunityScreen(
    slug: String,
    communityInfo: CommunityInfo,
    onNavigateBack: () -> Unit,
    onCommunityUpdated: () -> Unit,
    viewModel: EditCommunityViewModel = viewModel(
        factory = EditCommunityViewModelFactory(slug, communityInfo)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var confirmationInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState.updateSucceeded) {
        if (uiState.updateSucceeded) {
            onCommunityUpdated()
        }
    }

    LaunchedEffect(uiState.deleteSucceeded) {
        if (uiState.deleteSucceeded) {
            onNavigateBack()
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                confirmationInput = ""
            },
            title = { Text(stringResource(R.string.community_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.community_delete_warning))
                    OutlinedTextField(
                        value = confirmationInput,
                        onValueChange = { confirmationInput = it },
                        label = { Text(stringResource(R.string.community_delete_confirm_prompt, slug)) },
                        placeholder = { Text(slug) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCommunity()
                        showDeleteDialog = false
                        confirmationInput = ""
                    },
                    enabled = confirmationInput == slug && !uiState.isDeleting,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.community_delete_button))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        confirmationInput = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.community_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.updateCommunity() },
                        enabled = uiState.isValid && !uiState.isUpdating
                    ) {
                        if (uiState.isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.community_save))
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Info Section
            Text(
                text = stringResource(R.string.community_basic_info),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text(stringResource(R.string.community_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Show slug as read-only
            OutlinedTextField(
                value = uiState.slug,
                onValueChange = { },
                label = { Text(stringResource(R.string.community_id)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                prefix = { Text(stringResource(R.string.prefix_at_symbol)) },
                supportingText = {
                    Text(stringResource(R.string.community_id_cannot_change))
                }
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text(stringResource(R.string.community_description)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )

            // Privacy Section
            Text(
                text = stringResource(R.string.community_privacy),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.allowedVisibilities.contains("public")) {
                    VisibilityOption(
                        title = stringResource(R.string.community_visibility_public),
                        description = stringResource(R.string.community_visibility_public_desc),
                        selected = uiState.visibility == "public",
                        onClick = { viewModel.updateVisibility("public") },
                        enabled = true
                    )
                }

                if (uiState.allowedVisibilities.contains("unlisted")) {
                    VisibilityOption(
                        title = stringResource(R.string.community_visibility_unlisted),
                        description = stringResource(R.string.community_visibility_unlisted_desc),
                        selected = uiState.visibility == "unlisted",
                        onClick = { viewModel.updateVisibility("unlisted") },
                        enabled = true
                    )
                }

                if (uiState.allowedVisibilities.contains("private")) {
                    VisibilityOption(
                        title = stringResource(R.string.community_visibility_private),
                        description = stringResource(R.string.community_visibility_private_desc),
                        selected = uiState.visibility == "private",
                        onClick = { viewModel.updateVisibility("private") },
                        enabled = true
                    )
                }
            }

            if (uiState.isPrivate) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.community_visibility_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Danger Zone
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.community_danger_zone),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    Text(
                        text = stringResource(R.string.community_delete_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.community_delete_button))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilityOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (selected) {
            CardDefaults.outlinedCardBorder()
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class EditCommunityUiState(
    val name: String = "",
    val slug: String = "",
    val description: String = "",
    val visibility: String = "public",
    val originalVisibility: String = "public",
    val allowedVisibilities: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val isValid: Boolean = false,
    val isUpdating: Boolean = false,
    val isDeleting: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String? = null,
    val updateSucceeded: Boolean = false,
    val deleteSucceeded: Boolean = false
)

class EditCommunityViewModel(
    private val slug: String,
    communityInfo: CommunityInfo,
    private val apiClient: ApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditCommunityUiState(
            name = communityInfo.name,
            slug = communityInfo.slug,
            description = communityInfo.description ?: "",
            visibility = communityInfo.visibility,
            originalVisibility = communityInfo.visibility,
            allowedVisibilities = if (communityInfo.visibility == "private") {
                listOf("private")
            } else {
                listOf("public", "unlisted")
            },
            isPrivate = communityInfo.visibility == "private",
            isValid = true
        )
    )
    val uiState: StateFlow<EditCommunityUiState> = _uiState

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
        validateForm()
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
        validateForm()
    }

    fun updateVisibility(visibility: String) {
        _uiState.value = _uiState.value.copy(visibility = visibility)
        validateForm()
    }

    private fun validateForm() {
        val state = _uiState.value
        val isValid = state.name.trim().isNotEmpty()
        _uiState.value = state.copy(isValid = isValid)
    }

    fun updateCommunity() {
        val state = _uiState.value
        if (!state.isValid || state.isUpdating) return

        _uiState.value = state.copy(isUpdating = true)

        viewModelScope.launch {
            try {
                val request = UpdateCommunityRequest(
                    name = state.name.trim(),
                    description = state.description.trim(),
                    visibility = state.visibility
                )
                apiClient.apiService.updateCommunity(slug, request)
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    updateSucceeded = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdating = false,
                    showError = true,
                    errorMessage = e.message ?: "Failed to update community"
                )
            }
        }
    }

    fun deleteCommunity() {
        val state = _uiState.value
        if (state.isDeleting) return

        _uiState.value = state.copy(isDeleting = true)

        viewModelScope.launch {
            try {
                apiClient.apiService.deleteCommunity(slug)
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    deleteSucceeded = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    showError = true,
                    errorMessage = e.message ?: "Failed to delete community"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(showError = false, errorMessage = null)
    }
}

class EditCommunityViewModelFactory(
    private val slug: String,
    private val communityInfo: CommunityInfo
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditCommunityViewModel::class.java)) {
            return EditCommunityViewModel(slug, communityInfo, ApiClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
