package cafe.oeee.web

import android.util.Base64
import org.json.JSONObject

/**
 * The web platform features the site uses that Android's web view leaves out, filled in
 * by the app: sharing (assets/share.js), and saving a file the page made itself
 * (assets/download.js). The scripts post to these names; they are spelled out there too.
 */
object Polyfills {
    const val SHARE_OBJECT_NAME = "oeeeShare"
    const val DOWNLOAD_OBJECT_NAME = "oeeeDownload"

    /** What the page asked to share: a title, and the text and link as one. */
    class Share(val title: String, val text: String)

    fun share(message: String?): Share? = try {
        val info = JSONObject(message ?: "")
        Share(info.optString("title"), info.optString("text")).takeIf { it.text.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    /** The file the page handed over, from its data URL; null if it could not be read. */
    fun download(message: String?): SiteFile? = try {
        val info = JSONObject(message ?: "")
        val data = info.getString("data")
        val comma = data.indexOf(',')
        val header = data.substring(0, comma)
        val bytes = if (header.endsWith(";base64")) {
            Base64.decode(data.substring(comma + 1), Base64.DEFAULT)
        } else {
            java.net.URLDecoder.decode(data.substring(comma + 1), "UTF-8").toByteArray()
        }
        val type = info.optString("type").ifEmpty {
            header.removePrefix("data:").substringBefore(';').ifEmpty { "application/octet-stream" }
        }
        val name = info.optString("name").takeIf { it.isNotBlank() && it.contains('.') }
            ?: MediaFiles.nameFor(info.optString("name").ifBlank { "oeee-cafe" }, type)
        SiteFile(bytes, type, name.replace('/', '_'))
    } catch (e: Exception) {
        null
    }
}
