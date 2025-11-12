package cafe.oeee.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import cafe.oeee.R
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageSaver {
    fun hasStoragePermission(context: Context): Boolean {
        // For API 29+, we don't need WRITE_EXTERNAL_STORAGE for scoped storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        }
        // For API 28 and below, check permission
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun saveImage(
        context: Context,
        imageUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Check if we have permission
        if (!hasStoragePermission(context)) {
            onError(context.getString(R.string.post_image_permission_denied))
            return
        }

        // Permission granted or not needed, proceed with download and save
        downloadAndSave(context, imageUrl, onSuccess, onError)
    }

    private suspend fun downloadAndSave(
        context: Context,
        imageUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Download image using Coil
            val bitmap = downloadImage(context, imageUrl)
                ?: run {
                    onError(context.getString(R.string.post_image_download_failed))
                    return
                }

            // Save to MediaStore
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveImageToMediaStoreQ(context, bitmap)
            } else {
                saveImageToMediaStoreLegacy(context, bitmap)
            }

            if (saved) {
                onSuccess()
            } else {
                onError(context.getString(R.string.post_image_save_failed))
            }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: context.getString(R.string.post_image_save_failed))
        }
    }

    private suspend fun downloadImage(context: Context, imageUrl: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .build()

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    result.drawable.toBitmap()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun saveImageToMediaStoreQ(context: Context, bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "oeee_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OeeeCafe")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext false

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                true
            } catch (e: IOException) {
                false
            }
        }
    }

    private suspend fun saveImageToMediaStoreLegacy(context: Context, bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val oeeeDir = File(picturesDir, "OeeeCafe")
                if (!oeeeDir.exists()) {
                    oeeeDir.mkdirs()
                }

                val imageFile = File(oeeeDir, "oeee_${System.currentTimeMillis()}.png")
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                // Notify media scanner
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                true
            } catch (e: IOException) {
                false
            }
        }
    }
}
