package com.example.szatnia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Burgundy,
    onPrimary = Paper,
    primaryContainer = RoseMist,
    onPrimaryContainer = BurgundyDark,
    secondary = Clay,
    onSecondary = Paper,
    tertiary = Moss,
    onTertiary = Paper,
    background = Linen,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = RoseMist.copy(alpha = 0.45f),
    onSurfaceVariant = Slate,
)

private val DarkColors = darkColorScheme(
    primary = RoseMist,
    onPrimary = BurgundyDark,
    primaryContainer = BurgundyDark,
    onPrimaryContainer = Paper,
    secondary = Clay,
    onSecondary = Ink,
    tertiary = Moss,
    onTertiary = Ink,
    background = Ink,
    onBackground = Linen,
    surface = ColorTokens.DarkSurface,
    onSurface = Linen,
    surfaceVariant = ColorTokens.DarkSurfaceVariant,
    onSurfaceVariant = RoseMist,
)

private object ColorTokens {
    val DarkSurface = Slate.copy(alpha = 0.55f)
    val DarkSurfaceVariant = BurgundyDark.copy(alpha = 0.7f)
}

@Composable
fun SzatniaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}
