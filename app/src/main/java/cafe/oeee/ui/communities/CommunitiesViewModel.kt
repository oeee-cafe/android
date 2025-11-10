package cafe.oeee.ui.communities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.ActiveCommunity
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunitiesUiState(
    val myCommunities: List<ActiveCommunity> = emptyList(),
    val publicCommunities: List<ActiveCommunity> = emptyList(),
    val filteredMyCommunities: List<ActiveCommunity> = emptyList(),
    val filteredPrivateCommunities: List<ActiveCommunity> = emptyList(),
    val filteredUnlistedCommunities: List<ActiveCommunity> = emptyList(),
    val filteredPublicMyCommunities: List<ActiveCommunity> = emptyList(),
    val filteredPublicCommunities: List<ActiveCommunity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSearching: Boolean = false,
    val searchResults: List<ActiveCommunity> = emptyList(),
    val searchHasMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

@OptIn(FlowPreview::class)
class CommunitiesViewModel : ViewModel() {
    private val apiService = ApiClient.apiService

    private val _uiState = MutableStateFlow(CommunitiesUiState())
    val uiState: StateFlow<CommunitiesUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadCommunities()
        setupSearchDebounce()
    }

    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300) // 300ms debounce
                .distinctUntilChanged()
                .collect { query ->
                    val trimmedQuery = query.trim()
                    if (trimmedQuery.isEmpty()) {
                        // Clear search results and show browse mode
                        _uiState.update {
                            it.copy(
                                searchQuery = "",
                                searchResults = emptyList(),
                                isSearching = false,
                                searchHasMore = false
                            )
                        }
                    } else {
                        // Perform server-side search for public communities
                        performSearch(trimmedQuery)
                    }
                }
        }
    }

    fun loadCommunities() {
        // Don't reload if we already have data and are not showing an error
        if (_uiState.value.isLoading ||
            (_uiState.value.myCommunities.isNotEmpty() || _uiState.value.publicCommunities.isNotEmpty()) && _uiState.value.error == null
        ) {
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Load my communities
                val myCommunitiesResponse = apiService.getCommunitiesList()

                // Load first page of public communities
                val publicCommunitiesResponse = apiService.getPublicCommunities(offset = 0, limit = 20)

                val myComms = myCommunitiesResponse.communities.distinctBy { it.id }
                val publicComms = publicCommunitiesResponse.communities.distinctBy { it.id }

                _uiState.update {
                    it.copy(
                        myCommunities = myComms,
                        publicCommunities = publicComms,
                        filteredMyCommunities = myComms,
                        filteredPrivateCommunities = myComms.filter { it.visibility == "private" },
                        filteredUnlistedCommunities = myComms.filter { it.visibility == "unlisted" },
                        filteredPublicMyCommunities = myComms.filter { it.visibility == "public" },
                        filteredPublicCommunities = publicComms,
                        hasMore = publicCommunitiesResponse.pagination.hasMore,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null, publicCommunities = emptyList()) }

        viewModelScope.launch {
            try {
                // Load my communities
                val myCommunitiesResponse = apiService.getCommunitiesList()

                // Load first page of public communities
                val publicCommunitiesResponse = apiService.getPublicCommunities(offset = 0, limit = 20)

                val myComms = myCommunitiesResponse.communities.distinctBy { it.id }
                val publicComms = publicCommunitiesResponse.communities.distinctBy { it.id }

                _uiState.update {
                    it.copy(
                        myCommunities = myComms,
                        publicCommunities = publicComms,
                        filteredMyCommunities = myComms,
                        filteredPrivateCommunities = myComms.filter { it.visibility == "private" },
                        filteredUnlistedCommunities = myComms.filter { it.visibility == "unlisted" },
                        filteredPublicMyCommunities = myComms.filter { it.visibility == "public" },
                        filteredPublicCommunities = publicComms,
                        hasMore = publicCommunitiesResponse.pagination.hasMore,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun loadMorePublicCommunities() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMore || currentState.searchQuery.isNotEmpty()) {
            return
        }

        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val offset = currentState.publicCommunities.size
                val response = apiService.getPublicCommunities(offset = offset, limit = 20)

                val newCommunities = response.communities.distinctBy { it.id }
                val allPublicCommunities = (currentState.publicCommunities + newCommunities).distinctBy { it.id }

                _uiState.update {
                    it.copy(
                        publicCommunities = allPublicCommunities,
                        filteredPublicCommunities = if (currentState.searchQuery.isEmpty()) {
                            allPublicCommunities
                        } else {
                            // Re-filter with search query
                            val normalizedQuery = currentState.searchQuery.lowercase().trim()
                            allPublicCommunities.filter { community ->
                                community.name.lowercase().contains(normalizedQuery) ||
                                community.slug.lowercase().contains(normalizedQuery) ||
                                (community.description?.lowercase()?.contains(normalizedQuery) ?: false)
                            }
                        },
                        hasMore = response.pagination.hasMore,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        error = e.message
                    )
                }
            }
        }
    }

    private fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, searchQuery = query) }

        viewModelScope.launch {
            try {
                // Server-side search for public communities
                val searchResponse = apiService.searchPublicCommunities(query = query, offset = 0, limit = 20)
                val searchResults = searchResponse.communities.distinctBy { it.id }

                // Client-side filter for my communities
                val normalizedQuery = query.lowercase().trim()
                val filteredMyCommunities = _uiState.value.myCommunities.filter { community ->
                    community.name.lowercase().contains(normalizedQuery) ||
                    community.slug.lowercase().contains(normalizedQuery) ||
                    (community.description?.lowercase()?.contains(normalizedQuery) ?: false)
                }

                _uiState.update {
                    it.copy(
                        searchQuery = query,
                        searchResults = searchResults,
                        searchHasMore = searchResponse.pagination.hasMore,
                        filteredMyCommunities = filteredMyCommunities,
                        filteredPrivateCommunities = filteredMyCommunities.filter { it.visibility == "private" },
                        filteredUnlistedCommunities = filteredMyCommunities.filter { it.visibility == "unlisted" },
                        filteredPublicMyCommunities = filteredMyCommunities.filter { it.visibility == "public" },
                        isSearching = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = e.message
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        // Update the search query flow which will trigger debounced search
        _searchQuery.value = query
        // Immediately update the UI state to show the query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        onSearchQueryChange("")
    }
}
