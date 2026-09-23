package cafe.oeee.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.webkit.JsResult
import androidx.annotation.StringRes
import cafe.oeee.R

/**
 * The page's `alert()`, `confirm()` and leaving a page with unsaved work, asked as the
 * system's own dialogs. Left to the web view they are titled "The page at https://oeee.cafe
 * says", which no app says. Their words are the site's, in the page's language
 * (BridgeMessage.Words), so every app words them alike.
 */
class SiteDialogs(private val activity: Activity, private val words: () -> BridgeMessage.Words?) {
    private fun builder(): AlertDialog.Builder {
        val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = if (night) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        return AlertDialog.Builder(activity, theme)
    }

    private fun word(pick: (BridgeMessage.Words) -> String?, @StringRes fallback: Int): String =
        activity.word(words(), pick, fallback)

    /** Whether there is a window to show a dialog in; the page's question is answered no otherwise. */
    private val canShow: Boolean get() = !activity.isFinishing && !activity.isDestroyed

    fun alert(message: String?, result: JsResult): Boolean {
        if (!canShow) {
            result.cancel()
            return true
        }
        builder()
            .setMessage(message)
            .setPositiveButton(word({ it.ok }, R.string.ok)) { _, _ -> result.confirm() }
            .setOnCancelListener { result.confirm() }
            .show()
        return true
    }

    fun confirm(message: String?, result: JsResult): Boolean {
        if (!canShow) {
            result.cancel()
            return true
        }
        builder()
            .setMessage(message)
            .setPositiveButton(word({ it.ok }, R.string.ok)) { _, _ -> result.confirm() }
            .setNegativeButton(word({ it.cancel }, R.string.cancel)) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    /** The page would lose something if left: Stay is the safe answer, so it is the one kept. */
    fun beforeUnload(result: JsResult): Boolean {
        if (!canShow) {
            result.confirm()
            return true
        }
        builder()
            .setTitle(word({ it.leaveTitle }, R.string.leave_title))
            .setMessage(word({ it.leaveBody }, R.string.leave_body))
            .setPositiveButton(word({ it.leave }, R.string.leave)) { _, _ -> result.confirm() }
            .setNegativeButton(word({ it.stay }, R.string.stay)) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }
}

/**
 * One of the words the site sent with the page shown ([words]), or before any page has sent
 * them, the app's own. Those are English only: the site's catalogues are where these are
 * translated (the app-* messages in oeee-cafe/web's locales), and a copy here would drift
 * from them.
 */
fun Context.word(
    words: BridgeMessage.Words?,
    pick: (BridgeMessage.Words) -> String?,
    @StringRes fallback: Int
): String = words?.let(pick) ?: getString(fallback)
