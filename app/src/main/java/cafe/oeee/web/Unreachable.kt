package cafe.oeee.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.oeee.R
import cafe.oeee.ui.theme.OeeeCafeTheme

/**
 * Tells the web view when the device is back on the network after being off it, so a page that
 * could not be reached is tried again by itself, as the iOS app and the desktop app's
 * loader do.
 */
class Connectivity(context: Context, private val onRestored: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var online = true

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            main.post { changed(validated) }
        }

        override fun onLost(network: Network) {
            main.post { changed(false) }
        }
    }

    private fun changed(isOnline: Boolean) {
        val restored = isOnline && !online
        online = isOnline
        if (restored) onRestored()
    }

    fun start() {
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (e: RuntimeException) {
            // Too many callbacks registered, or none allowed: the retry button still works.
        }
    }

    fun stop() {
        try {
            manager.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            // Was never registered.
        }
    }
}

/**
 * A tab whose page could not be reached: said in words, with a way to try again, over the
 * web view's own error page. On the site's ground and its grid once a page has said what
 * they are (BridgeMessage.Theme), so it reads as the site's rather than as a different app.
 */
@Composable
fun UnreachableView(ground: Color?, grid: Color?, retry: () -> Unit) {
    val background = ground ?: MaterialTheme.colorScheme.background
    OeeeCafeTheme(darkTheme = background.luminance() < 0.5f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .grid(grid)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.WifiOff,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.unreachable_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                stringResource(R.string.unreachable_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(onClick = retry, modifier = Modifier.padding(top = 20.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/**
 * The lines the site rules on its ground (ds.css in oeee-cafe/web): one CSS pixel wide, at
 * the top and left of every 14px square. A CSS pixel in the web view is a dp here.
 */
private fun Modifier.grid(color: Color?): Modifier =
    if (color == null) this else drawBehind {
        val step = 14.dp.toPx()
        val line = 1.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(color, topLeft = Offset(0f, y), size = Size(size.width, line))
            y += step
        }
        var x = 0f
        while (x < size.width) {
            drawRect(color, topLeft = Offset(x, 0f), size = Size(line, size.height))
            x += step
        }
    }
