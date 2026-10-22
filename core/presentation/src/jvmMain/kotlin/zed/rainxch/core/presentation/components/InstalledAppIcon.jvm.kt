package zed.rainxch.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun InstalledAppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier,
    apkFilePath: String?,
    avatarFallbackUrl: String?,
) {
    FallbackAppIcon(appName = appName, avatarFallbackUrl = avatarFallbackUrl, modifier = modifier)
}
