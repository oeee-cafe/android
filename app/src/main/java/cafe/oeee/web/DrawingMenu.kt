package cafe.oeee.web

import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import cafe.oeee.data.remote.WebViewCookieJar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * What a long press on a drawing opens: the drawing itself, and what the gallery offers for
 * a picture -- save, copy, share -- with the post's link when the drawing is one. As the iOS
 * app's DrawingMenu does; the site lets the press through only on drawings.
 */
object DrawingMenu {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Signed in as the page is, as the web view's own request for the drawing was: an
            // image the site serves only to someone signed in would otherwise not come.
            .cookieJar(WebViewCookieJar())
            .build()
    }

    /**
     * A pressed drawing: what the page said at once, and the file, which follows -- fetched
     * as the web view would fetch it, with its user agent and from the page it is on.
     */
    class Drawing(
        val src: String,
        val link: String?,
        val width: Int,
        val height: Int,
        private val referrer: String?,
        private val userAgent: String
    ) {
        constructor(pressed: BridgeMessage.PressedDrawing, referrer: String?, userAgent: String) :
            this(pressed.src, pressed.link, pressed.width, pressed.height, referrer, userAgent)

        private var loading: Deferred<SiteFile?>? = null

        /** Starts fetching the file, once the menu is actually opening. */
        fun load(scope: CoroutineScope): Deferred<SiteFile?> =
            loading ?: scope.async(Dispatchers.IO) { fetch() }.also { loading = it }

        /** The file, once it has arrived; null if it could not be fetched. */
        suspend fun file(scope: CoroutineScope): SiteFile? = load(scope).await()

        private fun fetch(): SiteFile? = try {
            val request = Request.Builder().url(src)
                .header("User-Agent", userAgent)
                .apply { if (referrer != null) header("Referer", referrer) }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body
                val type = imageType(body.contentType()?.let { "${it.type}/${it.subtype}" })
                SiteFile(body.bytes(), type, MediaFiles.nameFor(src, type))
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * What the drawing is. The image store answers `application/octet-stream` for every
     * file, so its name says instead; it is an image the page drew either way.
     */
    private fun Drawing.imageType(served: String?): String {
        if (served != null && served.startsWith("image/")) return served
        val extension = MimeTypeMap.getFileExtensionFromUrl(src)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?.takeIf { it.startsWith("image/") } ?: "image/png"
    }
}

/** What each of the menu's actions does, done by the tab (WebTabController). */
interface DrawingActions {
    fun save(drawing: DrawingMenu.Drawing)
    fun copy(drawing: DrawingMenu.Drawing)
    fun share(drawing: DrawingMenu.Drawing)
    fun copyLink(drawing: DrawingMenu.Drawing)
}

/**
 * The menu, as a sheet: the drawing as large as the screen allows, its pixels kept square
 * rather than smoothed -- most drawings are pixel art a few hundred pixels across.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingSheet(
    drawing: DrawingMenu.Drawing,
    scope: CoroutineScope,
    actions: DrawingActions,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picture by remember(drawing) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(drawing) {
        val file = drawing.file(scope) ?: return@LaunchedEffect
        picture = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size)?.asImageBitmap()
        }
    }
    fun then(action: (DrawingMenu.Drawing) -> Unit): () -> Unit = {
        action(drawing)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            val ratio = if (drawing.width > 0 && drawing.height > 0) drawing.width.toFloat() / drawing.height else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .heightIn(max = 360.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                        .background(Color.White)
                ) {
                    picture?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
            SheetAction(Icons.Filled.Download, stringResource(R.string.drawing_save), then(actions::save))
            SheetAction(Icons.Filled.ContentCopy, stringResource(R.string.drawing_copy), then(actions::copy))
            SheetAction(Icons.Filled.Share, stringResource(R.string.drawing_share), then(actions::share))
            if (drawing.link != null) {
                SheetAction(Icons.Filled.Link, stringResource(R.string.drawing_copy_link), then(actions::copyLink))
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
