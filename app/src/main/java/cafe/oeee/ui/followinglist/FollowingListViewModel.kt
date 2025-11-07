package cafe.oeee.ui.followinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.ProfileFollowing
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FollowingListUiState(
    val followings: List<ProfileFollowing> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false
)

class FollowingListViewModel(private val loginName: String) : ViewModel() {
    private val _uiState = MutableStateFlow(FollowingListUiState())
    val uiState: StateFlow<FollowingListUiState> = _uiState.asStateFlow()

    private var currentOffset = 0
    private val limit = 50

    init {
        loadFollowings()
    }

    fun loadFollowings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = ApiClient.apiService.getProfileFollowings(
                    loginName = loginName,
                    offset = 0,
                    limit = limit
                )

                currentOffset = response.pagination.offset
                _uiState.value = _uiState.value.copy(
                    followings = response.followings,
                    isLoading = false,
                    hasMore = response.pagination.hasMore,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun loadMoreFollowings() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            try {
                val response = ApiClient.apiService.getProfileFollowings(
                    loginName = loginName,
                    offset = currentOffset,
                    limit = limit
                )

                currentOffset = response.pagination.offset
                _uiState.value = _uiState.value.copy(
                    followings = _uiState.value.followings + response.followings,
                    isLoadingMore = false,
                    hasMore = response.pagination.hasMore
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun refresh() {
        currentOffset = 0
        loadFollowings()
    }
}
