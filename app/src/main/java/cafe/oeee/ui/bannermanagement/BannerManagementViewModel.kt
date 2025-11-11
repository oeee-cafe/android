package cafe.oeee.ui.bannermanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.BannerListItem
import cafe.oeee.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BannerManagementUiState(
    val banners: List<BannerListItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isActivating: Boolean = false,
    val isDeleting: Boolean = false
)

class BannerManagementViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BannerManagementUiState())
    val uiState: StateFlow<BannerManagementUiState> = _uiState.asStateFlow()

    fun loadBanners() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = ApiClient.apiService.getBanners()
                _uiState.update {
                    it.copy(
                        banners = response.banners,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load banners"
                    )
                }
            }
        }
    }

    fun activateBanner(bannerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActivating = true, error = null) }
            try {
                ApiClient.apiService.activateBanner(bannerId)
                loadBanners() // Reload to update active status
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isActivating = false,
                        error = e.message ?: "Failed to activate banner"
                    )
                }
            }
        }
    }

    fun deleteBanner(bannerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, error = null) }
            try {
                ApiClient.apiService.deleteBanner(bannerId)
                loadBanners() // Reload to remove deleted banner
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        error = e.message ?: "Failed to delete banner. You cannot delete your active banner."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
