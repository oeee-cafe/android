package cafe.oeee.web

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

/**
 * What the app answers a page that asked it to sign in with Google ([GoogleSignIn]), as the
 * object `window.oeeeSignIn.answer` takes (app_sign_in.jinja in oeee-cafe/web): an ID token,
 * a sign-in put away, or one Google would not make. Each is JSON, which the app writes into
 * the call it evaluates, so it is a literal the page reads whatever the token holds.
 *
 * Nothing here touches Android, so it is checked by the JVM's own tests, as [BridgeMessage] is.
 */
object GoogleSignInMessages {
    private val adapter: JsonAdapter<Any> = Moshi.Builder().build().adapter(Any::class.java)

    /** The ID token Credential Manager handed over, for the page to post to the site. */
    fun token(idToken: String): String = adapter.toJson(mapOf("id_token" to idToken))

    /** Put away without signing in, or nothing to sign in with: the page stays as it was. */
    const val CANCELLED = """{"cancelled":true}"""

    /** Google would not say who this is: the site says so, in the page's own words. */
    const val FAILED = """{}"""
}
