package cafe.oeee

import android.app.Application

/**
 * The app's process. Its one job is the notification channel, which has to exist before FCM
 * shows a push in it, whether or not the app has been opened since it started.
 */
class OeeeCafeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OeeeCafeMessagingService.createNotificationChannel(this)
    }
}
