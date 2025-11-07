package cafe.oeee.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.CommunityDetail
import cafe.oeee.data.model.CommunityDetailPost
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommunityDetailUiState(
    val communityDetail: CommunityDetail? = null,
    val posts: List<CommunityDetailPost> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val currentOffset: Int = 0
)

class CommunityDetailViewModel(private val slug: String) : ViewModel() {
    private val _uiState = MutableStateFlow(CommunityDetailUiState())
    val uiState: StateFlow<CommunityDetailUiState> = _uiState.asStateFlow()

    private val apiService = ApiClient.apiService

    init {
        loadCommunity()
    }

    fun loadCommunity() {
        if (_uiState.value.isLoading || _uiState.value.communityDetail != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.getCommunityDetail(slug, offset = 0, limit = 18)
                _uiState.value = _uiState.value.copy(
                    communityDetail = response,
                    posts = response.posts,
                    currentOffset = response.pagination.offset,
                    hasMore = response.pagination.hasMore,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun refreshCommunity() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.getCommunityDetail(slug, offset = 0, limit = 18)
                _uiState.value = _uiState.value.copy(
                    communityDetail = response,
                    posts = response.posts,
                    currentOffset = response.pagination.offset,
                    hasMore = response.pagination.hasMore,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun loadMorePosts() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            try {
                val response = apiService.getCommunityDetail(
                    slug,
                    offset = _uiState.value.currentOffset,
                    limit = 18
                )
                _uiState.value = _uiState.value.copy(
                    posts = _uiState.value.posts + response.posts,
                    currentOffset = response.pagination.offset,
                    hasMore = response.pagination.hasMore,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentOffset = 0)

            try {
                val response = apiService.getCommunityDetail(slug, offset = 0, limit = 18)
                _uiState.value = _uiState.value.copy(
                    communityDetail = response,
                    posts = response.posts,
                    currentOffset = response.pagination.offset,
                    hasMore = response.pagination.hasMore,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
}
