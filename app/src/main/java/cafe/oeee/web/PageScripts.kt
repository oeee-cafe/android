package cafe.oeee.web

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the app evaluates in a page of the site: calls on `window.oeeeApp`, which the
 * page makes (app_bridge.jinja in oeee-cafe/web). A page without the part that answers one
 * simply lacks the member, so each asks for it before calling it.
 *
 * Nothing here touches Android but org.json, so the tests check every call against the site's
 * own list of what an app may call (appContract.json in oeee-cafe/web).
 */
object PageScripts {
    /**
     * The reader said Leave in the app's own dialog: the page puts its loading bar up. Word
     * for word what every app evaluates (appContract.json's `scripts.leaving`). It is a
     * promise, which on Chromium settles at once, so the app lets the load go on without it.
     */
    const val LEAVING = "window.oeeeApp && window.oeeeApp.leaving ? window.oeeeApp.leaving() : null"

    /** This device's push token, for the page to register for whoever is signed in. */
    fun pushToken(token: String): String =
        "window.oeeeApp && window.oeeeApp.pushToken && window.oeeeApp.pushToken(${JSONObject.quote(token)});"

    /** How a sign-in went ([GoogleSignInMessages]), as the object the page takes. */
    fun signInAnswer(told: String): String =
        "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.answer($told);"

    /** Back in front, probably from the browser: the page asks the site again at once. */
    const val SIGN_IN_RESUME = "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.resume();"

    /**
     * Back from the browser by the site's own way back, oeee-cafe://handoff/done, which says
     * the handoff's code: the page finishes the sign-in with it. Only a code the site could
     * have made, exactly 64 lowercase hex digits, is handed over. The scheme is anyone's to
     * open, so anything else a link of it says is kept out of the page, which is only asked
     * again, as when the reader comes back any other way or from a site that says no code.
     */
    fun signInResume(code: String?): String =
        if (code != null && HANDOFF_CODE.matches(code)) {
            "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.resume(${JSONObject.quote(code)});"
        } else {
            SIGN_IN_RESUME
        }

    private val HANDOFF_CODE = Regex("^[0-9a-f]{64}$")

    /** The price of each product /supporter asked about, as the store formats it. */
    fun storePrices(prices: Map<String, String>): String =
        "window.oeeeApp && window.oeeeApp.store && window.oeeeApp.store.prices && window.oeeeApp.store.prices(${JSONObject(prices)});"

    /**
     * Purchase tokens, for the page to post to the site (app_store.jinja). The promise it
     * returns is not waited for: the site acknowledges what it takes, so the app has nothing
     * to finish.
     */
    fun storePurchased(tokens: List<String>): String =
        "window.oeeeApp && window.oeeeApp.store && window.oeeeApp.store.purchased && window.oeeeApp.store.purchased(${JSONArray(tokens)});"

    /** A press that ended without anything to hand over: one of [PlayBilling.Outcome]. */
    fun storeEnded(outcome: String): String =
        "window.oeeeApp && window.oeeeApp.store && window.oeeeApp.store.ended && window.oeeeApp.store.ended(${JSONObject.quote(outcome)});"

    /** The browser the page asked for could not be opened. */
    const val SIGN_IN_UNOPENED = "window.oeeeApp && window.oeeeApp.signIn && window.oeeeApp.signIn.unopened();"
}
