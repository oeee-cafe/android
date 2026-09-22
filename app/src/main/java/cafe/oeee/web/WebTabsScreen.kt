package cafe.oeee.web

import android.app.Activity
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import cafe.oeee.ui.theme.OeeeCafeTheme

/** The native tab bar, each tab showing its own page of the site. */
@Composable
fun WebTabsScreen(
    store: WebTabStore,
    visibleTabs: List<WebTab>,
    selectedTab: WebTab,
    onSelectTab: (WebTab) -> Unit,
    badgeCount: (WebTab) -> Long
) {
    // Read so that a recreated web view is picked up.
    store.generation
    val controller = store.controller(selectedTab)
    val activity = LocalContext.current as Activity

    // Back goes back in the tab's own history, then to the home tab, then leaves the app.
    BackHandler {
        when {
            controller.webView.canGoBack() -> controller.webView.goBack()
            selectedTab != WebTab.HOME -> onSelectTab(WebTab.HOME)
            else -> activity.moveTaskToBack(true)
        }
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
                                if (selected) store.controller(tab).reselect() else onSelectTab(tab)
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
        }
    }
}

/** The search tab: a native search field, with the site's results below it. */
@Composable
private fun SearchTab(controller: WebTabController) {
    var query by rememberSaveable { mutableStateOf("") }
    var hasSearched by remember(controller) { mutableStateOf(controller.hasLoaded) }
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
                    hasSearched = true
                    controller.search(trimmed)
                    keyboard?.hide()
                }
            })
        )
        Box(modifier = Modifier.fillMaxSize()) {
            WebTabView(controller)
            if (!hasSearched) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(WebTab.SEARCH.title),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Shows a tab's web view. The web view outlives this view (it keeps the tab's history while
 * another tab is shown), so it is moved in from wherever it was last shown.
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
