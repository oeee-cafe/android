package cafe.oeee

import android.content.Intent
import android.net.Uri
import android.util.Log
import cafe.oeee.web.WebTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Turns a tapped push notification into a page of the site, and the tab to open it in. */
object NavigationCoordinator {
    private const val TAG = "NavigationCoordinator"

    data class PendingNavigation(val tab: WebTab, val path: String)

    private val _pendingNavigation = MutableStateFlow<PendingNavigation?>(null)
    val pendingNavigation: StateFlow<PendingNavigation?> = _pendingNavigation.asStateFlow()

    private fun navigate(path: String, tab: WebTab) {
        Log.d(TAG, "Navigating to $path in ${tab.name}")
        _pendingNavigation.value = PendingNavigation(tab, path)
    }

    private fun segment(value: String): String = Uri.encode(value)

    /** Handles the data of a push notification the app was opened from, if any. */
    fun handleNotificationIntent(intent: Intent) {
        val notificationType = intent.getStringExtra("notification_type") ?: return
        Log.i(TAG, "Handling notification tap: type=$notificationType")

        when (notificationType) {
            "Comment", "Mention", "CommentReply", "PostReply", "CommunityPost", "Reaction" -> {
                val postId = intent.getStringExtra("post_id")
                if (postId != null) {
                    navigate("/posts/${segment(postId)}", WebTab.HOME)
                } else {
                    Log.w(TAG, "Missing post_id for $notificationType")
                }
            }

            "Follow", "GuestbookEntry", "GuestbookReply" -> {
                val actorLoginName = intent.getStringExtra("actor_login_name")
                if (actorLoginName != null) {
                    navigate("/@${segment(actorLoginName)}", WebTab.HOME)
                } else {
                    Log.w(TAG, "Missing actor_login_name for $notificationType")
                }
            }

            // Invitations are listed on the notifications page
            "community_invite" -> navigate(WebTab.NOTIFICATIONS.path, WebTab.NOTIFICATIONS)

            "invitation_accepted", "invitation_declined" -> {
                val communitySlug = intent.getStringExtra("community_slug")
                if (communitySlug != null) {
                    navigate("/communities/@${segment(communitySlug)}/members", WebTab.COMMUNITIES)
                } else {
                    Log.w(TAG, "Missing community_slug for $notificationType")
                }
            }

            else -> {
                Log.w(TAG, "Unknown notification type: $notificationType")
                navigate(WebTab.NOTIFICATIONS.path, WebTab.NOTIFICATIONS)
            }
        }
    }

    fun clearPendingNavigation() {
        _pendingNavigation.value = null
    }
}
