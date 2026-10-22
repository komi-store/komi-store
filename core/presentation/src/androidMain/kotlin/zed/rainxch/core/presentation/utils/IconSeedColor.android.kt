package zed.rainxch.core.presentation.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import zed.rainxch.core.presentation.components.resolveInstalledIcon

internal actual suspend fun loadIconBitmap(
    context: PlatformContext,
    packageName: String?,
    imageUrl: String?,
): ImageBitmap? {
    val installed = packageName?.let { resolveInstalledIcon(context.packageManager, it) }
    if (installed != null || imageUrl == null) return installed
    // A hardware bitmap keeps its pixels on the GPU, where the quantizer can't read them.
    val request =
        ImageRequest
            .Builder(context)
            .data(imageUrl)
            .size(ICON_SEED_SIZE)
            .allowHardware(false)
            .build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
    return result?.image?.toBitmap()?.asImageBitmap()
}
