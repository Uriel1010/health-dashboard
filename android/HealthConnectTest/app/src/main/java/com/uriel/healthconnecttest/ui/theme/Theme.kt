package com.uriel.healthconnecttest.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.uriel.healthconnecttest.data.AppearancePreferences
import com.uriel.healthconnecttest.data.AppearanceMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC7A7FF), onPrimary = Color(0xFF30105A),
    primaryContainer = Color(0xFF392451), onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFF77CED6), tertiary = Color(0xFFFFC275),
    background = Color(0xFF141119), surface = Color(0xFF1D1924),
    onBackground = Color(0xFFF1EDF7), onSurface = Color(0xFFF1EDF7),
    onSurfaceVariant = Color(0xFFBDB5CC), outline = Color(0xFF82758F), outlineVariant = Color(0xFF393142),
    surfaceContainer = Color(0xFF241D2D), surfaceContainerHighest = Color(0xFF30263B),
    secondaryContainer = Color(0xFF392451), onSecondaryContainer = Color(0xFFEBDDFF),
    surfaceVariant = Color(0xFF2B2335), error = Color(0xFFFF9AAB), errorContainer = Color(0xFF481E2C), onErrorContainer = Color(0xFFFFDCE3)
)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6324B5), onPrimary = Color.White,
    primaryContainer = Color(0xFFEEE7FF), onPrimaryContainer = Color(0xFF45158A),
    secondary = Color(0xFF25858F), tertiary = Color(0xFFB86A0B),
    background = Color(0xFFFAF9FE), surface = Color(0xFFFDFCFF),
    onBackground = Color(0xFF17131E), onSurface = Color(0xFF17131E), onSurfaceVariant = Color(0xFF585165),
    outline = Color(0xFFB2A9C1), outlineVariant = Color(0xFFE6E1ED),
    surfaceContainer = Color(0xFFF5F2FB), surfaceContainerHighest = Color(0xFFECE7F3),
    secondaryContainer = Color(0xFFEEE7FF), onSecondaryContainer = Color(0xFF511B99),
    surfaceVariant = Color(0xFFF0ECF7), error = Color(0xFFC42039), errorContainer = Color(0xFFFFE9EE), onErrorContainer = Color(0xFF71152A)
)

@Composable
fun HealthConnectTestTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode by remember(context) { AppearancePreferences.mode(context) }.collectAsState(initial = AppearanceMode.System)
    val dark = when (mode) { AppearanceMode.System -> darkTheme; AppearanceMode.Light -> false; AppearanceMode.Dark -> true }
    val colors = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography, shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp)), content = content)
}
