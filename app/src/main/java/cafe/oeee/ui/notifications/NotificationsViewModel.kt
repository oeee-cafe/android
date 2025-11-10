package cafe.oeee.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.oeee.data.model.notification.NotificationItem
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.service.NotificationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val unreadCount: Long = 0,
    val invitationCount: Int = 0,
    val hasLoadedData: Boolean = false
)

class NotificationsViewModel(context: Context) : ViewModel() {
    private val notificationService = NotificationService.getInstance(context)
    private val apiClient = ApiClient

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val pageSize = 50
    private var currentOffset = 0

    fun loadInitial() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentOffset = 0

            val result = notificationService.fetchNotifications(limit = pageSize, offset = 0)

            result.fold(
                onSuccess = { response ->
                    // Filter out notifications with unknown types (null notificationType)
                    val validNotifications = response.notifications.filter { it.notificationType != null }

                    _uiState.value = _uiState.value.copy(
                        notifications = validNotifications,
                        hasMore = response.hasMore,
                        isLoading = false,
                        hasLoadedData = true
                    )
                    currentOffset = pageSize

                    // Also load unread count and invitation count
                    updateUnreadCount()
                    updateInvitationCount()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load notifications",
                        hasLoadedData = true
                    )
                }
            )
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            val result = notificationService.fetchNotifications(limit = pageSize, offset = currentOffset)

            result.fold(
                onSuccess = { response ->
                    // Filter out notifications with unknown types (null notificationType)
                    val validNotifications = response.notifications.filter { it.notificationType != null }

                    _uiState.value = _uiState.value.copy(
                        notifications = _uiState.value.notifications + validNotifications,
                        hasMore = response.hasMore,
                        isLoadingMore = false
                    )
                    currentOffset += pageSize
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = error.message ?: "Failed to load more notifications"
                    )
                }
            )
        }
    }

    fun refresh() {
        loadInitial()
    }

    fun markAsRead(notification: NotificationItem) {
        if (notification.isRead) return

        viewModelScope.launch {
            val result = notificationService.markAsRead(notification.id)

            result.fold(
                onSuccess = { updatedNotification ->
                    if (updatedNotification != null) {
                        // Update the notification in the list
                        val updatedList = _uiState.value.notifications.map {
                            if (it.id == notification.id) updatedNotification else it
                        }
                        _uiState.value = _uiState.value.copy(notifications = updatedList)

                        // Update unread count
                        updateUnreadCount()
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to mark as read: ${error.message}"
                    )
                }
            )
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val result = notificationService.markAllAsRead()

            result.fold(
                onSuccess = {
                    // Refresh to get updated notifications
                    refresh()
                    // Explicitly update unread count after refresh to ensure badge is updated
                    viewModelScope.launch {
                        updateUnreadCount()
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to mark all as read: ${error.message}"
                    )
                }
            )
        }
    }

    fun deleteNotification(notification: NotificationItem) {
        viewModelScope.launch {
            val result = notificationService.deleteNotification(notification.id)

            result.fold(
                onSuccess = {
                    // Remove from local list
                    val updatedList = _uiState.value.notifications.filter { it.id != notification.id }
                    _uiState.value = _uiState.value.copy(notifications = updatedList)

                    // Update unread count
                    updateUnreadCount()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete notification: ${error.message}"
                    )
                }
            )
        }
    }

    private suspend fun updateUnreadCount() {
        val result = notificationService.getUnreadCount()
        result.fold(
            onSuccess = { count ->
                _uiState.value = _uiState.value.copy(unreadCount = count)
            },
            onFailure = {
                // Silently fail for unread count updates
            }
        )
    }

    fun updateInvitationCount() {
        viewModelScope.launch {
            try {
                val response = apiClient.apiService.getUserInvitations()
                _uiState.value = _uiState.value.copy(invitationCount = response.invitations.size)
            } catch (e: Exception) {
                // Silently fail for invitation count updates
                android.util.Log.d("NotificationsViewModel", "Failed to update invitation count: ${e.message}")
            }
        }
    }
}
