package zed.rainxch.core.presentation.personality.classic

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// Null where the platform has no wallpaper colors: Desktop, and Android 11 and older.
@Composable
expect fun systemWallpaperColorScheme(dark: Boolean): ColorScheme?
