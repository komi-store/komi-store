package zed.rainxch.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun InstalledAppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier = Modifier,
    apkFilePath: String? = null,
)
