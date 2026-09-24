package cafe.oeee.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.DefaultFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The five Material icons the app draws, copied from material-icons-core and
 * material-icons-extended 1.7.8 (Icons.Filled.*), path for path, and built as their
 * materialIcon and materialPath build them, so they draw exactly as those did without the
 * library of every icon there is.
 */
object AppIcons {
    val Download: ImageVector by lazy {
        materialIcon("Filled.Download") {
            moveTo(5.0f, 20.0f)
            horizontalLineToRelative(14.0f)
            verticalLineToRelative(-2.0f)
            horizontalLineTo(5.0f)
            verticalLineTo(20.0f)
            close()
            moveTo(19.0f, 9.0f)
            horizontalLineToRelative(-4.0f)
            verticalLineTo(3.0f)
            horizontalLineTo(9.0f)
            verticalLineToRelative(6.0f)
            horizontalLineTo(5.0f)
            lineToRelative(7.0f, 7.0f)
            lineTo(19.0f, 9.0f)
            close()
        }
    }

    val ContentCopy: ImageVector by lazy {
        materialIcon("Filled.ContentCopy") {
            moveTo(16.0f, 1.0f)
            lineTo(4.0f, 1.0f)
            curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
            verticalLineToRelative(14.0f)
            horizontalLineToRelative(2.0f)
            lineTo(4.0f, 3.0f)
            horizontalLineToRelative(12.0f)
            lineTo(16.0f, 1.0f)
            close()
            moveTo(19.0f, 5.0f)
            lineTo(8.0f, 5.0f)
            curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
            verticalLineToRelative(14.0f)
            curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(11.0f)
            curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
            lineTo(21.0f, 7.0f)
            curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
            close()
            moveTo(19.0f, 21.0f)
            lineTo(8.0f, 21.0f)
            lineTo(8.0f, 7.0f)
            horizontalLineToRelative(11.0f)
            verticalLineToRelative(14.0f)
            close()
        }
    }

    val Share: ImageVector by lazy {
        materialIcon("Filled.Share") {
            moveTo(18.0f, 16.08f)
            curveToRelative(-0.76f, 0.0f, -1.44f, 0.3f, -1.96f, 0.77f)
            lineTo(8.91f, 12.7f)
            curveToRelative(0.05f, -0.23f, 0.09f, -0.46f, 0.09f, -0.7f)
            reflectiveCurveToRelative(-0.04f, -0.47f, -0.09f, -0.7f)
            lineToRelative(7.05f, -4.11f)
            curveToRelative(0.54f, 0.5f, 1.25f, 0.81f, 2.04f, 0.81f)
            curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
            reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
            reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
            curveToRelative(0.0f, 0.24f, 0.04f, 0.47f, 0.09f, 0.7f)
            lineTo(8.04f, 9.81f)
            curveTo(7.5f, 9.31f, 6.79f, 9.0f, 6.0f, 9.0f)
            curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
            reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
            curveToRelative(0.79f, 0.0f, 1.5f, -0.31f, 2.04f, -0.81f)
            lineToRelative(7.12f, 4.16f)
            curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
            curveToRelative(0.0f, 1.61f, 1.31f, 2.92f, 2.92f, 2.92f)
            curveToRelative(1.61f, 0.0f, 2.92f, -1.31f, 2.92f, -2.92f)
            reflectiveCurveToRelative(-1.31f, -2.92f, -2.92f, -2.92f)
            close()
        }
    }

    val Link: ImageVector by lazy {
        materialIcon("Filled.Link") {
            moveTo(3.9f, 12.0f)
            curveToRelative(0.0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
            horizontalLineToRelative(4.0f)
            lineTo(11.0f, 7.0f)
            lineTo(7.0f, 7.0f)
            curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
            reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
            horizontalLineToRelative(4.0f)
            verticalLineToRelative(-1.9f)
            lineTo(7.0f, 15.1f)
            curveToRelative(-1.71f, 0.0f, -3.1f, -1.39f, -3.1f, -3.1f)
            close()
            moveTo(8.0f, 13.0f)
            horizontalLineToRelative(8.0f)
            verticalLineToRelative(-2.0f)
            lineTo(8.0f, 11.0f)
            verticalLineToRelative(2.0f)
            close()
            moveTo(17.0f, 7.0f)
            horizontalLineToRelative(-4.0f)
            verticalLineToRelative(1.9f)
            horizontalLineToRelative(4.0f)
            curveToRelative(1.71f, 0.0f, 3.1f, 1.39f, 3.1f, 3.1f)
            reflectiveCurveToRelative(-1.39f, 3.1f, -3.1f, 3.1f)
            horizontalLineToRelative(-4.0f)
            lineTo(13.0f, 17.0f)
            horizontalLineToRelative(4.0f)
            curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
            reflectiveCurveToRelative(-2.24f, -5.0f, -5.0f, -5.0f)
            close()
        }
    }

    val WifiOff: ImageVector by lazy {
        materialIcon("Filled.WifiOff") {
            moveTo(22.99f, 9.0f)
            curveTo(19.15f, 5.16f, 13.8f, 3.76f, 8.84f, 4.78f)
            lineToRelative(2.52f, 2.52f)
            curveToRelative(3.47f, -0.17f, 6.99f, 1.05f, 9.63f, 3.7f)
            lineToRelative(2.0f, -2.0f)
            close()
            moveTo(18.99f, 13.0f)
            curveToRelative(-1.29f, -1.29f, -2.84f, -2.13f, -4.49f, -2.56f)
            lineToRelative(3.53f, 3.53f)
            lineToRelative(0.96f, -0.97f)
            close()
            moveTo(2.0f, 3.05f)
            lineTo(5.07f, 6.1f)
            curveTo(3.6f, 6.82f, 2.22f, 7.78f, 1.0f, 9.0f)
            lineToRelative(1.99f, 2.0f)
            curveToRelative(1.24f, -1.24f, 2.67f, -2.16f, 4.2f, -2.77f)
            lineToRelative(2.24f, 2.24f)
            curveTo(7.81f, 10.89f, 6.27f, 11.73f, 5.0f, 13.0f)
            verticalLineToRelative(0.01f)
            lineTo(6.99f, 15.0f)
            curveToRelative(1.36f, -1.36f, 3.14f, -2.04f, 4.92f, -2.06f)
            lineTo(18.98f, 20.0f)
            lineToRelative(1.27f, -1.26f)
            lineTo(3.29f, 1.79f)
            lineTo(2.0f, 3.05f)
            close()
            moveTo(9.0f, 17.0f)
            lineToRelative(3.0f, 3.0f)
            lineToRelative(3.0f, -3.0f)
            curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6.0f, 0.0f)
            close()
        }
    }

    /** materialIcon { materialPath { ... } }: 24dp on a 24 by 24 viewport, one black path. */
    private fun materialIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24f.dp,
            defaultHeight = 24f.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = false
        ).path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = DefaultFillType,
            pathBuilder = pathBuilder
        ).build()
}
