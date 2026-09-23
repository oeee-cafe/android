package cafe.oeee.web

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import cafe.oeee.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sign in with Google, natively, for the site in a web view.
 *
 * The site's "Sign in with Google" goes to Google's page in a browser, which Google refuses
 * inside an embedded web view (`disallowed_useragent`). In this app the page takes the press
 * itself (app_sign_in.jinja in oeee-cafe/web): it asks the site for this sign-in's state and
 * nonce, hands the nonce over on the bridge (BridgeMessage.SignIn), and posts the ID token this
 * answers with, from Credential Manager, to the site. So
 * everything the site is asked carries the page's cookie and origin, and the app never
 * holds the session itself.
 *
 * Credential Manager is given the site's own Web application OAuth client id as its server
 * client id, so the token comes back made for the site, which is the audience the site
 * checks (src/google.rs in oeee-cafe/web).
 */
class GoogleSignIn(
    private val activity: Activity,
    private val webView: WebView,
    private val siteOrigin: String,
    private val scope: CoroutineScope
) {
    /**
     * The page's `signIn`: Google's own sheet, with the page's nonce. A build without the
     * site's client id has no sheet to show, and says so the way a sign-in Google refused
     * does, so the page is not left waiting on one that is never coming.
     */
    fun signIn(nonce: String) {
        if (BuildConfig.GOOGLE_SERVER_CLIENT_ID.isEmpty()) {
            Log.w(TAG, "Built without oeeeGoogleServerClientId; cannot sign in with Google")
            answer(GoogleSignInMessages.FAILED)
            return
        }
        scope.launch { answer(ask(nonce)) }
    }

    /**
     * The page is told how it went, as JSON it takes as an object literal. The token is in
     * the script, so the script is never logged either, and it is only ever evaluated in a
     * page of the site: the sheet can be up for a while, and the web view is not bound to
     * still be where it was when it asked.
     */
    private fun answer(told: String) {
        val here = webView.url?.let { SiteBridge.origin(Uri.parse(it)) }
        if (here != siteOrigin) {
            Log.w(TAG, "The page that asked to sign in is gone; not answering")
            return
        }
        webView.evaluateJavascript(PageScripts.signInAnswer(told), null)
    }

    /** What Credential Manager said, as the page is to be told it (GoogleSignInMessages). */
    private suspend fun ask(nonce: String): String {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        Log.i(TAG, "Asking Credential Manager to sign in")
        // Nothing below logs the answer itself: it carries the ID token, and logcat is
        // readable by anything with the right permission on some devices.
        return try {
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
    }

    private companion object {
        const val TAG = "GoogleSignIn"
    }
}
