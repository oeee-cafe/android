package cafe.oeee.web

import android.util.Base64

/**
 * The web platform features the site uses that Android's web view leaves out, and the page
 * hands the app instead on the bridge (app_polyfills.jinja in oeee-cafe/web): sharing, which
 * needs nothing more than [BridgeMessage.Share], and saving a file the page made itself.
 */
object Polyfills {
    /** The file the page handed over, from its data URL; null if it could not be read. */
    fun file(download: BridgeMessage.Download): SiteFile? = try {
        val data = download.data
        val comma = data.indexOf(',')
        val header = data.substring(0, comma)
        // The page reads every file with readAsDataURL, which always writes base64.
        require(header.endsWith(";base64")) { "Not base64: $header" }
        val bytes = Base64.decode(data.substring(comma + 1), Base64.DEFAULT)
        val type = header.removePrefix("data:").substringBefore(';').ifEmpty { "application/octet-stream" }
        val name = download.name.takeIf { it.isNotBlank() && it.contains('.') }
            ?: MediaFiles.nameFor(download.name.ifBlank { "oeee-cafe" }, type)
        SiteFile(bytes, type, name.replace('/', '_'))
    } catch (e: Exception) {
        null
    }
}
