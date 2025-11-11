package cafe.oeee.ui.bannermanagement

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import cafe.oeee.BuildConfig
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
fun BannerDrawWebViewScreen(
    onNavigateBack: () -> Unit,
    onBannerComplete: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Draw Banner") },
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
            BannerDrawWebView(
                context = context,
                onLoadingChanged = { isLoading = it },
                onBannerComplete = onBannerComplete,
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
fun BannerDrawWebView(
    context: Context,
    onLoadingChanged: (Boolean) -> Unit,
    onBannerComplete: (String, String) -> Unit,
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
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

                // Sync cookies
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                try {
                    val appContext = context.applicationContext
                    val uri = java.net.URI(baseUrl)
                    val cookieStore = cafe.oeee.data.remote.PersistentCookieStore(appContext)
                    val cookies = cookieStore.get(uri)

                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("BannerDrawWebView", "Found ${cookies.size} cookies for $baseUrl")
                    }

                    cookies.forEach { cookie ->
                        val cookieString = "${cookie.name}=${cookie.value}; domain=${cookie.domain ?: ".$baseUrlHost"}; path=${cookie.path}"
                        cookieManager.setCookie(baseUrl, cookieString)
                        if (BuildConfig.DEBUG) {
                            android.util.Log.d("BannerDrawWebView", "Set cookie: ${cookie.name}=${cookie.value}")
                        }
                    }

                    cookieManager.flush()
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.e("BannerDrawWebView", "Error syncing cookies: ${e.message}", e)
                    }
                }

                // Add JavaScript interface for native bridge
                addJavascriptInterface(
                    BannerWebAppInterface(context, webView, onBannerComplete),
                    "OeeeCafe"
                )

                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.d("BannerDrawWebView", "${message.message()} -- From line ${message.lineNumber()} of ${message.sourceId()}")
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.e("BannerDrawWebView", "WebView error: ${error?.description} for ${request?.url}")
                        }
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.d("BannerDrawWebView", "Page started: $url")
                        }
                        onLoadingChanged(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.d("BannerDrawWebView", "Page finished: $url")
                        }
                        onLoadingChanged(false)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val url = request?.url
                        if (BuildConfig.DEBUG) {
                            android.util.Log.d("BannerDrawWebView", "Navigating to: $url")
                        }

                        if (url != null && url.host != null && !url.host!!.contains(baseUrlHost)) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                            context.startActivity(intent)
                            return true
                        }

                        return false
                    }
                }

                // Load banner drawing page
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("BannerDrawWebView", "Loading $baseUrl/banners/draw/mobile")
                }
                loadUrl("$baseUrl/banners/draw/mobile")
            }
        },
        modifier = modifier
    )
}

class BannerWebAppInterface(
    private val context: Context,
    private val webView: WebView,
    private val onBannerComplete: (String, String) -> Unit
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type")

            if (type == "banner_complete") {
                val bannerId = json.optString("bannerId")
                val imageUrl = json.optString("imageUrl")

                if (BuildConfig.DEBUG) {
                    android.util.Log.d("BannerDrawWebView", "Banner complete: bannerId=$bannerId, imageUrl=$imageUrl")
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    webView.evaluateJavascript(
                        "if (typeof Neo !== 'undefined' && Neo.painter) { Neo.painter.clearSession(); }"
                    ) { result ->
                        if (BuildConfig.DEBUG) {
                            android.util.Log.d("BannerDrawWebView", "clearSession() result: $result")
                        }
                    }

                    onBannerComplete(bannerId, imageUrl)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.e("BannerDrawWebView", "Error parsing message", e)
            }
        }
    }
}
