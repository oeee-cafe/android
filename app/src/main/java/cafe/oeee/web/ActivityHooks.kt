package cafe.oeee.web

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/** Lets the site's file inputs pick files, through the activity. */
fun interface FileChooser {
    fun show(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
}

/** Asks, through the activity, to write to the shared folders (Android 9 and older only). */
fun interface StoragePermission {
    fun request(onResult: (Boolean) -> Unit)
}
