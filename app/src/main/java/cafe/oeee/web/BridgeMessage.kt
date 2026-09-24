package cafe.oeee.web

import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/**
 * What the site tells the app over `window.oeeeBridge`, as app_bridge.jinja in oeee-cafe/web
 * describes it: one JSON string per message, `{v: 1, type, ...}`. Types and fields this app
 * does not know are ignored, so the site can add either without a release of the app.
 *
 * Nothing here touches Android but org.json, which the JVM's own tests have from Maven, so
 * the parsing is checked there. That org.json is not quite Android's -- it is stricter about
 * what is not JSON -- but they read JSON the same, and that is all the page sends.
 */
sealed interface BridgeMessage {
    /**
     * The page shown, sent on every page and again whenever any of it changes. Only what this
     * app acts on is read; the site says more, for the other apps. So does it send types this
     * app has no use for -- the bell's number, the painter being ready, a store's -- which
     * are left to the unknown ones.
     */
    data class Page(
        /** Null on a page without the toolbar, which cannot tell. */
        val signedIn: Boolean?,
        /** Whether pulling down may reload the page; neither a painter nor a replay may be. */
        val refreshable: Boolean
    ) : BridgeMessage

    /**
     * The design system's ground -- what the page has at both its edges, so the bars are drawn
     * in it -- and the grid ruled on it every 14px, for what the app draws where the page does
     * not reach.
     */
    data class Theme(
        val ground: Color?,
        val grid: Color?
    ) : BridgeMessage

    /**
     * What the app says in dialogs and menus of its own over the page, in the page's language,
     * sent once a page. Each is null where the page left it out, and the app's own is said then.
     */
    data class Words(
        val leaveTitle: String?,
        val leaveBody: String?,
        val leave: String?,
        val stay: String?,
        val saveImage: String?,
        val copyImage: String?,
        val share: String?,
        val copyLink: String?,
        val savedImage: String?,
        val savedFile: String?,
        val saveFailed: String?
    ) : BridgeMessage

    /** A control that is felt as well as seen: one of the names [hapticFeedback] knows. */
    data class Haptic(val name: String) : BridgeMessage

    /** As a finger lands, the drawing it landed on; null when it is not on one. */
    data class Pressed(val drawing: PressedDrawing?) : BridgeMessage

    /**
     * A sign-in the page is carrying (app_sign_in.jinja): make an ID token for [nonce] with
     * the platform's own sheet, and answer with `window.oeeeApp.signIn.answer`. The page
     * chooses which way each app signs in with each provider, and the only one it sends
     * this one here is Google, so the provider it names is not read. A sign-in with no nonce
     * -- Steam's, which is only ever sent to the Steam build -- is none of this app's.
     */
    data class SignIn(val nonce: String) : BridgeMessage

    /**
     * The same, handed to the system's browser: open [url], which the page says is the site's
     * own. The app checks that for itself before opening anything (SignInHandoff).
     */
    data class Browse(val url: String) : BridgeMessage

    /** `navigator.share` with text and links only, which Android's web view does not have. */
    data class Share(val title: String, val text: String) : BridgeMessage

    /**
     * A file the page made itself -- a blob: or data: link clicked with `download`, which
     * Android's web view drops -- read by the page into [data], a data: URL (app_polyfills.jinja).
     *
     * Its type is read from the data URL rather than from a field of the message: a field
     * called `type` is the one every message already has, and the page's `post` would put
     * the file's over the message's.
     */
    data class Download(val name: String, val data: String) : BridgeMessage {
        // Megabytes of base64 are no use in a failed assertion or a log line.
        override fun toString(): String = "Download(name=$name, data=${data.length} chars)"
    }

    /** A drawing as the page describes it; [link] is null on the post's own page. */
    data class PressedDrawing(val src: String, val link: String?, val width: Int, val height: Int)

    companion object {
        /** The only version of the contract this app speaks; a later one may mean something else. */
        const val VERSION = 1

        /** The message in [text]; null for one this app does not understand. */
        fun parse(text: String?): BridgeMessage? {
            if (text.isNullOrEmpty()) return null
            val message = try {
                JSONObject(text)
            } catch (e: Exception) {
                return null
            }
            if (message.int("v") != VERSION) return null
            return when (message.string("type")) {
                "page" -> Page(
                    signedIn = message.opt("signedIn") as? Boolean,
                    refreshable = message.opt("refreshable") as? Boolean ?: true
                )
                "theme" -> Theme(
                    ground = parseCssColor(message.string("ground")),
                    grid = parseCssColor(message.string("grid"))
                )
                "words" -> Words(
                    leaveTitle = message.word("leaveTitle"),
                    leaveBody = message.word("leaveBody"),
                    leave = message.word("leave"),
                    stay = message.word("stay"),
                    saveImage = message.word("saveImage"),
                    copyImage = message.word("copyImage"),
                    share = message.word("share"),
                    copyLink = message.word("copyLink"),
                    savedImage = message.word("savedImage"),
                    savedFile = message.word("savedFile"),
                    saveFailed = message.word("saveFailed")
                )
                "haptic" -> message.string("name")?.let { Haptic(it) }
                "pressed" -> Pressed((message.opt("drawing") as? JSONObject)?.let(::pressedDrawing))
                "signIn" -> message.word("nonce")?.let { SignIn(it) }
                "browse" -> message.word("url")?.let { Browse(it) }
                // Something to share is some text; a title alone is the page's to fill in.
                "share" -> message.word("text")?.let { Share(message.string("title") ?: "", it) }
                "download" -> message.word("data")?.let { Download(name = message.string("name") ?: "", data = it) }
                else -> null
            }
        }

        /** Only a drawing the app can fetch itself; the page's own `blob:` images are not. */
        private fun pressedDrawing(drawing: JSONObject): PressedDrawing? {
            val src = drawing.string("src")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: return null
            return PressedDrawing(
                src = src,
                link = drawing.string("link")?.takeIf { it.isNotEmpty() },
                width = drawing.int("width") ?: 0,
                height = drawing.int("height") ?: 0
            )
        }

        // Each field is read as the type it is, never coerced: optString would make 42 a
        // nonce of "42" and a missing field "", and optInt would read "1" as the version.
        // JSON null is JSONObject.NULL, which is none of these types, so it reads as absent.

        private fun JSONObject.string(key: String): String? = opt(key) as? String

        /** A number, cut to a whole one; through a double, as JSON's numbers are. */
        private fun JSONObject.int(key: String): Int? = (opt(key) as? Number)?.toDouble()?.toInt()

        /** A string with something in it; an empty one says nothing. */
        private fun JSONObject.word(key: String): String? = string(key)?.takeIf { it.isNotEmpty() }
    }
}

/**
 * `rgb(r, g, b)` or `rgba(r, g, b, a)`, as `getComputedStyle` gives colours, in either its
 * comma or its space syntax; or a hex colour, as a design-system token reads back as it was
 * written (`#ccccff`, ds.css in oeee-cafe/web). The alpha is left out: the site only reports
 * colours that are not transparent, and what the app draws in them is opaque.
 */
fun parseCssColor(css: String?): Color? {
    val text = css?.trim() ?: return null
    HEX_COLOR.matchEntire(text)?.let { match ->
        val digits = match.groupValues[1]
        // #rgb and #rgba write each channel once; #rrggbb and #rrggbbaa twice.
        val (r, g, b) = if (digits.length <= 4) {
            (0 until 3).map { "${digits[it]}${digits[it]}".toInt(16) }
        } else {
            (0 until 3).map { digits.substring(it * 2, it * 2 + 2).toInt(16) }
        }
        return Color(r, g, b)
    }
    val match = CSS_COLOR.matchEntire(text) ?: return null
    val (r, g, b) = match.destructured.toList().map { it.toFloat().toInt().coerceIn(0, 255) }
    return Color(r, g, b)
}

private val CSS_COLOR = Regex("""rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+).*\)""")
private val HEX_COLOR = Regex("""#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})""")
