package cafe.oeee.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The messages app_bridge.jinja (oeee-cafe-web) really sends, captured from its script running
 * in Chromium over pages shaped like the site's: a feed signed in, a collaborative painter in
 * the dark theme, a replay signed out, and a page with no toolbar at all. The hand-written
 * cases in BridgeMessageTest say what the parser should do; these say that it does it for what
 * arrives. Recapture them when the site's script changes.
 */
class SiteMessagesTest {
    private val messages: List<String> =
        javaClass.classLoader!!.getResource("bridge-messages.jsonl")!!.readText().lines().filter { it.isNotBlank() }

    @Test
    fun everyMessageTheSiteSendsIsUnderstood() {
        for (text in messages) assertNotNull(text, BridgeMessage.parse(text))
    }

    @Test
    fun whatTheSiteSendsMeansWhatItShould() {
        val parsed = messages.map { BridgeMessage.parse(it)!! }
        val pages = parsed.filterIsInstance<BridgeMessage.Page>()
        assertEquals(listOf(true, true, false, null), pages.map { it.signedIn })
        assertEquals(listOf(true, false, false, true), pages.map { it.refreshable })
        assertEquals(listOf(false, true, false, false), pages.map { it.painting })
        assertEquals(listOf(12, 0), parsed.filterIsInstance<BridgeMessage.Unread>().map { it.count })

        val pressed = parsed.filterIsInstance<BridgeMessage.Pressed>().single().drawing!!
        assertEquals("https://oeee.cafe/@artist/9c881320-2b43-4afa-b2bb-7128c8a3e985", pressed.link)

        // A page where nothing has a colour says so, rather than sending transparent black.
        val bare = parsed.filterIsInstance<BridgeMessage.Theme>().last()
        assertNull(bare.top)
        assertNull(bare.bottom)
        assertNotNull(parsed.filterIsInstance<BridgeMessage.Theme>().first().top)
    }
}
