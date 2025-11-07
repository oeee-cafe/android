package cafe.oeee.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.ProfileDetail
import cafe.oeee.data.model.ProfilePost
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profileDetail: ProfileDetail? = null,
    val posts: List<ProfilePost> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val currentOffset: Int = 0
)

class ProfileViewModel(private val loginName: String) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val apiService = ApiClient.apiService

    init {
        loadProfile()
    }

    fun loadProfile() {
        if (_uiState.value.isLoading || _uiState.value.profileDetail != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.getProfileDetail(loginName, offset = 0, limit = 18)
                _uiState.value = _uiState.value.copy(
                    profileDetail = response,
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
                val response = apiService.getProfileDetail(
                    loginName,
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
                val response = apiService.getProfileDetail(loginName, offset = 0, limit = 18)
                _uiState.value = _uiState.value.copy(
                    profileDetail = response,
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

    fun toggleFollow() {
        val currentProfile = _uiState.value.profileDetail ?: return

        viewModelScope.launch {
            try {
                if (currentProfile.user.isFollowing) {
                    apiService.unfollowProfile(loginName)
                } else {
                    apiService.followProfile(loginName)
                }

                // Update local state optimistically
                val updatedUser = currentProfile.user.copy(
                    isFollowing = !currentProfile.user.isFollowing
                )
                val updatedProfile = currentProfile.copy(user = updatedUser)
                _uiState.value = _uiState.value.copy(profileDetail = updatedProfile)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to toggle follow"
                )
            }
        }
    }
}
