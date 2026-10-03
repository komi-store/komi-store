package zed.rainxch.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GitHubStoreImage(
    imageModel: () -> Any?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    showLoadingIndicator: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val model = imageModel()
    if (model == null) {
        Box(modifier = modifier)
        return
    }

    val sizeResolver = rememberConstraintsSizeResolver()
    val platformContext = LocalPlatformContext.current
    val request =
        remember(model, sizeResolver) {
            ImageRequest
                .Builder(platformContext)
                .data(model)
                .size(sizeResolver)
                .build()
        }
    val painter = rememberAsyncImagePainter(model = request)
    val state by painter.state.collectAsState()
    val resolvedModifier = modifier.then(sizeResolver)

    when (state) {
        is AsyncImagePainter.State.Success ->
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = resolvedModifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
            )

        is AsyncImagePainter.State.Error ->
            Box(modifier = resolvedModifier, contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxSize(.5f),
                )
            }

        is AsyncImagePainter.State.Loading ->
            Box(modifier = resolvedModifier, contentAlignment = Alignment.Center) {
                if (showLoadingIndicator) CircularWavyProgressIndicator()
            }

        is AsyncImagePainter.State.Empty ->
            Box(modifier = resolvedModifier)
    }
}
