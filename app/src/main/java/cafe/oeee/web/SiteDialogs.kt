package cafe.oeee.web

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.webkit.JsResult
import cafe.oeee.R

/**
 * The page's `alert()`, `confirm()` and leaving a page with unsaved work, asked as the
 * system's own dialogs. Left to the web view they are titled "The page at https://oeee.cafe
 * says", which no app says; the leaving one is worded as the iOS app words it.
 */
class SiteDialogs(private val activity: Activity) {
    private fun builder(): AlertDialog.Builder {
        val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = if (night) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        return AlertDialog.Builder(activity, theme)
    }

    /** Whether there is a window to show a dialog in; the page's question is answered no otherwise. */
    private val canShow: Boolean get() = !activity.isFinishing && !activity.isDestroyed

    fun alert(message: String?, result: JsResult): Boolean {
        if (!canShow) {
            result.cancel()
            return true
        }
        builder()
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> result.confirm() }
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
            .setPositiveButton(R.string.ok) { _, _ -> result.confirm() }
            .setNegativeButton(R.string.cancel) { _, _ -> result.cancel() }
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
            .setTitle(R.string.leave_title)
            .setMessage(R.string.leave_body)
            .setPositiveButton(R.string.leave) { _, _ -> result.confirm() }
            .setNegativeButton(R.string.stay) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }
}
