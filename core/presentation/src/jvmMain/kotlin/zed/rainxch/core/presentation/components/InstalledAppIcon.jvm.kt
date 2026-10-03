package zed.rainxch.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.app_icon

@Composable
actual fun InstalledAppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier,
    apkFilePath: String?,
) {
    Image(
        painter = painterResource(Res.drawable.app_icon),
        contentDescription = appName,
        modifier = modifier,
    )
}
