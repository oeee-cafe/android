package cafe.oeee.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.ChildPost
import cafe.oeee.data.model.Comment
import cafe.oeee.data.model.CreateCommentRequest
import cafe.oeee.data.model.PostDetail
import cafe.oeee.data.model.ReactionCount
import cafe.oeee.data.model.reaction.ReactionResponse
import cafe.oeee.data.model.reaction.Reactor
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PostDetailUiState(
    val post: PostDetail? = null,
    val parentPost: ChildPost? = null,
    val comments: List<Comment> = emptyList(),
    val childPosts: List<ChildPost> = emptyList(),
    val reactions: List<ReactionCount> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingComments: Boolean = false,
    val commentsHasMore: Boolean = false,
    val error: String? = null,
    val selectedReactionEmoji: String? = null,
    val reactors: List<Reactor> = emptyList(),
    val isLoadingReactors: Boolean = false,
    val commentText: String = "",
    val replyingToComment: Comment? = null,
    val isPostingComment: Boolean = false,
    val isDeleting: Boolean = false,
    val postDeleted: Boolean = false
)

class PostDetailViewModel(private val postId: String) : ViewModel() {
    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private val apiService = ApiClient.apiService
    private var commentsOffset = 0
    private val commentsLimit = 100

    init {
        loadPostDetail()
    }

    fun loadPostDetail() {
        if (_uiState.value.isLoading || _uiState.value.post != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.getPostDetail(postId)
                _uiState.value = _uiState.value.copy(
                    post = response.post,
                    parentPost = response.parentPost,
                    childPosts = response.childPosts,
                    reactions = response.reactions,
                    isLoading = false
                )

                // Load comments separately
                loadComments()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun loadComments() {
        if (_uiState.value.isLoadingComments) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingComments = true)

            try {
                val response = apiService.getPostComments(
                    postId = postId,
                    offset = commentsOffset,
                    limit = commentsLimit
                )

                val updatedComments = if (commentsOffset == 0) {
                    response.comments
                } else {
                    _uiState.value.comments + response.comments
                }

                _uiState.value = _uiState.value.copy(
                    comments = updatedComments,
                    commentsHasMore = response.pagination.hasMore,
                    isLoadingComments = false
                )

                commentsOffset += response.comments.size
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingComments = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun loadMoreComments() {
        if (_uiState.value.commentsHasMore && !_uiState.value.isLoadingComments) {
            loadComments()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            commentsOffset = 0

            try {
                val response = apiService.getPostDetail(postId)
                _uiState.value = _uiState.value.copy(
                    post = response.post,
                    parentPost = response.parentPost,
                    childPosts = response.childPosts,
                    reactions = response.reactions,
                    comments = emptyList(),
                    isLoading = false
                )

                // Reload comments
                loadComments()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun loadReactors(emoji: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingReactors = true,
                selectedReactionEmoji = emoji
            )

            try {
                val response = apiService.getPostReactionsByEmoji(postId, emoji)
                _uiState.value = _uiState.value.copy(
                    reactors = response.reactions,
                    isLoadingReactors = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingReactors = false,
                    reactors = emptyList()
                )
            }
        }
    }

    fun clearReactors() {
        _uiState.value = _uiState.value.copy(
            selectedReactionEmoji = null,
            reactors = emptyList()
        )
    }

    fun updateCommentText(text: String) {
        _uiState.value = _uiState.value.copy(commentText = text)
    }

    fun setReplyTarget(comment: Comment) {
        _uiState.value = _uiState.value.copy(replyingToComment = comment)
    }

    fun cancelReply() {
        _uiState.value = _uiState.value.copy(replyingToComment = null)
    }

    fun postComment() {
        val text = _uiState.value.commentText.trim()
        if (text.isEmpty() || _uiState.value.isPostingComment) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPostingComment = true, error = null)

            try {
                val request = CreateCommentRequest(
                    content = text,
                    parentCommentId = _uiState.value.replyingToComment?.id
                )

                apiService.postComment(postId, request)

                // Clear comment text and reply target
                _uiState.value = _uiState.value.copy(
                    commentText = "",
                    replyingToComment = null,
                    isPostingComment = false
                )

                // Reload comments to show the new comment
                commentsOffset = 0
                _uiState.value = _uiState.value.copy(comments = emptyList())
                loadComments()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPostingComment = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun deletePost() {
        if (_uiState.value.isDeleting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)

            try {
                apiService.deletePost(postId)
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    postDeleted = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun toggleReaction(emoji: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("PostDetailViewModel", "Toggling reaction: $emoji for post: $postId")
                val reaction = _uiState.value.reactions.firstOrNull { it.emoji == emoji }
                android.util.Log.d("PostDetailViewModel", "Current reaction state: reactedByUser=${reaction?.reactedByUser}")

                val response: ReactionResponse = if (reaction?.reactedByUser == true) {
                    // Remove reaction
                    android.util.Log.d("PostDetailViewModel", "Removing reaction")
                    apiService.removeReaction(postId, emoji)
                } else {
                    // Add reaction
                    android.util.Log.d("PostDetailViewModel", "Adding reaction")
                    apiService.addReaction(postId, emoji)
                }

                // Update reactions with response
                android.util.Log.d("PostDetailViewModel", "Reaction toggle successful, updating UI")
                _uiState.value = _uiState.value.copy(reactions = response.reactions)
            } catch (e: Exception) {
                android.util.Log.e("PostDetailViewModel", "Failed to toggle reaction", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to toggle reaction"
                )
            }
        }
    }
}
