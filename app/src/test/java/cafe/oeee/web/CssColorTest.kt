package cafe.oeee.web

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Colours as `getComputedStyle` gives them, which is how the site reports its edges, and as
 * the design system's tokens read back, which is hex.
 */
class CssColorTest {
    @Test
    fun rgb() {
        assertEquals(Color(255, 255, 255), parseCssColor("rgb(255, 255, 255)"))
        assertEquals(Color(34, 34, 63), parseCssColor("  rgb(34,34,63) "))
    }

    @Test
    fun rgbaLeavesTheAlphaOut() {
        assertEquals(Color(187, 187, 255), parseCssColor("rgba(187, 187, 255, 0.5)"))
    }

    @Test
    fun theSpaceSyntaxAndFractions() {
        assertEquals(Color(10, 20, 30), parseCssColor("rgb(10 20 30 / 0.4)"))
        assertEquals(Color(12, 0, 255), parseCssColor("rgb(12.7, 0.2, 255)"))
    }

    @Test
    fun outOfRangeIsClamped() {
        assertEquals(Color(255, 0, 0), parseCssColor("rgb(300, 0, 0)"))
    }

    @Test
    fun hex() {
        assertEquals(Color(204, 204, 255), parseCssColor("#ccccff"))
        assertEquals(Color(23, 23, 43), parseCssColor(" #17172B "))
        assertEquals(Color(187, 187, 255), parseCssColor("#bbf"))
        // The alpha is left out, as rgba's is.
        assertEquals(Color(187, 187, 255), parseCssColor("#bbf8"))
        assertEquals(Color(34, 34, 63), parseCssColor("#22223f80"))
    }

    @Test
    fun anythingElseIsNoColour() {
        assertNull(parseCssColor(null))
        assertNull(parseCssColor(""))
        assertNull(parseCssColor("transparent"))
        assertNull(parseCssColor("#fffff"))
        assertNull(parseCssColor("#gggggg"))
        assertNull(parseCssColor("ccccff"))
        assertNull(parseCssColor("red"))
        assertNull(parseCssColor("rgb(1, 2)"))
    }
}
