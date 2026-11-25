package zed.rainxch.githubstore.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.channels.ReceiveChannel
import zed.rainxch.githubstore.app.deeplink.DeepLinkDestination
import zed.rainxch.githubstore.app.deeplink.DeepLinkParser

@Composable
fun HandleDesktopToolbarDeeplinks(
    deepLinkUris: ReceiveChannel<String>,
    onDeepLink: (DeepLinkDestination) -> Unit,
    onDeepLinkConsume: () -> Unit,
) {
    val currentOnDeepLink by rememberUpdatedState(onDeepLink)
    val currentOnDeepLinkConsume by rememberUpdatedState(onDeepLinkConsume)

    LaunchedEffect(deepLinkUris) {
        for (uri in deepLinkUris) {
            currentOnDeepLink(DeepLinkParser.parse(uri))
            currentOnDeepLinkConsume()
        }
    }
}
