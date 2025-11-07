package cafe.oeee.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.ActiveCommunity
import cafe.oeee.data.model.Post
import cafe.oeee.data.model.RecentComment
import cafe.oeee.data.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val posts: List<Post> = emptyList(),
    val communities: List<ActiveCommunity> = emptyList(),
    val comments: List<RecentComment> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val currentOffset: Int = 0
)

class HomeViewModel : ViewModel() {
    private val repository = HomeRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadInitial()
    }

    fun loadInitial() {
        // Don't reload if we already have data
        if (_uiState.value.isLoading || _uiState.value.posts.isNotEmpty()) {
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Fetch all data concurrently
                val postsResult = repository.getPublicPosts(offset = 0)
                val communitiesResult = repository.getActiveCommunities()
                val commentsResult = repository.getLatestComments()

                var errorMessage: String? = null

                postsResult.fold(
                    onSuccess = { postsResponse ->
                        _uiState.update {
                            it.copy(
                                posts = postsResponse.posts,
                                hasMore = postsResponse.pagination.hasMore,
                                currentOffset = postsResponse.pagination.offset
                            )
                        }
                    },
                    onFailure = { error ->
                        errorMessage = error.message ?: "Failed to load posts"
                    }
                )

                communitiesResult.fold(
                    onSuccess = { communitiesResponse ->
                        _uiState.update { it.copy(communities = communitiesResponse.communities) }
                    },
                    onFailure = { /* Optional: communities are not critical */ }
                )

                commentsResult.fold(
                    onSuccess = { commentsResponse ->
                        _uiState.update { it.copy(comments = commentsResponse.comments) }
                    },
                    onFailure = { /* Optional: comments are not critical */ }
                )

                _uiState.update { it.copy(isLoading = false, error = errorMessage) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || currentState.isLoading || !currentState.hasMore) {
            return
        }

        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            repository.getPublicPosts(offset = currentState.currentOffset).fold(
                onSuccess = { postsResponse ->
                    _uiState.update {
                        it.copy(
                            posts = it.posts + postsResponse.posts,
                            hasMore = postsResponse.pagination.hasMore,
                            currentOffset = postsResponse.pagination.offset,
                            isLoadingMore = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            error = error.message ?: "Failed to load more posts"
                        )
                    }
                }
            )
        }
    }

    fun refresh() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore) {
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, currentOffset = 0) }

        viewModelScope.launch {
            try {
                // Fetch all data concurrently
                val postsResult = repository.getPublicPosts(offset = 0)
                val communitiesResult = repository.getActiveCommunities()
                val commentsResult = repository.getLatestComments()

                var errorMessage: String? = null

                postsResult.fold(
                    onSuccess = { postsResponse ->
                        _uiState.update {
                            it.copy(
                                posts = postsResponse.posts,
                                hasMore = postsResponse.pagination.hasMore,
                                currentOffset = postsResponse.pagination.offset
                            )
                        }
                    },
                    onFailure = { error ->
                        errorMessage = error.message ?: "Failed to load posts"
                    }
                )

                communitiesResult.fold(
                    onSuccess = { communitiesResponse ->
                        _uiState.update { it.copy(communities = communitiesResponse.communities) }
                    },
                    onFailure = { /* Optional: communities are not critical */ }
                )

                commentsResult.fold(
                    onSuccess = { commentsResponse ->
                        _uiState.update { it.copy(comments = commentsResponse.comments) }
                    },
                    onFailure = { /* Optional: comments are not critical */ }
                )

                _uiState.update { it.copy(isLoading = false, error = errorMessage) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }
}
