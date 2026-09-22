package cafe.oeee.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A file the site handed over: a drawing pressed, or something it offered to save. */
class SiteFile(val bytes: ByteArray, val mimeType: String, val name: String) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

/**
 * Where the site's files go: the gallery for pictures and Downloads for the rest, as the
 * files they are -- a pixel drawing keeps its PNG rather than being re-encoded -- and the
 * share sheet and clipboard through the app's own file provider.
 */
object MediaFiles {
    private const val TAG = "MediaFiles"
    private const val FOLDER = "Oeee Cafe"
    private const val SHARED_DIR = "shared"

    /** Whether saving to the shared folders needs the storage permission (Android 9 and older). */
    val needsStoragePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Saves [file] to Pictures or Downloads; false when it couldn't be written. */
    suspend fun save(context: Context, file: SiteFile): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(context, file)
            else saveToPublicDirectory(context, file)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save ${file.name}", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, file: SiteFile) {
        val resolver = context.contentResolver
        val (collection, folder) = if (file.isImage) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_PICTURES
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore refused ${file.name}")
        try {
            resolver.openOutputStream(uri)?.use { it.write(file.bytes) } ?: error("No stream for $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDirectory(context: Context, file: SiteFile) {
        val type = if (file.isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
        val folder = File(Environment.getExternalStoragePublicDirectory(type), FOLDER).apply { mkdirs() }
        val target = uniqueFile(folder, file.name)
        target.writeBytes(file.bytes)
        // So the gallery sees it without waiting for its next scan.
        android.media.MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(file.mimeType), null)
    }

    private fun uniqueFile(folder: File, name: String): File {
        var candidate = File(folder, name)
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (candidate.exists()) candidate = File(folder, "$base ($n)$extension").also { n++ }
        return candidate
    }

    /** Opens the share sheet for [file], with [link] as its text when there is one. */
    suspend fun share(context: Context, file: SiteFile, link: String?, title: CharSequence) {
        val uri = shareable(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            if (link != null) putExtra(Intent.EXTRA_TEXT, link)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }

    /** Puts [file] on the clipboard; Android 13 and later say so themselves. */
    suspend fun copy(context: Context, file: SiteFile) {
        val uri = shareable(context, file)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, file.name, uri))
    }

    /** The file in the app's cache, where the file provider can hand it to other apps. */
    private suspend fun shareable(context: Context, file: SiteFile): Uri = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        // One at a time is all anyone shares; the last one is kept until the next.
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, file.name).apply { writeBytes(file.bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }

    /** A file name for [url] of [mimeType], from its last path segment. */
    fun nameFor(url: String, mimeType: String): String {
        val last = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() && !it.contains('/') }
        if (last != null && last.contains('.')) return last
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        return "${last ?: "oeee-cafe"}.$extension"
    }
}
