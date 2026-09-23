package cafe.oeee.web

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

/**
 * Everything the app evaluates in a page of the site: calls on `window.oeeeApp`, which the
 * page makes (app_bridge.jinja in oeee-cafe/web). A page without the part that answers one
 * simply lacks the member, so each asks for it before calling it.
 *
 * Nothing here touches Android, so the tests check every call against the site's own list of
 * what an app may call (appContract.json in oeee-cafe/web).
 */
object PageScripts {
    private val string: JsonAdapter<String> = Moshi.Builder().build().adapter(String::class.java)

    /**
     * The reader said Leave in the app's own dialog: the page puts its loading bar up. Word
     * for word what every app evaluates (appContract.json's `scripts.leaving`). It is a
     * promise, which on Chromium settles at once, so the app lets the load go on without it.
     */
    const val LEAVING = "window.oeeeApp && window.oeeeApp.leaving ? window.oeeeApp.leaving() : null"

    /** This device's push token, for the page to register for whoever is signed in. */
    fun pushToken(token: String): String =
        "window.oeeeApp && window.oeeeApp.pushToken && window.oeeeApp.pushToken(${string.toJson(token)});"

    /** How a sign-in went ([GoogleSignInMessages]), as the object the page takes. */
    fun signInAnswer(told: String): String =
        "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.answer($told);"

    /** Back in front, probably from the browser: the page asks the site again at once. */
    const val SIGN_IN_RESUME = "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.resume();"

    /** The browser the page asked for could not be opened. */
    const val SIGN_IN_UNOPENED = "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.unopened();"
}
