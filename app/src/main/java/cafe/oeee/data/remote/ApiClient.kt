package cafe.oeee.data.remote

import android.content.Context
import cafe.oeee.data.model.notification.NotificationTypeAdapter
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// Custom CookieJar implementation using Java's CookieManager
class JavaNetCookieJar(private val cookieManager: CookieManager) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val uri = url.toUri()
        android.util.Log.d("JavaNetCookieJar", "saveFromResponse: url=$url, uri=$uri, cookies.size=${cookies.size}")
        cookies.forEach { cookie ->
            val httpCookie = HttpCookie(cookie.name, cookie.value).apply {
                // When server doesn't specify domain, default to request host
                // This is required for proper cookie matching in subsequent requests
                domain = cookie.domain ?: url.host
                path = cookie.path ?: "/"
                secure = cookie.secure
                maxAge = if (cookie.persistent) {
                    ((cookie.expiresAt - System.currentTimeMillis()) / 1000L)
                } else {
                    -1L
                }
            }
            android.util.Log.d("JavaNetCookieJar", "Saving cookie: name=${httpCookie.name}, domain=${httpCookie.domain}, path=${httpCookie.path}, maxAge=${httpCookie.maxAge}")
            cookieManager.cookieStore.add(uri, httpCookie)
        }
        // Log all cookies in store after save
        val allCookies = cookieManager.cookieStore.cookies
        android.util.Log.d("JavaNetCookieJar", "Total cookies in store after save: ${allCookies.size}")
        allCookies.forEach { c ->
            android.util.Log.d("JavaNetCookieJar", "  - ${c.name}=${c.value} (domain=${c.domain}, path=${c.path})")
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val uri = url.toUri()
        android.util.Log.d("JavaNetCookieJar", "loadForRequest: url=$url, uri=$uri")
        val httpCookies = cookieManager.cookieStore.get(uri)
        android.util.Log.d("JavaNetCookieJar", "Found ${httpCookies.size} cookies for request")
        httpCookies.forEach { c ->
            android.util.Log.d("JavaNetCookieJar", "  Loading: ${c.name}=${c.value} (domain=${c.domain}, path=${c.path})")
        }
        // Filter out cookies with empty values (deleted/expired cookies)
        val result = httpCookies
            .filter { it.value.isNotEmpty() }
            .mapNotNull { httpCookie ->
                Cookie.Builder()
                    .name(httpCookie.name)
                    .value(httpCookie.value)
                    .domain(httpCookie.domain ?: url.host)
                    .path(httpCookie.path ?: "/")
                    .apply {
                        if (httpCookie.secure) secure()
                        if (httpCookie.maxAge > 0) {
                            expiresAt(System.currentTimeMillis() + httpCookie.maxAge * 1000L)
                        }
                    }
                    .build()
            }
        android.util.Log.d("JavaNetCookieJar", "Returning ${result.size} OkHttp cookies (filtered from ${httpCookies.size})")
        return result
    }
}

// Custom Date adapter for ISO 8601 dates
class DateAdapter {
    private val formats = listOf(
        // With timezone offset
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        // With Z
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    )

    @FromJson
    fun fromJson(json: String): Date {
        for (format in formats) {
            try {
                return format.parse(json) ?: continue
            } catch (e: Exception) {
                continue
            }
        }
        // If all parsing fails, return current date as fallback
        return Date()
    }

    @ToJson
    fun toJson(date: Date): String {
        return formats[3].format(date)
    }
}

object ApiClient {
    // Deprecated: Use getBaseUrl(context) instead
    // const val BASE_URL = "https://oeee.cafe"

    private lateinit var persistentCookieStore: PersistentCookieStore
    private lateinit var applicationContext: Context
    private var isInitialized = false

    fun initialize(context: Context) {
        if (!isInitialized) {
            applicationContext = context.applicationContext
            persistentCookieStore = PersistentCookieStore(applicationContext)
            isInitialized = true
        }
    }

    fun getBaseUrl(): String {
        return if (isInitialized) {
            ApiConfig.getBaseUrl(applicationContext)
        } else {
            "https://oeee.cafe" // Fallback default
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Cookie manager with persistent cookie store
    private val cookieManager by lazy {
        CookieManager(persistentCookieStore, CookiePolicy.ACCEPT_ALL)
    }

    val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .cookieJar(JavaNetCookieJar(cookieManager))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val moshi = Moshi.Builder()
        .add(DateAdapter())
        .add(NotificationTypeAdapter())
        .build()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // Method to clear all cookies (used during logout)
    fun clearCookies() {
        cookieManager.cookieStore.removeAll()
    }
}
