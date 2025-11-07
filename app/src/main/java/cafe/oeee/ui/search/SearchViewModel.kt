package cafe.oeee.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.search.SearchPostResult
import cafe.oeee.data.model.search.SearchUserResult
import cafe.oeee.data.service.SearchService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val searchText: String = "",
    val users: List<SearchUserResult> = emptyList(),
    val posts: List<SearchPostResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(context: Context) : ViewModel() {
    private val searchService = SearchService.getInstance(context)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun updateSearchText(text: String) {
        _uiState.value = _uiState.value.copy(searchText = text)

        // Cancel previous search job
        searchJob?.cancel()

        if (text.isBlank()) {
            // Clear results if search text is empty
            _uiState.value = _uiState.value.copy(
                users = emptyList(),
                posts = emptyList(),
                error = null
            )
            return
        }

        // Debounce search
        searchJob = viewModelScope.launch {
            delay(300) // Wait 300ms before searching
            performSearch(text)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        val result = searchService.search(query, limit = 20)

        result.fold(
            onSuccess = { response ->
                _uiState.value = _uiState.value.copy(
                    users = response.users,
                    posts = response.posts,
                    isLoading = false,
                    error = null
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Search failed"
                )
            }
        )
    }
}
