package cafe.oeee.data.service

import android.content.Context
import cafe.oeee.data.model.notification.NotificationItem
import cafe.oeee.data.model.notification.NotificationsResponse
import cafe.oeee.data.remote.ApiClient

class NotificationService private constructor(private val context: Context) {
    private val apiService = ApiClient.apiService

    companion object {
        @Volatile
        private var INSTANCE: NotificationService? = null

        fun getInstance(context: Context): NotificationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun fetchNotifications(limit: Int = 50, offset: Int = 0): Result<NotificationsResponse> {
        return try {
            val response = apiService.getNotifications(limit, offset)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUnreadCount(): Result<Long> {
        return try {
            val response = apiService.getUnreadNotificationCount()
            Result.success(response.count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsRead(notificationId: String): Result<NotificationItem?> {
        return try {
            val response = apiService.markNotificationAsRead(notificationId)
            Result.success(response.notification)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllAsRead(): Result<Long> {
        return try {
            val response = apiService.markAllNotificationsAsRead()
            Result.success(response.count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            // Server returns 204 No Content on success
            apiService.deleteNotification(notificationId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
