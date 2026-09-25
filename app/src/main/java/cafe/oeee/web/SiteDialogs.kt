package cafe.oeee.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.webkit.JsResult
import androidx.annotation.StringRes
import cafe.oeee.R

/**
 * Leaving a page with unsaved work, asked as the system's own dialog. Left to the web view
 * it is titled "The page at https://oeee.cafe says", which no app says. Its words are the
 * site's, in the page's language (BridgeMessage.Words), so every app words it alike. The
 * site asks everything else in its own dialog.
 */
class SiteDialogs(
    private val activity: Activity,
    private val words: () -> BridgeMessage.Words?,
    /** Whether the site is in its dark look, which the dialog wears over it (WebController.isDark). */
    private val dark: () -> Boolean
) {
    private fun builder(): AlertDialog.Builder {
        val theme = if (dark()) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        return AlertDialog.Builder(activity, theme)
    }

    private fun word(pick: (BridgeMessage.Words) -> String?, @StringRes fallback: Int): String =
        activity.word(words(), pick, fallback)

    /** Whether there is a window to show a dialog in; the page's question is answered no otherwise. */
    private val canShow: Boolean get() = !activity.isFinishing && !activity.isDestroyed

    /**
     * The page would lose something if left: Stay is the safe answer, so it is the one kept.
     * [onLeave] follows a Leave, which only the app hears.
     */
    fun beforeUnload(result: JsResult, onLeave: () -> Unit = {}): Boolean {
        if (!canShow) {
            result.confirm()
            return true
        }
        builder()
            .setTitle(word({ it.leaveTitle }, R.string.leave_title))
            .setMessage(word({ it.leaveBody }, R.string.leave_body))
            .setPositiveButton(word({ it.leave }, R.string.leave)) { _, _ ->
                result.confirm()
                onLeave()
            }
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
