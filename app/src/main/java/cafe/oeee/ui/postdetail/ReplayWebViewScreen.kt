package cafe.oeee.ui.postdetail

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import cafe.oeee.R
import cafe.oeee.data.remote.ApiClient
import cafe.oeee.data.remote.PersistentCookieStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayWebViewScreen(postId: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.replay_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val baseUrl = ApiClient.getBaseUrl()
        val baseUrlHost = try {
            java.net.URI(baseUrl).host ?: "oeee.cafe"
        } catch (e: Exception) {
            "oeee.cafe"
        }
        val url = "$baseUrl/posts/$postId/replay/mobile"

        AndroidView(
            factory = {
                WebView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    // Sync cookies from ApiClient's cookie store to WebView
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    // Get cookies from the app's persistent cookie store and set them in WebView
                    try {
                        val appContext = context.applicationContext
                        val uri = java.net.URI(baseUrl)
                        val cookieStore = PersistentCookieStore(appContext)
                        val cookies = cookieStore.get(uri)

                        android.util.Log.d("ReplayWebView", "Found ${cookies.size} cookies for $baseUrl")

                        cookies.forEach { cookie ->
                            val cookieString = "${cookie.name}=${cookie.value}; domain=${cookie.domain ?: ".$baseUrlHost"}; path=${cookie.path}"
                            cookieManager.setCookie(baseUrl, cookieString)
                            android.util.Log.d("ReplayWebView", "Set cookie: ${cookie.name}=${cookie.value}")
                        }

                        // Flush cookies to ensure they're saved
                        cookieManager.flush()
                        android.util.Log.d("ReplayWebView", "Cookies flushed successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("ReplayWebView", "Error syncing cookies: ${e.message}", e)
                    }

                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        )
    }
}
