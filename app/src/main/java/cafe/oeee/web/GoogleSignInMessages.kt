package cafe.oeee.web

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

/**
 * What the page and the app say to each other while signing in with Google
 * (assets/google-sign-in.js and [GoogleSignIn]): the page sends the nonce it got from the
 * site, and hears back one of an ID token, a sign-in put away, or one Google would not make.
 *
 * Nothing here touches Android, so it is checked by the JVM's own tests, as [BridgeMessage] is.
 */
object GoogleSignInMessages {
    private val adapter: JsonAdapter<Any> = Moshi.Builder().build().adapter(Any::class.java)

    /** The nonce the page asked the site for; null when the message does not carry one. */
    fun nonce(message: String?): String? {
        if (message.isNullOrEmpty()) return null
        val asked = try {
            adapter.fromJson(message) as? Map<*, *>
        } catch (e: Exception) {
            null
        } ?: return null
        return (asked["nonce"] as? String)?.takeIf { it.isNotEmpty() }
    }

    /** The ID token Credential Manager handed over, for the page to post to the site. */
    fun token(idToken: String): String = adapter.toJson(mapOf("id_token" to idToken))

    /** Put away without signing in, or nothing to sign in with: the page stays as it was. */
    const val CANCELLED = """{"cancelled":true}"""

    /** Google would not say who this is: the site says so, in the page's own words. */
    const val FAILED = """{"error":"failed"}"""
}
