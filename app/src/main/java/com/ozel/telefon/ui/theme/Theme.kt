package com.ozel.telefon.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** iOS sistem renkleri */
object IOS {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val Gray = Color(0xFF8E8E93)
}

/**
 * Yazı tipi ailesini app/src/main/assets/fonts/ klasöründeki dosyalardan yükler.
 * Önce San Francisco Pro (iOS yazı tipi), bulunamazsa Inter, o da yoksa sistem fontu.
 */
fun loadAppFont(context: Context): FontFamily? {
    val sf = listOf(
        "SF-Pro-Text-Light.otf" to FontWeight.Light,
        "SF-Pro-Text-Regular.otf" to FontWeight.Normal,
        "SF-Pro-Text-Medium.otf" to FontWeight.Medium,
        "SF-Pro-Text-Semibold.otf" to FontWeight.SemiBold,
        "SF-Pro-Text-Bold.otf" to FontWeight.Bold
    )
    val inter = listOf(
        "Inter_24pt-Light.ttf" to FontWeight.Light,
        "Inter_24pt-Regular.ttf" to FontWeight.Normal,
        "Inter_24pt-Medium.ttf" to FontWeight.Medium,
        "Inter_24pt-SemiBold.ttf" to FontWeight.SemiBold,
        "Inter_24pt-Bold.ttf" to FontWeight.Bold
    )
    return loadFamily(context, sf) ?: loadFamily(context, inter)
}

private fun loadFamily(context: Context, defs: List<Pair<String, FontWeight>>): FontFamily? {
    return try {
        val am = context.assets
        val files = am.list("fonts")?.toSet() ?: return null
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
    val family = remember { loadAppFont(context) ?: FontFamily.Default }

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
        content = {
            // SF Pro Text harfleri iOS'ta sistemin yaptığı sıkılaştırma olmadan geniş durur; hafifçe daraltılır.
            ProvideTextStyle(TextStyle(letterSpacing = (-0.3).sp), content)
        }
    )
}
