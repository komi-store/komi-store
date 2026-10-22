package zed.rainxch.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.app_icon

@Composable
expect fun InstalledAppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier = Modifier,
    apkFilePath: String? = null,
    avatarFallbackUrl: String? = null,
)

@Composable
internal fun FallbackAppIcon(
    appName: String,
    avatarFallbackUrl: String?,
    modifier: Modifier = Modifier,
) {
    val url = avatarFallbackUrl?.takeIf { it.isNotBlank() }
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = appName,
            modifier = modifier,
            error = painterResource(Res.drawable.app_icon),
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = appName,
            modifier = modifier,
        )
    }
}
