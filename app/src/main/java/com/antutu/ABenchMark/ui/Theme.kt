package com.antutu.ABenchMark.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The app palette.
 *
 * Material You is used where the platform provides it, so the app picks up
 * the wallpaper colours of the device it runs on. The hand rolled scheme is
 * the fallback for devices without dynamic colour, and it is tuned so the
 * brand accent stays recognisable in both light and dark.
 */
private val BrandLight = Color(0xFF6C4CFF)
private val BrandDark = Color(0xFFB9A6FF)

private val LightScheme = lightColorScheme(
    primary = BrandLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF00696E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF0F6),
    onSecondaryContainer = Color(0xFF002022),
    tertiary = Color(0xFF9A4600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC7),
    onTertiaryContainer = Color(0xFF331200),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF380094),
    primaryContainer = Color(0xFF5227CC),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFF80D4DA),
    onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFF9CF0F6),
    tertiary = Color(0xFFFFB786),
    onTertiary = Color(0xFF522400),
    tertiaryContainer = Color(0xFF753300),
    onTertiaryContainer = Color(0xFFFFDBC7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun MasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
