package com.hungphat.attendance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AttendanceColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EAF7),
    onPrimaryContainer = Navy900,
    secondary = Teal600,
    onSecondary = Color.White,
    tertiary = Green600,
    onTertiary = Color.White,
    background = SurfaceSoft,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE8EEF3),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFB8C4CE),
)

@Composable
fun CongTyAttendanceTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AttendanceColorScheme,
        typography = Typography,
        content = content,
    )
}
