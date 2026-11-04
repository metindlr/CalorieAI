package com.calorieai.app

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Light = lightColorScheme(
    primary = Color(0xFF0B7A5A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F4E6), onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFFF59E0B), onSecondary = Color(0xFF2B1B00),
    secondaryContainer = Color(0xFFFFE9B8), onSecondaryContainer = Color(0xFF3A2500),
    tertiary = Color(0xFF0F5E73),
    background = Color(0xFFF5F8F6), onBackground = Color(0xFF0F1A14),
    surface = Color.White, onSurface = Color(0xFF0F1A14),
    surfaceVariant = Color(0xFFE6EEE9), onSurfaceVariant = Color(0xFF5B6B62),
    outline = Color(0xFF8A9A91), outlineVariant = Color(0xFFDCE6E0), error = Color(0xFFD9363E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF3DDC97), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF12382C), onPrimaryContainer = Color(0xFFBDF2DB),
    secondary = Color(0xFFFFB84D), onSecondary = Color(0xFF2B1B00),
    secondaryContainer = Color(0xFF4A3510), onSecondaryContainer = Color(0xFFFFE0A8),
    tertiary = Color(0xFF4FC3DC),
    background = Color(0xFF0B100E), onBackground = Color(0xFFE5ECE8),
    surface = Color(0xFF131A17), onSurface = Color(0xFFE5ECE8),
    surfaceVariant = Color(0xFF1E2823), onSurfaceVariant = Color(0xFF9FB0A7),
    outline = Color(0xFF6B7C73), outlineVariant = Color(0xFF27332D), error = Color(0xFFFF8A8F),
)

@Composable
fun CalorieTheme(dark: Boolean, content: @Composable () -> Unit) {
    val b = Typography()
    val typo = b.copy(
        displayMedium = b.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = b.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
        headlineSmall = b.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = b.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = b.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = b.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = typo,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
