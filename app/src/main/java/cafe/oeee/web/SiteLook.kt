package cafe.oeee.web

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * The site's look as its pages last said it (BridgeMessage.Theme): the reader's light or dark
 * choice from the toolbar, and the ground and grid of each. Remembered, so the app opens in it
 * rather than turning over once the first page says so -- the web view is white until
 * something paints it, and before a page had spoken the app had nothing to paint it with on a
 * cold start or after its renderer died. iOS keeps the same (SiteTheme.swift in oeee-cafe-apple).
 *
 * A ground is kept for light and for dark apart, told by its own luminance, so a reader
 * who leaves the look to the system opens in whichever the system is in now.
 */
class SiteLook(context: Context) {
    private val preferences = context.getSharedPreferences("site_look", Context.MODE_PRIVATE)

    /** "light" or "dark" as the reader picked it; null for the system's. */
    val choice: String? get() = preferences.getString(CHOICE, null)

    fun remember(theme: BridgeMessage.Theme) {
        preferences.edit().apply {
            putString(CHOICE, theme.choice)
            theme.ground?.let { ground ->
                val look = if (isDark(ground)) "dark" else "light"
                putInt("ground_$look", ground.toArgb())
                theme.grid?.let { putInt("grid_$look", it.toArgb()) }
            }
        }.apply()
    }

    /** Whether the site will be dark when it opens, as its reader chose or as the system is. */
    fun opensDark(configuration: Configuration): Boolean = when (choice) {
        "dark" -> true
        "light" -> false
        else -> configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /** The ground last said for that look, or the design system's own (ds.css in oeee-cafe/web). */
    fun ground(dark: Boolean): Color =
        stored(if (dark) "ground_dark" else "ground_light") ?: if (dark) DARK_GROUND else LIGHT_GROUND

    fun grid(dark: Boolean): Color =
        stored(if (dark) "grid_dark" else "grid_light") ?: if (dark) DARK_GRID else LIGHT_GRID

    private fun stored(key: String): Color? =
        if (preferences.contains(key)) Color(preferences.getInt(key, 0)) else null

    companion object {
        private const val CHOICE = "choice"

        // --ds-ground and --ds-grid, light and dark: for the first time the app opens.
        private val LIGHT_GROUND = Color(0xFFCCCCFF)
        private val LIGHT_GRID = Color(0xFFBBBBFF)
        private val DARK_GROUND = Color(0xFF17172B)
        private val DARK_GRID = Color(0xFF22223F)

        /** Whether a ground is a dark look's: what the app's own dialogs and sheets follow. */
        fun isDark(ground: Color): Boolean = ground.luminance() < 0.5f
    }
}
