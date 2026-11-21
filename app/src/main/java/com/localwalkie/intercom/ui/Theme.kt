package com.localwalkie.intercom.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.localwalkie.intercom.model.PresenceStatus

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1C5),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF123B3A),
    onPrimaryContainer = Color(0xFFB7F2EC),
    secondary = Color(0xFF7AA2F7),
    background = Color(0xFF0E1621),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF15202E),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2A3B),
    onSurfaceVariant = Color(0xFF9FB0C3),
    outline = Color(0xFF3B4C61),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E69),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEFEA),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF2F5DA8),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF0E1621),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1621),
    surfaceVariant = Color(0xFFE3EAF2),
    onSurfaceVariant = Color(0xFF4A5B6E),
    outline = Color(0xFFB4C0CE),
    error = Color(0xFFBA1A1A)
)

val RecordRed = Color(0xFFE5484D)
val AcceptGreen = Color(0xFF2E9E5B)

fun statusColor(status: PresenceStatus): Color = when (status) {
    PresenceStatus.AVAILABLE -> Color(0xFF2ECC71)
    PresenceStatus.IN_MEETING -> Color(0xFFE5484D)
    PresenceStatus.BUSY -> Color(0xFFF5A524)
    PresenceStatus.AWAY -> Color(0xFF8B98A8)
}

@Composable
fun WalkieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
