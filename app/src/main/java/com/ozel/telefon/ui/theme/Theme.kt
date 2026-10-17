package com.ozel.telefon.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** iOS sistem renkleri */
object IOS {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val Gray = Color(0xFF8E8E93)
}

/** Inter fontunu app/src/main/assets/fonts/ klasöründeki static dosyalardan yükler. */
fun loadInter(context: Context): FontFamily? {
    return try {
        val am = context.assets
        val files = am.list("fonts")?.toSet() ?: return null
        val defs = listOf(
            "Inter_24pt-Light.ttf" to FontWeight.Light,
            "Inter_24pt-Regular.ttf" to FontWeight.Normal,
            "Inter_24pt-Medium.ttf" to FontWeight.Medium,
            "Inter_24pt-SemiBold.ttf" to FontWeight.SemiBold,
            "Inter_24pt-Bold.ttf" to FontWeight.Bold
        )
        val fonts = defs.filter { it.first in files }
            .map { (name, w) -> Font(assetManager = am, path = "fonts/$name", weight = w) }
        if (fonts.isEmpty()) null else FontFamily(fonts)
    } catch (e: Exception) {
        null
    }
}

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

@Composable
fun OzelTelefonTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val family = remember { loadInter(context) ?: FontFamily.Default }

    val scheme = if (dark) {
        darkColorScheme(
            primary = IOS.Blue,
            background = Color.Black,
            surface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFF2C2C2E),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = IOS.Gray
        )
    } else {
        lightColorScheme(
            primary = IOS.Blue,
            background = Color.White,
            surface = Color(0xFFF7F7F7),
            surfaceVariant = Color(0xFFE9E9EB),
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = IOS.Gray
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography().withFont(family),
        content = content
    )
}
