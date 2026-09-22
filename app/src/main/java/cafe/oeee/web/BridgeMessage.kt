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
    /** The page shown, sent on every page and again whenever any of it changes. */
    data class Page(
        val path: String?,
        /** Null on a page without the toolbar, which cannot tell. */
        val signedIn: Boolean?,
        val presence: String?,
        val community: String?,
        val group: String?,
        /** Whether leaving would lose a drawing in progress. */
        val painting: Boolean,
        /** Whether pulling down may reload the page; neither a painter nor a replay may be. */
        val refreshable: Boolean
    ) : BridgeMessage

    /** The number on the site's bell: unread notifications and invitations waiting, together. */
    data class Unread(val count: Int) : BridgeMessage

    /** The site's theme, and the colours at the page's two edges, for the bars drawn against them. */
    data class Theme(val choice: String?, val dark: Boolean, val top: Color?, val bottom: Color?) : BridgeMessage

    /** A control that is felt as well as seen: one of the names [hapticFeedback] knows. */
    data class Haptic(val name: String) : BridgeMessage

    /** As a finger lands, the drawing it landed on; null when it is not on one. */
    data class Pressed(val drawing: PressedDrawing?) : BridgeMessage

    /** The painter's state; "ready" once `window.oeeePainter` can be driven. */
    data class Painter(val state: String) : BridgeMessage

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
                    path = message.string("path"),
                    signedIn = message["signedIn"] as? Boolean,
                    presence = message.string("presence"),
                    community = message.string("community"),
                    group = message.string("group"),
                    painting = message["painting"] as? Boolean ?: false,
                    refreshable = message["refreshable"] as? Boolean ?: true
                )
                "unread" -> Unread(((message["count"] as? Number)?.toInt() ?: 0).coerceAtLeast(0))
                "theme" -> Theme(
                    choice = message.string("choice"),
                    dark = message["dark"] as? Boolean ?: false,
                    top = parseCssColor(message.string("top")),
                    bottom = parseCssColor(message.string("bottom"))
                )
                "haptic" -> message.string("name")?.let { Haptic(it) }
                "pressed" -> Pressed((message["drawing"] as? Map<*, *>)?.let(::pressedDrawing))
                "painter" -> message.string("state")?.let { Painter(it) }
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
    }
}

/**
 * `rgb(r, g, b)` or `rgba(r, g, b, a)`, as `getComputedStyle` gives colours, in either its
 * comma or its space syntax. The alpha is left out: the site only reports colours that are
 * not transparent, and the bars drawn in them are opaque.
 */
fun parseCssColor(css: String?): Color? {
    val match = CSS_COLOR.matchEntire(css?.trim() ?: return null) ?: return null
    val (r, g, b) = match.destructured.toList().map { it.toFloat().toInt().coerceIn(0, 255) }
    return Color(r, g, b)
}

private val CSS_COLOR = Regex("""rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+).*\)""")
