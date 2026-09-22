package cafe.oeee.web

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The messages as app_bridge.jinja (oeee-cafe/web) writes them. What matters most is what
 * the app does with a message it does not expect: nothing, rather than something wrong.
 */
class BridgeMessageTest {
    @Test
    fun page() {
        val message = BridgeMessage.parse(
            """{"v":1,"type":"page","path":"/draw","signedIn":true,"presence":"drawing",""" +
                """"community":"오이카페","group":null,"painting":true,"refreshable":false}"""
        )
        assertEquals(
            BridgeMessage.Page(
                path = "/draw",
                signedIn = true,
                presence = "drawing",
                community = "오이카페",
                group = null,
                painting = true,
                refreshable = false
            ),
            message
        )
    }

    @Test
    fun aPageWithoutTheToolbarCannotSayWhoIsSignedIn() {
        val message = BridgeMessage.parse(
            """{"v":1,"type":"page","path":"/","signedIn":null,"presence":null,"painting":false,"refreshable":true}"""
        ) as BridgeMessage.Page
        assertNull(message.signedIn)
        assertEquals(true, message.refreshable)
    }

    @Test
    fun unread() {
        assertEquals(BridgeMessage.Unread(12), BridgeMessage.parse("""{"v":1,"type":"unread","count":12}"""))
        assertEquals(BridgeMessage.Unread(0), BridgeMessage.parse("""{"v":1,"type":"unread","count":-3}"""))
    }

    @Test
    fun theme() {
        assertEquals(
            BridgeMessage.Theme(
                choice = "system",
                dark = true,
                top = Color(34, 34, 63),
                bottom = Color(255, 255, 255)
            ),
            BridgeMessage.parse(
                """{"v":1,"type":"theme","choice":"system","dark":true,""" +
                    """"top":"rgb(34, 34, 63)","bottom":"rgba(255, 255, 255, 0.9)"}"""
            )
        )
        val unknown = BridgeMessage.parse("""{"v":1,"type":"theme","choice":"dark","dark":true,"top":null,"bottom":"red"}""")
        assertEquals(BridgeMessage.Theme(choice = "dark", dark = true, top = null, bottom = null), unknown)
    }

    @Test
    fun haptic() {
        assertEquals(BridgeMessage.Haptic("success"), BridgeMessage.parse("""{"v":1,"type":"haptic","name":"success"}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"haptic"}"""))
    }

    @Test
    fun pressed() {
        assertEquals(
            BridgeMessage.Pressed(
                BridgeMessage.PressedDrawing(
                    src = "https://r2.oeee.cafe/image/a.png",
                    link = "https://oeee.cafe/@reader/9c881320",
                    width = 300,
                    height = 300
                )
            ),
            BridgeMessage.parse(
                """{"v":1,"type":"pressed","drawing":{"src":"https://r2.oeee.cafe/image/a.png",""" +
                    """"link":"https://oeee.cafe/@reader/9c881320","width":300,"height":300}}"""
            )
        )
    }

    @Test
    fun aDrawingOnItsOwnPostPageHasNoLink() {
        val message = BridgeMessage.parse(
            """{"v":1,"type":"pressed","drawing":{"src":"https://r2.oeee.cafe/a.png","link":"","width":1,"height":2}}"""
        ) as BridgeMessage.Pressed
        assertNull(message.drawing?.link)
    }

    @Test
    fun aPressOffADrawingOrOnOneTheAppCannotFetchIsNone() {
        assertEquals(BridgeMessage.Pressed(null), BridgeMessage.parse("""{"v":1,"type":"pressed","drawing":null}"""))
        assertEquals(
            BridgeMessage.Pressed(null),
            BridgeMessage.parse("""{"v":1,"type":"pressed","drawing":{"src":"blob:https://oeee.cafe/1","width":1,"height":1}}""")
        )
    }

    @Test
    fun painter() {
        assertEquals(BridgeMessage.Painter("ready"), BridgeMessage.parse("""{"v":1,"type":"painter","state":"ready"}"""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        assertEquals(
            BridgeMessage.Unread(1),
            BridgeMessage.parse("""{"v":1,"type":"unread","count":1,"invitations":1,"extra":{"a":[1,2]}}""")
        )
    }

    @Test
    fun whatTheAppDoesNotUnderstandIsNothing() {
        assertNull(BridgeMessage.parse(null))
        assertNull(BridgeMessage.parse(""))
        assertNull(BridgeMessage.parse("light"))
        assertNull(BridgeMessage.parse("{not json"))
        assertNull(BridgeMessage.parse("[1,2]"))
        assertNull(BridgeMessage.parse("\"page\""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"someday"}"""))
        assertNull(BridgeMessage.parse("""{"v":1}"""))
        // A later version of the contract may mean something else by the same names.
        assertNull(BridgeMessage.parse("""{"v":2,"type":"unread","count":1}"""))
        assertNull(BridgeMessage.parse("""{"type":"unread","count":1}"""))
    }
}
