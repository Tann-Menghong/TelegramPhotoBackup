package com.example.tgphotobackup.ui

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

// Telegram-inspired blue palette
private val Blue40  = Color(0xFF0066AA)
private val Blue80  = Color(0xFF94CCFF)
private val Blue90  = Color(0xFFCDE5FF)
private val Blue10  = Color(0xFF001E30)
private val Blue30  = Color(0xFF004A72)

private val LightColors = lightColorScheme(
    primary            = Blue40,
    onPrimary          = Color.White,
    primaryContainer   = Blue90,
    onPrimaryContainer = Blue10,
    secondaryContainer = Color(0xFFDCE8F5),
    surface            = Color(0xFFF8FAFB),
    surfaceVariant     = Color(0xFFEBF2F7),
    background         = Color(0xFFF4F7FA),
)

private val DarkColors = darkColorScheme(
    primary            = Blue80,
    onPrimary          = Blue10,
    primaryContainer   = Blue30,
    onPrimaryContainer = Blue90,
    surface            = Color(0xFF1A1C1E),
    surfaceVariant     = Color(0xFF252A2E),
    background         = Color(0xFF111416),
)

@Composable
fun TgBackupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
