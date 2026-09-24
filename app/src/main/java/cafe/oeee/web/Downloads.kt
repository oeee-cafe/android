package cafe.oeee.web

import android.app.Activity
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.HapticFeedbackConstants
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import cafe.oeee.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Where the web view's files go: a file the site links to through the system's download manager,
 * and one the page made or a drawing pressed into Pictures or Downloads ([MediaFiles]).
 */
class Downloads(
    private val activity: Activity,
    private val webView: WebView,
    private val storagePermission: StoragePermission,
    /** What the page shown says, for saying where a file went in its language (SiteDialogs.word). */
    private val words: () -> BridgeMessage.Words?
) {
    /**
     * A file the site links to, rather than makes: to Downloads through the system's download
     * manager, which shows its progress, signed in as the page is.
     */
    fun download(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(uri)
            .setTitle(name)
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .apply {
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                // Android 9 and older would need the storage permission for this; there the
                // download manager keeps the file itself, still listed in Downloads.
                if (!MediaFiles.needsStoragePermission) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                }
            }
        try {
            activity.getSystemService(DownloadManager::class.java).enqueue(request)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't download $url", e)
            saved(null)
        }
    }

    /** Puts [file] in Pictures or Downloads, and says whether it went there. */
    suspend fun save(file: SiteFile?) {
        saved(file?.takeIf { mayWriteSharedFolders() && MediaFiles.save(activity, it) })
    }

    /** Says where a file went, or that it didn't, and is felt either way. */
    fun saved(file: SiteFile?) {
        val words = words()
        val message = when {
            file == null -> activity.word(words, { it.saveFailed }, R.string.save_failed)
            file.isImage -> activity.word(words, { it.savedImage }, R.string.saved_image)
            else -> activity.word(words, { it.savedFile }, R.string.saved_file)
        }
        feel(if (file == null) "error" else "success")
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    /** Plays one of the site's haptic names on the web view. */
    fun feel(name: String) {
        webView.performHapticFeedback(hapticFeedback(name) ?: HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private suspend fun mayWriteSharedFolders(): Boolean {
        if (!MediaFiles.needsStoragePermission) return true
        return suspendCancellableCoroutine { continuation ->
            storagePermission.request { granted -> if (continuation.isActive) continuation.resume(granted) }
        }
    }

    private companion object {
        const val TAG = "Downloads"
    }
}
