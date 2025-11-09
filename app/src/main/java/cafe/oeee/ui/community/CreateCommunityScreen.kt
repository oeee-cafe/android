package cafe.oeee.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import cafe.oeee.data.model.CreateCommunityRequest
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityScreen(
    onNavigateBack: () -> Unit,
    onCommunityCreated: (String) -> Unit,
    viewModel: CreateCommunityViewModel = viewModel(
        factory = CreateCommunityViewModelFactory()
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdSlug) {
        uiState.createdSlug?.let { slug ->
            onCommunityCreated(slug)
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
                title = { Text(stringResource(R.string.community_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.createCommunity() },
                        enabled = uiState.isValid && !uiState.isCreating
                    ) {
                        if (uiState.isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.community_create))
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                )
            )

            OutlinedTextField(
                value = uiState.slug,
                onValueChange = { viewModel.updateSlug(it) },
                label = { Text(stringResource(R.string.community_id)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (uiState.slugError != null) {
                        Text(uiState.slugError!!)
                    } else {
                        Text(stringResource(R.string.community_id_hint))
                    }
                },
                isError = uiState.slugError != null,
                prefix = { Text(stringResource(R.string.prefix_at_symbol)) }
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
                VisibilityOption(
                    title = stringResource(R.string.community_visibility_public),
                    description = stringResource(R.string.community_visibility_public_desc),
                    selected = uiState.visibility == "public",
                    onClick = { viewModel.updateVisibility("public") }
                )

                VisibilityOption(
                    title = stringResource(R.string.community_visibility_unlisted),
                    description = stringResource(R.string.community_visibility_unlisted_desc),
                    selected = uiState.visibility == "unlisted",
                    onClick = { viewModel.updateVisibility("unlisted") }
                )

                VisibilityOption(
                    title = stringResource(R.string.community_visibility_private),
                    description = stringResource(R.string.community_visibility_private_desc),
                    selected = uiState.visibility == "private",
                    onClick = { viewModel.updateVisibility("private") }
                )
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
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
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

data class CreateCommunityUiState(
    val name: String = "",
    val slug: String = "",
    val description: String = "",
    val visibility: String = "public",
    val slugError: String? = null,
    val isValid: Boolean = false,
    val isCreating: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String? = null,
    val createdSlug: String? = null
)

class CreateCommunityViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateCommunityUiState())
    val uiState: StateFlow<CreateCommunityUiState> = _uiState

    private val slugRegex = Regex("^[a-zA-Z0-9_-]*$")

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
        validateForm()
    }

    fun updateSlug(slug: String) {
        val newSlug = slug.lowercase()
        val error = when {
            newSlug.isEmpty() -> null
            !slugRegex.matches(newSlug) -> "Only letters, numbers, hyphens, and underscores allowed"
            newSlug.length < 2 -> "ID must be at least 2 characters"
            newSlug.length > 50 -> "ID must be at most 50 characters"
            else -> null
        }
        _uiState.value = _uiState.value.copy(slug = newSlug, slugError = error)
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
        val isValid = state.name.trim().isNotEmpty() &&
                state.slug.trim().isNotEmpty() &&
                state.slugError == null &&
                state.slug.length >= 2
        _uiState.value = state.copy(isValid = isValid)
    }

    fun createCommunity() {
        val state = _uiState.value
        if (!state.isValid || state.isCreating) return

        _uiState.value = state.copy(isCreating = true)

        viewModelScope.launch {
            try {
                val request = CreateCommunityRequest(
                    name = state.name.trim(),
                    slug = state.slug.trim(),
                    description = state.description.trim(),
                    visibility = state.visibility
                )
                val response = apiClient.apiService.createCommunity(request)
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdSlug = response.community.slug
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    showError = true,
                    errorMessage = e.message ?: "Failed to create community"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(showError = false, errorMessage = null)
    }
}

class CreateCommunityViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateCommunityViewModel::class.java)) {
            return CreateCommunityViewModel(ApiClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
