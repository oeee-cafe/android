package cafe.oeee.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the page says to the app while signing in with Google, and what it hears back
 * (assets/google-sign-in.js). The page reads the answer with JSON.parse, so a token has to
 * come back as JSON whatever is in it -- a JWT is dots and base64url, but nothing here
 * should depend on that.
 */
class GoogleSignInMessagesTest {
    @Test
    fun theNonceThePageAsksWith() {
        assertEquals("abc123", GoogleSignInMessages.nonce("""{"nonce":"abc123"}"""))
        // Nothing to sign in with: a message without a nonce, or not a message at all.
        assertNull(GoogleSignInMessages.nonce("""{"nonce":""}"""))
        assertNull(GoogleSignInMessages.nonce("""{"nonce":null}"""))
        assertNull(GoogleSignInMessages.nonce("""{"nonce":42}"""))
        assertNull(GoogleSignInMessages.nonce("{}"))
        assertNull(GoogleSignInMessages.nonce("not json"))
        assertNull(GoogleSignInMessages.nonce(""))
        assertNull(GoogleSignInMessages.nonce(null))
    }

    @Test
    fun theTokenTheAppAnswersWith() {
        assertEquals("""{"id_token":"a.b.c"}""", GoogleSignInMessages.token("a.b.c"))
        // Whatever the token holds, the page gets JSON it can parse back to it.
        val awkward = "a\"b\\c\nd"
        val answered = GoogleSignInMessages.token(awkward)
        assertEquals(awkward, GoogleSignInMessages.nonce(answered.replace("id_token", "nonce")))
    }

    @Test
    fun theAnswersThatAreNotATokenAreJson() {
        assertEquals(true, GoogleSignInMessages.nonce(GoogleSignInMessages.CANCELLED) == null)
        assertEquals(
            "failed",
            GoogleSignInMessages.nonce(GoogleSignInMessages.FAILED.replace("error", "nonce"))
        )
    }
}
