package cafe.oeee.web

import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat

/** The one web view, with the site's own toolbar the only way around it. */
@Composable
fun WebTabsScreen(controller: WebTabController) {
    // The screen is only ever shown by MainActivity.
    val activity = checkNotNull(LocalActivity.current)

    // Back goes back through the pages read, then leaves the app. Leaving is the system's
    // own back, so from Android 13 its predictive animation shows the home screen as the
    // app goes; before 12 the system's would close the activity and the web view with it,
    // so there the app steps aside itself.
    val leavesBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    BackHandler(enabled = controller.canGoBack || !leavesBySystem) {
        if (controller.webView.canGoBack()) {
            controller.webView.goBack()
        } else {
            activity.moveTaskToBack(true)
        }
    }

    controller.drawingMenu?.let { drawing ->
        DrawingSheet(
            drawing = drawing,
            scope = controller.coroutineScope,
            actions = controller,
            words = controller.words,
            onDismiss = { controller.drawingMenu = null }
        )
    }

    // The status bar and the system's navigation bar take the site's ground, which is what
    // the page has at both its edges, with icons that read on it.
    val barColor = controller.ground ?: MaterialTheme.colorScheme.background
    val barIsLight = barColor.luminance() > 0.5f
    val view = LocalView.current
    LaunchedEffect(barIsLight) {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = barIsLight
            isAppearanceLightNavigationBars = barIsLight
        }
    }

    Scaffold(containerColor = barColor) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            WebTabView(controller)
            if (controller.isUnreachable) {
                UnreachableView(
                    ground = controller.ground,
                    grid = controller.grid,
                    retry = controller::retry
                )
            }
        }
    }
}

/**
 * Shows the web view. It outlives this view -- it keeps the history -- so it is moved in
 * from wherever it was last shown.
 */
@Composable
private fun WebTabView(controller: WebTabController) {
    key(controller) {
        AndroidView(
            factory = {
                controller.view.also { (it.parent as? ViewGroup)?.removeView(it) }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
