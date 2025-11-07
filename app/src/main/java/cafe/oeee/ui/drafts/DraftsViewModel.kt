package cafe.oeee.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.remote.model.DraftPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftsUiState(
    val drafts: List<DraftPost> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DraftsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DraftsUiState())
    val uiState: StateFlow<DraftsUiState> = _uiState.asStateFlow()

    fun loadDrafts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = ApiClient.apiService.getDraftPosts()
                _uiState.update {
                    it.copy(
                        drafts = response.drafts,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load drafts"
                    )
                }
            }
        }
    }

    fun refresh() {
        loadDrafts()
    }
}
