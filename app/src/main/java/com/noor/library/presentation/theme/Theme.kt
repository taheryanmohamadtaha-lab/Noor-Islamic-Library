package com.noor.library.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.noor.library.R

val Emerald = Color(0xFF0F4C3A)
val EmeraldDeep = Color(0xFF0B2A24)
val EmeraldSurface = Color(0xFF123B33)
val Gold = Color(0xFFD4A94C)
val GoldSoft = Color(0xFFB8913A)
val Cream = Color(0xFFFBF4E0)
val CreamDark = Color(0xFFF1E6C6)

val AmiriFont = FontFamily(Font(R.font.amiri_quran))
val NaskhFont = FontFamily(Font(R.font.noto_naskh))
val VazirFont = FontFamily(Font(R.font.vazirmatn))

fun arabicFamily(key: String): FontFamily = when (key) {
    "naskh" -> NaskhFont
    "system" -> FontFamily.Default
    else -> AmiriFont
}

fun persianFamily(key: String): FontFamily = when (key) {
    "system" -> FontFamily.Default
    else -> VazirFont
}

private val DarkScheme = darkColorScheme(
    primary = Gold, onPrimary = EmeraldDeep,
    secondary = Color(0xFF5FB89A), onSecondary = EmeraldDeep,
    background = EmeraldDeep, onBackground = Color(0xFFEFE9D6),
    surface = EmeraldSurface, onSurface = Color(0xFFEFE9D6),
    surfaceVariant = Color(0xFF1A4A40), onSurfaceVariant = Color(0xFFC9D4CC),
    outline = Color(0xFF3E6B60),
)

private val LightScheme = lightColorScheme(
    primary = Emerald, onPrimary = Color.White,
    secondary = GoldSoft, onSecondary = Color.White,
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF14231F),
    surface = Color(0xFFF4F8F6), onSurface = Color(0xFF14231F),
    surfaceVariant = Color(0xFFE3EEE9), onSurfaceVariant = Color(0xFF3B4D47),
    outline = Color(0xFF9DB3AA),
)

private val CreamScheme = lightColorScheme(
    primary = Emerald, onPrimary = Color.White,
    secondary = GoldSoft, onSecondary = Color.White,
    background = Cream, onBackground = Color(0xFF2B2415),
    surface = Color(0xFFFFFAEB), onSurface = Color(0xFF2B2415),
    surfaceVariant = CreamDark, onSurfaceVariant = Color(0xFF5A4E32),
    outline = Color(0xFFBFAE83),
)

@Composable
fun NoorTheme(theme: String, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        "light" -> LightScheme
        "cream" -> CreamScheme
        "system" -> if (isSystemInDarkTheme()) DarkScheme else LightScheme
        else -> DarkScheme
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Subtle eight-pointed-star lattice inspired by Islamic geometric art. */
fun Modifier.islamicPattern(color: Color = Gold, alpha: Float = 0.10f, cell: Float = 72f): Modifier =
    this.drawBehind {
        val c = color.copy(alpha = alpha)
        val stroke = Stroke(width = 1.2f)
        var y = 0f
        while (y < size.height + cell) {
            var x = 0f
            while (x < size.width + cell) {
                val half = cell * 0.32f
                val center = Offset(x, y)
                drawRect(c, topLeft = Offset(center.x - half, center.y - half), size = Size(half * 2, half * 2), style = stroke)
                rotate(45f, pivot = center) {
                    drawRect(c, topLeft = Offset(center.x - half, center.y - half), size = Size(half * 2, half * 2), style = stroke)
                }
                x += cell
            }
            y += cell
        }
    }
