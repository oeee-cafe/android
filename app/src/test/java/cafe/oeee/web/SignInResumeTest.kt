package cafe.oeee.web

import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the page is asked when the reader is back from signing in in a browser. The code
 * oeee-cafe://handoff/done says is written into the script, and that scheme is anyone's to
 * open, so only a code of the site's own shape goes in; anything else asks with nothing.
 */
class SignInResumeTest {
    private val code = "0123456789abcdef".repeat(4)

    private fun argument(script: String): Any =
        JSONTokener(script.substringAfter("resume(").substringBeforeLast(");")).nextValue()

    @Test
    fun aCodeOfTheSitesShapeIsHandedOver() {
        val script = PageScripts.signInResume(code)
        assertEquals(
            "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.resume(\"$code\");",
            script
        )
        assertEquals(code, argument(script))
    }

    @Test
    fun noCodeAsksWithNothing() {
        assertEquals(PageScripts.SIGN_IN_RESUME, PageScripts.signInResume(null))
        assertEquals(PageScripts.SIGN_IN_RESUME, PageScripts.signInResume(""))
    }

    @Test
    fun anythingElseAsksWithNothing() {
        val others = listOf(
            code.uppercase(),
            code.dropLast(1),
            code + "0",
            code.dropLast(1) + "g",
            " $code",
            "$code\n",
            "\n$code",
            code.dropLast(2) + "\");alert(1);//",
            code.dropLast(1) + "٠"
        )
        for (other in others) {
            assertEquals(other, PageScripts.SIGN_IN_RESUME, PageScripts.signInResume(other))
        }
    }
}
