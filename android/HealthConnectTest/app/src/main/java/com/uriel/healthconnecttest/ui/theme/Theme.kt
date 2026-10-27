package com.uriel.healthconnecttest.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF70DDB7),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF164E3C),
    onPrimaryContainer = Color(0xFFA0F2D2),
    secondary = Color(0xFF9BCBEB),
    tertiary = Color(0xFFF1B5C5),
    background = Color(0xFF111614),
    surface = Color(0xFF181E1B),
    surfaceContainer = Color(0xFF202823),
    surfaceContainerHighest = Color(0xFF2C3730),
    secondaryContainer = Color(0xFF24485D),
    onSecondaryContainer = Color(0xFFC4E7FF),
    surfaceVariant = Color(0xFF26322C)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006C50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAF1D9),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF286584),
    tertiary = Color(0xFF98415B),
    background = Color(0xFFF5F8F6),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF3EF),
    surfaceContainerHighest = Color(0xFFE2EBE5),
    secondaryContainer = Color(0xFFCCE7F8),
    onSecondaryContainer = Color(0xFF092F44),
    surfaceVariant = Color(0xFFE6EEE9)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun HealthConnectTestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(8.dp),
            extraLarge = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}
