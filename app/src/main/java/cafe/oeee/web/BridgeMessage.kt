package cafe.oeee.web

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

/**
 * What the site tells the app over `window.oeeeBridge`, as app_bridge.jinja in oeee-cafe/web
 * describes it: one JSON string per message, `{v: 1, type, ...}`. Types and fields this app
 * does not know are ignored, so the site can add either without a release of the app.
 *
 * Nothing here touches Android, so the parsing is checked by the JVM's own tests.
 */
sealed interface BridgeMessage {
    /**
     * The page shown, sent on every page and again whenever any of it changes. Only what this
     * app acts on is read; the site says more, for the other apps.
     */
    data class Page(
        /** Null on a page without the toolbar, which cannot tell. */
        val signedIn: Boolean?,
        /** Whether leaving would lose a drawing in progress. */
        val painting: Boolean,
        /** Whether pulling down may reload the page; neither a painter nor a replay may be. */
        val refreshable: Boolean
    ) : BridgeMessage

    /** The number on the site's bell: unread notifications and invitations waiting, together. */
    data class Unread(val count: Int) : BridgeMessage

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

    /** The painter's state; "ready" once `window.oeeeApp.painter` can be driven. */
    data class Painter(val state: String) : BridgeMessage

    /**
     * A sign-in the page is carrying, after the app stopped its link (app_sign_in.jinja): make
     * an ID token for [nonce] with the platform's own sheet, and answer with
     * `window.oeeeApp.signIn.answer`.
     */
    data class SignIn(val provider: String, val nonce: String) : BridgeMessage

    /**
     * The same, handed to the system's browser: open [url], which the page says is the site's
     * own. The app checks that for itself before opening anything.
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

        private val adapter: JsonAdapter<Any> = Moshi.Builder().build().adapter(Any::class.java)

        /** The message in [text]; null for one this app does not understand. */
        fun parse(text: String?): BridgeMessage? {
            if (text.isNullOrEmpty()) return null
            val message = try {
                adapter.fromJson(text) as? Map<*, *>
            } catch (e: Exception) {
                null
            } ?: return null
            if ((message["v"] as? Number)?.toInt() != VERSION) return null
            return when (message["type"]) {
                "page" -> Page(
                    signedIn = message["signedIn"] as? Boolean,
                    painting = message["painting"] as? Boolean ?: false,
                    refreshable = message["refreshable"] as? Boolean ?: true
                )
                "unread" -> Unread(((message["count"] as? Number)?.toInt() ?: 0).coerceAtLeast(0))
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
                "pressed" -> Pressed((message["drawing"] as? Map<*, *>)?.let(::pressedDrawing))
                "painter" -> message.string("state")?.let { Painter(it) }
                "signIn" -> {
                    val provider = message.word("provider") ?: return null
                    val nonce = message.word("nonce") ?: return null
                    SignIn(provider, nonce)
                }
                "browse" -> message.word("url")?.let { Browse(it) }
                // Something to share is some text; a title alone is the page's to fill in.
                "share" -> message.word("text")?.let { Share(message.string("title") ?: "", it) }
                "download" -> message.word("data")?.let { Download(name = message.string("name") ?: "", data = it) }
                else -> null
            }
        }

        /** Only a drawing the app can fetch itself; the page's own `blob:` images are not. */
        private fun pressedDrawing(drawing: Map<*, *>): PressedDrawing? {
            val src = drawing.string("src")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: return null
            return PressedDrawing(
                src = src,
                link = drawing.string("link")?.takeIf { it.isNotEmpty() },
                width = (drawing["width"] as? Number)?.toInt() ?: 0,
                height = (drawing["height"] as? Number)?.toInt() ?: 0
            )
        }

        private fun Map<*, *>.string(key: String): String? = this[key] as? String

        /** A string with something in it; an empty one says nothing. */
        private fun Map<*, *>.word(key: String): String? = string(key)?.takeIf { it.isNotEmpty() }
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
