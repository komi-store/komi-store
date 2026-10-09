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
    avatarFallbackUrl: String?,
) {
    // The desktop build has no package manager to read an installed icon from. A download that is
    // not installed yet still belongs to a repository, so show that repository's owner avatar
    // rather than this application's own logo — the same rule the Android side follows.
    if (avatarFallbackUrl != null) {
        GitHubStoreImage(
            imageModel = { avatarFallbackUrl },
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = appName,
            modifier = modifier,
        )
    }
}
