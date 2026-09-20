package com.eetu.twitchapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class TwitchColorPalette(
    val isOled: Boolean,
    val background: Color,
    val surface: Color,
    val card: Color,
    val chatBackground: Color
)

val LocalTwitchColors = staticCompositionLocalOf {
    TwitchColorPalette(
        isOled = false,
        background = TwitchDark,
        surface = TwitchDarkSurface,
        card = TwitchDarkCard,
        chatBackground = TwitchDarkChat
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = TwitchPurple,
    secondary = TwitchDarkPurple,
    tertiary = TwitchTeal,
    background = TwitchDark,
    surface = TwitchDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = TwitchTextLight,
    onSurface = TwitchTextLight
)

private val OledColorScheme = darkColorScheme(
    primary = TwitchPurple,
    secondary = TwitchDarkPurple,
    tertiary = TwitchTeal,
    background = TwitchOledBackground,
    surface = TwitchOledSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = TwitchTextLight,
    onSurface = TwitchTextLight
)

@Composable
fun TwitchAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isOled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isOled) OledColorScheme else DarkColorScheme
    val twitchColors = if (isOled) {
        TwitchColorPalette(
            isOled = true,
            background = TwitchOledBackground,
            surface = TwitchOledSurface,
            card = TwitchOledCard,
            chatBackground = TwitchOledChat
        )
    } else {
        TwitchColorPalette(
            isOled = false,
            background = TwitchDark,
            surface = TwitchDarkSurface,
            card = TwitchDarkCard,
            chatBackground = TwitchDarkChat
        )
    }

    CompositionLocalProvider(LocalTwitchColors provides twitchColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
