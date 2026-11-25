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

// The color an icon is built around, from the installed icon or imageUrl; null until known, or when too gray.
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
