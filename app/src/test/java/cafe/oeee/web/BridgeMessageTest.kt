package cafe.oeee.web

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The messages as app_bridge.jinja (oeee-cafe/web) could write them, beyond the examples in
 * its contract, which AppContractTest reads every one of. What matters most is what the app
 * does with a message it does not expect: nothing, rather than something wrong.
 */
class BridgeMessageTest {
    @Test
    fun themeGroundAndGrid() {
        // The design system's tokens read back as they were written, which is hex.
        val message = BridgeMessage.parse(
            """{"v":1,"type":"theme","choice":"light","ground":"#ccccff","grid":" #bbf "}"""
        ) as BridgeMessage.Theme
        assertEquals(Color(204, 204, 255), message.ground)
        assertEquals(Color(187, 187, 255), message.grid)
        assertEquals("light", message.choice)
        // A page without the design system's stylesheet has neither, and a build of the site
        // from before them says nothing at all.
        val none = BridgeMessage.parse(
            """{"v":1,"type":"theme","choice":"dark","ground":null,"grid":""}"""
        ) as BridgeMessage.Theme
        assertNull(none.ground)
        assertNull(none.grid)
        val older = BridgeMessage.parse("""{"v":1,"type":"theme","choice":"dark","dark":true}""") as BridgeMessage.Theme
        assertNull(older.ground)
    }

    @Test
    fun theSystemsLookIsNoChoice() {
        // "system" leaves it to the device, and so does anything the app does not know.
        for (choice in listOf("\"system\"", "\"sepia\"", "null", "1")) {
            val message = BridgeMessage.parse(
                """{"v":1,"type":"theme","choice":$choice,"ground":"#17172b"}"""
            ) as BridgeMessage.Theme
            assertNull(choice, message.choice)
        }
        val dark = BridgeMessage.parse("""{"v":1,"type":"theme","choice":"dark"}""") as BridgeMessage.Theme
        assertEquals("dark", dark.choice)
    }

    @Test
    fun wordsThePageLeftOutAreTheAppsOwn() {
        val message = BridgeMessage.parse("""{"v":1,"type":"words","leave":"Leave","stay":"","share":null}""") as BridgeMessage.Words
        assertEquals("Leave", message.leave)
        assertNull(message.stay)
        assertNull(message.share)
        assertNull(message.saveImage)
    }

    @Test
    fun aSignInWithNoNonceIsNone() {
        // Nothing to make a token for: a nonce that says nothing, or one that is not a string.
        assertNull(BridgeMessage.parse("""{"v":1,"type":"signIn","provider":"google","nonce":""}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"signIn","provider":"google","nonce":42}"""))
    }

    @Test
    fun aBrowseWithNoUrlIsNone() {
        assertNull(BridgeMessage.parse("""{"v":1,"type":"browse","url":""}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"browse"}"""))
    }

    @Test
    fun aShareIsSomeText() {
        assertEquals(
            BridgeMessage.Share(title = "", text = "https://oeee.cafe/"),
            BridgeMessage.parse("""{"v":1,"type":"share","text":"https://oeee.cafe/"}""")
        )
        // Nothing to share.
        assertNull(BridgeMessage.parse("""{"v":1,"type":"share","title":"A drawing","text":""}"""))
    }

    @Test
    fun aDownloadIsItsData() {
        // A file with no name still has its data URL to say what it is.
        assertEquals(
            BridgeMessage.Download(name = "", data = "data:text/plain,hi"),
            BridgeMessage.parse("""{"v":1,"type":"download","data":"data:text/plain,hi"}""")
        )
        assertNull(BridgeMessage.parse("""{"v":1,"type":"download","name":"drawing.png"}"""))
    }

    @Test
    fun aHapticWithNoNameIsNone() {
        assertNull(BridgeMessage.parse("""{"v":1,"type":"haptic"}"""))
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
    fun unknownFieldsAreIgnored() {
        assertEquals(
            BridgeMessage.Haptic("light"),
            BridgeMessage.parse("""{"v":1,"type":"haptic","name":"light","strength":1,"extra":{"a":[1,2]}}""")
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
        assertNull(BridgeMessage.parse("""{"v":2,"type":"haptic","name":"light"}"""))
        assertNull(BridgeMessage.parse("""{"type":"haptic","name":"light"}"""))
    }
}
