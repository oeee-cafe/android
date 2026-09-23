package cafe.oeee.web

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the app answers a page that asked it to sign in with Google, as
 * `window.oeeeSignIn.answer` takes it (app_sign_in.jinja). The answer is written into the
 * script the app evaluates, so it has to be JSON whatever the token holds -- a JWT is dots
 * and base64url, but nothing here should depend on that.
 */
class GoogleSignInMessagesTest {
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    private fun read(json: String): Map<*, *>? = adapter.fromJson(json) as? Map<*, *>

    @Test
    fun theTokenTheAppAnswersWith() {
        assertEquals("""{"id_token":"a.b.c"}""", GoogleSignInMessages.token("a.b.c"))
        // Whatever the token holds, the page gets a literal that reads back as it.
        val awkward = "a\"b\\c\nd\u2028e"
        assertEquals(mapOf("id_token" to awkward), read(GoogleSignInMessages.token(awkward)))
    }

    @Test
    fun theAnswersThatAreNotATokenAreJson() {
        assertEquals(mapOf("cancelled" to true), read(GoogleSignInMessages.CANCELLED))
        // One that could not sign in says nothing more, and the site words it.
        assertEquals(emptyMap<String, Any>(), read(GoogleSignInMessages.FAILED))
    }
}
