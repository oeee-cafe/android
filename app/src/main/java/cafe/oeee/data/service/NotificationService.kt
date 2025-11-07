package cafe.oeee.data.service

import android.content.Context
import cafe.oeee.data.model.notification.DeleteNotificationResponse
import cafe.oeee.data.model.notification.MarkAllReadResponse
import cafe.oeee.data.model.notification.MarkNotificationReadResponse
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
            if (response.success) {
                Result.success(response.notification)
            } else {
                Result.failure(Exception(response.error ?: "Failed to mark notification as read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAllAsRead(): Result<Long> {
        return try {
            val response = apiService.markAllNotificationsAsRead()
            if (response.success) {
                Result.success(response.count)
            } else {
                Result.failure(Exception("Failed to mark all notifications as read"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            val response = apiService.deleteNotification(notificationId)
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Failed to delete notification"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
