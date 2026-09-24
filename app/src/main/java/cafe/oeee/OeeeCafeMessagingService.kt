package cafe.oeee

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import cafe.oeee.data.service.PushNotificationService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Shows the site's push notifications. Tapping one opens the page its `url` names
 * (OpenedFrom); the site decides where each kind of notification leads.
 */
class OeeeCafeMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"

        /**
         * The one channel every notification is posted in: by the app, and by FCM itself for
         * a push that arrives while the app is not in front, which the manifest's
         * default_notification_channel_id sends here too.
         */
        private const val CHANNEL_ID = "oeee_cafe_notifications"

        /**
         * Creates the channel, named in the reader's language, or renames it. Done as the
         * app's process starts (OeeeCafeApplication), so it is there before FCM shows the
         * first push in it, and again as this service starts, for a language changed since.
         */
        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    /**
     * A new token, on install or when FCM refreshes it: handed to the page showing if
     * someone is signed in there, and otherwise to the next page that says someone is.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushNotificationService.tokenArrived(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // What a notification says is the reader's own business, so none of it is logged.
        // The site sends every push with a notification (src/push/fcm.rs in oeee-cafe/web).
        val notification = remoteMessage.notification
        if (notification == null) {
            Log.d(TAG, "Not a notification")
            return
        }
        showNotification(
            title = notification.title ?: "oeee.cafe",
            body = notification.body ?: "",
            data = remoteMessage.data
        )
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
            .setColor(getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setNumber(data["badge"]?.toIntOrNull() ?: 0)

        getSystemService(NotificationManager::class.java).notify(notificationId, notificationBuilder.build())
    }
}
