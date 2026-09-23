package cafe.oeee

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import cafe.oeee.data.service.AuthService
import cafe.oeee.data.service.PushNotificationService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows the site's push notifications. Tapping one opens the page its `url` names
 * (NavigationCoordinator); the site decides where each kind of notification leads.
 */
class OeeeCafeMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "oeee_cafe_notifications"
    }

    /** Work that lasts as long as the service does, and no longer. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // A token can be refreshed with no activity started, and is only registered for
        // someone signed in, which is what the last page said (AuthService).
        AuthService.start(this)
        createNotificationChannel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** A new token, on install or when FCM refreshes it: registered for whoever is signed in. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { PushNotificationService.registerFcmToken(token) }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // What a notification says is the reader's own business, so none of it is logged.
        val notification = remoteMessage.notification
        val data = remoteMessage.data
        when {
            notification != null -> showNotification(
                title = notification.title ?: "oeee.cafe",
                body = notification.body ?: "",
                data = data
            )
            // A data message only still shows as a notification.
            data.isNotEmpty() -> showNotification(data["title"] ?: "oeee.cafe", data["body"] ?: "", data)
            else -> Log.d(TAG, "Empty message")
        }
    }

    /** The one channel every notification is posted in, named in the reader's language. */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_description)
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Opens the page in the running app rather than starting it over.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // The page to open (`url`) and the rest of the payload, as FCM hands them to the
            // app when the system shows the notification itself.
            data.forEach { (key, value) -> putExtra(key, value) }
        }

        // The same notification sent twice replaces itself rather than showing again; and its
        // request code keeps each notification's intent, and so its page, apart from the others'.
        val notificationId = (data["notification_id"] ?: "$title\n$body").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setNumber(data["badge"]?.toIntOrNull() ?: 0)

        getSystemService(NotificationManager::class.java).notify(notificationId, notificationBuilder.build())
    }
}
