package cafe.oeee.web

import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import cafe.oeee.ui.theme.OeeeCafeTheme

/** The native tab bar, picking what the one web view shows. */
@Composable
fun WebTabsScreen(controller: WebTabController, visibleTabs: List<WebTab>, badgeCount: (WebTab) -> Long) {
    // The section of the page showing, or home for a page whose section has no tab of
    // theirs -- the sign-in page, once they are signed in.
    val selectedTab = if (controller.section in visibleTabs) controller.section else WebTab.HOME
    // The screen is only ever shown by MainActivity.
    val activity = checkNotNull(LocalActivity.current)

    // Back goes back through the pages read, then to the home page, then leaves the app.
    // Leaving is the system's own back, so from Android 13 its predictive animation shows
    // the home screen as the app goes; before 12 the system's would close the activity
    // and the web view with it, so there the app steps aside itself.
    val leavesBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    BackHandler(enabled = controller.canGoBack || selectedTab != WebTab.HOME || !leavesBySystem) {
        when {
            controller.webView.canGoBack() -> controller.webView.goBack()
            selectedTab != WebTab.HOME -> controller.show(WebTab.HOME)
            else -> activity.moveTaskToBack(true)
        }
    }

    controller.drawingMenu?.let { drawing ->
        DrawingSheet(
            drawing = drawing,
            scope = controller.coroutineScope,
            actions = controller,
            onDismiss = { controller.drawingMenu = null }
        )
    }

    // The status bar takes the color of the page's top edge and the tab bar (and the
    // system's navigation bar under it) the color of its bottom edge, with icons that read on them.
    val statusBarColor = controller.topColor ?: MaterialTheme.colorScheme.background
    val tabBarColor = controller.bottomColor ?: MaterialTheme.colorScheme.surfaceContainer
    val tabBarIsLight = tabBarColor.luminance() > 0.5f
    val view = LocalView.current
    LaunchedEffect(statusBarColor, tabBarIsLight) {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = statusBarColor.luminance() > 0.5f
            isAppearanceLightNavigationBars = tabBarIsLight
        }
    }

    Scaffold(
        containerColor = statusBarColor,
        bottomBar = {
            // Icons and labels in the light or dark scheme, whichever reads on the page's color.
            OeeeCafeTheme(darkTheme = !tabBarIsLight) {
                NavigationBar(containerColor = tabBarColor) {
                    for (tab in visibleTabs) {
                        val selected = tab == selectedTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Selecting the already selected tab takes it back to its top.
                                if (selected) controller.reselect() else controller.show(tab)
                            },
                            icon = {
                                val count = badgeCount(tab)
                                BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
                                    Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.title)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            if (selectedTab == WebTab.SEARCH) {
                SearchTab(controller)
            } else {
                WebTabView(controller)
            }
            if (controller.isUnreachable) {
                UnreachableView(ground = controller.topColor, retry = controller::retry)
            }
        }
    }
}

/** The search tab: a native search field, with the site's results below it. */
@Composable
private fun SearchTab(controller: WebTabController) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(WebTab.SEARCH.title)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                val trimmed = query.trim()
                if (trimmed.isNotEmpty()) {
                    controller.search(trimmed)
                    keyboard?.hide()
                }
            })
        )
        // What is under the field before anything is searched for is the site's own search
        // page, which leaves its form out where a field like this one is above it
        // (search.jinja in oeee-cafe/web).
        WebTabView(controller)
    }
}

/**
 * Shows the web view. It outlives this view -- it is the same one in every tab, and keeps
 * the history -- so it is moved in from wherever it was last shown.
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
