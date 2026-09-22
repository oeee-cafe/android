package cafe.oeee.web

import android.util.Base64
import org.json.JSONObject

/**
 * The web platform features the site uses that Android's web view leaves out, filled in
 * by the app: sharing, and saving a file the page made itself.
 */
object SiteBridges {
    const val SHARE_OBJECT_NAME = "oeeeShare"
    const val DOWNLOAD_OBJECT_NAME = "oeeeDownload"

    /**
     * `navigator.share`, which the web view does not have, through Android's share sheet.
     * The site checks for it before falling back to copying the link (sharePost in
     * post_view.jinja, oeee-cafe/web). Text and links only; no files.
     */
    val SHARE_SCRIPT = """
        (function () {
          var bridge = window.$SHARE_OBJECT_NAME;
          if (!bridge || navigator.share) return;
          function shareable(data) {
            return !!data && !(data.files && data.files.length) && !!(data.url || data.text || data.title);
          }
          navigator.share = function (data) {
            if (!shareable(data)) {
              return Promise.reject(new TypeError('Nothing this app can share'));
            }
            var text = [data.text, data.url].filter(Boolean).join('\n');
            bridge.postMessage(JSON.stringify({ title: data.title || '', text: text || data.title }));
            return Promise.resolve();
          };
          navigator.canShare = shareable;
        })();
    """.trimIndent()

    /**
     * Files the page makes itself -- the painter's PNG, a collaborative session's save --
     * are a link to a `blob:` or `data:` URL clicked with `download`, which the web view
     * drops without a word: its download listener gets a URL that only the page can read.
     * So the page reads it and hands the app the bytes. The painter revokes the URL the
     * moment after the click, which is why the read has to start inside it.
     */
    val DOWNLOAD_SCRIPT = """
        (function () {
          if (window.__oeeeDownload) return;
          window.__oeeeDownload = true;
          var bridge = window.$DOWNLOAD_OBJECT_NAME;
          if (!bridge) return;
          function local(link) {
            return link.hasAttribute('download') && /^(blob|data):/i.test(link.href);
          }
          function save(link) {
            var name = link.getAttribute('download') || '';
            fetch(link.href).then(function (response) {
              return response.blob();
            }).then(function (blob) {
              var reader = new FileReader();
              reader.onload = function () {
                bridge.postMessage(JSON.stringify({ name: name, type: blob.type || '', data: reader.result }));
              };
              reader.readAsDataURL(blob);
            });
          }
          // A link made and clicked from script, never put in the page, as the painter's is.
          var click = HTMLAnchorElement.prototype.click;
          HTMLAnchorElement.prototype.click = function () {
            if (local(this)) {
              save(this);
              return;
            }
            return click.apply(this, arguments);
          };
          // A link in the page, clicked by a finger.
          document.addEventListener('click', function (event) {
            var link = event.target && event.target.closest ? event.target.closest('a[download]') : null;
            if (!link || !local(link) || event.defaultPrevented) return;
            event.preventDefault();
            save(link);
          }, true);
        })();
    """.trimIndent()

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
