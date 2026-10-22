package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The color an icon is built around: the installed app's own icon when there is one, else the
// image at [imageUrl]. Null until it is known, and for icons too gray to give one.
@Composable
fun rememberIconSeedColor(
    packageName: String?,
    imageUrl: String?,
): Color? {
    val context = LocalPlatformContext.current
    return produceState<Color?>(null, packageName, imageUrl) {
        value = withContext(Dispatchers.Default) { loadIconBitmap(context, packageName, imageUrl)?.themeColorOrNull() }
    }.value
}

internal expect suspend fun loadIconBitmap(
    context: PlatformContext,
    packageName: String?,
    imageUrl: String?,
): ImageBitmap?

// The seed needs no detail, and a small image quantizes quickly.
internal const val ICON_SEED_SIZE = 96
