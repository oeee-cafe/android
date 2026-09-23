package cafe.oeee.web

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import cafe.oeee.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Sign in with Google, natively, for the site in a web view.
 *
 * The site's "Sign in with Google" is a link to `/auth/google`, which in a browser goes to
 * Google's page and back. Google refuses those pages inside an embedded web view
 * (`disallowed_useragent`), so the tab stops the link ([isSignInLink]) and asks Credential
 * Manager here instead.
 *
 * The page does the rest (assets/google-sign-in.js): it asks the site for this sign-in's
 * state and nonce, hands the nonce over through [OBJECT_NAME], and posts the ID token this
 * answers with to `/auth/google`. So everything the site is asked carries the page's cookie
 * and origin, and the app never holds the session itself.
 *
 * Credential Manager is given the site's own Web application OAuth client id as its server
 * client id, so the token comes back made for the site, which is the audience the site
 * checks (src/google.rs in oeee-cafe/web).
 */
class GoogleSignIn(
    private val activity: Activity,
    private val webView: WebView,
    siteOrigin: String,
    script: String,
    private val scope: CoroutineScope
) {
    init {
        // The page's half, in every page of the site before its own scripts run.
        WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(siteOrigin))
        WebViewCompat.addWebMessageListener(webView, OBJECT_NAME, setOf(siteOrigin)) {
                _: WebView, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean,
                reply: JavaScriptReplyProxy ->
            if (!isMainFrame || SiteBridge.origin(sourceOrigin) != siteOrigin) {
                Log.w(TAG, "Ignored $OBJECT_NAME from ${if (isMainFrame) sourceOrigin else "a frame"}")
                return@addWebMessageListener
            }
            val nonce = GoogleSignInMessages.nonce(message.data)
            if (nonce == null) {
                Log.w(TAG, "A sign-in was asked for without a nonce")
                reply.postMessage(GoogleSignInMessages.CANCELLED)
                return@addWebMessageListener
            }
            scope.launch { ask(nonce, reply) }
        }
    }

    /** Stops the link and starts the sign-in the page will carry, going on to `next`. */
    fun begin(url: Uri) {
        val next = url.getQueryParameter("next")
        val argument = if (next == null) "null" else JSONObject.quote(next)
        webView.evaluateJavascript(
            "window.oeeeGoogleAuth && window.oeeeGoogleAuth.begin($argument);",
            null
        )
    }

    /** Google's own sheet, with the page's nonce; the answer goes straight back to it. */
    private suspend fun ask(nonce: String, reply: JavaScriptReplyProxy) {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        Log.i(TAG, "Asking Credential Manager to sign in")
        // Nothing below logs the answer itself: it carries the ID token, and logcat is
        // readable by anything with the right permission on some devices.
        val answer = try {
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            val token = if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
            if (token == null) {
                Log.w(TAG, "Credential Manager answered with no Google ID token")
                GoogleSignInMessages.FAILED
            } else {
                GoogleSignInMessages.token(token)
            }
        } catch (e: GetCredentialCancellationException) {
            // Put away without signing in: the page stays as it was, and says nothing,
            // because nothing went wrong.
            //
            // Google reports a sign-in its own sheet refused this way too -- an OAuth
            // client that does not match this build's package and signing certificate
            // comes back as a cancellation, not as an error -- so it is logged even
            // though it is silent on screen. Without this line a misconfigured console
            // is indistinguishable from a finger on the back gesture.
            Log.i(TAG, "Credential Manager was dismissed without a token")
            GoogleSignInMessages.CANCELLED
        } catch (e: NoCredentialException) {
            // No Google account on the device to offer. Nothing went wrong and there is
            // nothing to sign in with, so the page is left as it was rather than told the
            // site could not confirm who this is.
            Log.i(TAG, "No Google account to sign in with")
            GoogleSignInMessages.CANCELLED
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google did not sign in - ${e::class.java.simpleName}: ${e.message}")
            GoogleSignInMessages.FAILED
        } catch (e: Throwable) {
            // Anything else at all. A sign-in that ends without an answer leaves the page
            // waiting on a reply that never comes, with nothing on screen to say so --
            // the one outcome worse than saying it went wrong.
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Google sign-in ended unexpectedly - ${e::class.java.simpleName}: ${e.message}")
            GoogleSignInMessages.FAILED
        }
        reply.postMessage(answer)
    }

    companion object {
        private const val TAG = "GoogleSignIn"

        /** What the page posts the nonce to, and hears the answer on. */
        const val OBJECT_NAME = "oeeeGoogleSignIn"

                /**
         * Whether this build can sign in with Google at all: one built without the site's
         * client id cannot, and neither can a web view too old for the two halves of the
         * bridge this needs.
         */
        /**
         * Whether this build and this web view can do it at all: a web view without the
         * two halves of the bridge cannot, and Android System WebView updates apart from
         * the app, so the same build can differ from one device to the next.
         */
        fun isAvailable(): Boolean =
            BuildConfig.GOOGLE_SERVER_CLIENT_ID.isNotEmpty() &&
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

        /** Whether [request] is the site's link to sign in with Google. */
        fun isSignInLink(request: WebResourceRequest, navigation: Navigation): Boolean =
            request.isForMainFrame &&
                request.method.equals("GET", ignoreCase = true) &&
                navigation.isSiteUrl(request.url) &&
                request.url.path == "/auth/google"
    }
}
