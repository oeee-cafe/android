package cafe.oeee.ui.draw

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import cafe.oeee.R
import cafe.oeee.data.remote.ApiClient
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawWebViewScreen(
    width: Int = 300,
    height: Int = 300,
    tool: String = "neo",
    communityId: String? = null,
    parentPostId: String? = null,
    onNavigateBack: () -> Unit,
    onDrawingComplete: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            DrawWebView(
                context = context,
                width = width,
                height = height,
                tool = tool,
                communityId = communityId,
                parentPostId = parentPostId,
                onLoadingChanged = { isLoading = it },
                onDrawingComplete = onDrawingComplete,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            )

            if (isLoading) {
                CircularProgressIndicator()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DrawWebView(
    context: Context,
    width: Int,
    height: Int,
    tool: String,
    communityId: String?,
    parentPostId: String?,
    onLoadingChanged: (Boolean) -> Unit,
    onDrawingComplete: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseUrl = ApiClient.getBaseUrl()
    val baseUrlHost = try {
        java.net.URI(baseUrl).host ?: "oeee.cafe"
    } catch (e: Exception) {
        "oeee.cafe"
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                val webView = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Enable debugging
                WebView.setWebContentsDebuggingEnabled(true)

                // Sync cookies from ApiClient's cookie store to WebView
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                // Get cookies from the app's persistent cookie store and set them in WebView
                try {
                    val appContext = context.applicationContext
                    val uri = java.net.URI(baseUrl)
                    val cookieStore = cafe.oeee.data.remote.PersistentCookieStore(appContext)
                    val cookies = cookieStore.get(uri)

                    android.util.Log.d("DrawWebView", "Found ${cookies.size} cookies for $baseUrl")

                    cookies.forEach { cookie ->
                        val cookieString = "${cookie.name}=${cookie.value}; domain=${cookie.domain ?: ".$baseUrlHost"}; path=${cookie.path}"
                        cookieManager.setCookie(baseUrl, cookieString)
                        android.util.Log.d("DrawWebView", "Set cookie: ${cookie.name}=${cookie.value}")
                    }

                    // Flush cookies to ensure they're saved
                    cookieManager.flush()
                    android.util.Log.d("DrawWebView", "Cookies flushed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("DrawWebView", "Error syncing cookies: ${e.message}", e)
                }

                // Add JavaScript interface for native bridge
                addJavascriptInterface(
                    WebAppInterface(context, webView, onDrawingComplete),
                    "OeeeCafe"
                )

                // Add console logging
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d("DrawWebView", "${message.message()} -- From line ${message.lineNumber()} of ${message.sourceId()}")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        android.util.Log.e("DrawWebView", "WebView error: ${error?.description} for ${request?.url}")
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        android.util.Log.d("DrawWebView", "Page started: $url")
                        onLoadingChanged(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        android.util.Log.d("DrawWebView", "Page finished: $url")
                        onLoadingChanged(false)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val url = request?.url
                        android.util.Log.d("DrawWebView", "Navigating to: $url")

                        // Check if this is an external link (not the configured base URL domain)
                        if (url != null && url.host != null && !url.host!!.contains(baseUrlHost)) {
                            // Open in external browser
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                            context.startActivity(intent)
                            return true // Prevent WebView from loading the URL
                        }

                        return false // Allow navigation within the configured domain
                    }
                }

                // Use postUrl instead of loadDataWithBaseURL for proper POST with cookies
                var postData = "width=$width&height=$height&tool=$tool"
                if (communityId != null) {
                    postData += "&community_id=$communityId"
                }
                if (parentPostId != null) {
                    postData += "&parent_post_id=$parentPostId"
                }
                android.util.Log.d("DrawWebView", "Posting to $baseUrl/draw/mobile with data: $postData")
                postUrl("$baseUrl/draw/mobile", postData.toByteArray())
            }
        },
        modifier = modifier
    )
}

class WebAppInterface(
    private val context: Context,
    private val webView: WebView,
    private val onDrawingComplete: (String, String, String) -> Unit
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")

            if (type == "drawing_complete") {
                val postId = json.optString("postId")
                val communityId = json.optString("communityId")
                val imageUrl = json.optString("imageUrl")

                android.util.Log.d("DrawWebView", "Drawing complete: postId=$postId, communityId=$communityId, imageUrl=$imageUrl")

                // Clear the drawing session
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    webView.evaluateJavascript("Neo.painter.clearSession();") { result ->
                        android.util.Log.d("DrawWebView", "clearSession() result: $result")
                    }

                    // Call completion callback
                    onDrawingComplete(postId, communityId, imageUrl)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DrawWebView", "Error parsing message", e)
        }
    }
}
