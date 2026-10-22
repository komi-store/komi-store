package zed.rainxch.core.presentation.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap

// Desktop has no installed apps, so the seed always comes from the image.
internal actual suspend fun loadIconBitmap(
    context: PlatformContext,
    packageName: String?,
    imageUrl: String?,
): ImageBitmap? {
    val request =
        ImageRequest
            .Builder(context)
            .data(imageUrl ?: return null)
            .size(ICON_SEED_SIZE)
            .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
    return result?.image?.toBitmap()?.asComposeImageBitmap()
}
