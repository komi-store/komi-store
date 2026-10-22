package zed.rainxch.core.presentation.components.markdown

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageWidth
import com.mikepenz.markdown.model.PlaceholderConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.cd_image

class MarkdownImageTransformer(
    private val probeClient: HttpClient,
) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        if (link.isBlank()) return null

        val normalizedLink =
            if (link.contains("github.com") && link.contains("/blob/")) {
                link
                    .replace("github.com", "raw.githubusercontent.com")
                    .replace("/blob/", "/")
            } else {
                link
            }

        if (!normalizedLink.startsWith("http://") &&
            !normalizedLink.startsWith("https://") &&
            !normalizedLink.startsWith("data:")
        ) {
            return null
        }

        val isDataUri = normalizedLink.startsWith("data:")

        var probeResult by remember(normalizedLink) {
            mutableStateOf<ProbeResult>(probeCache[normalizedLink] ?: ProbeResult.Pending)
        }
        LaunchedEffect(normalizedLink) {
            if (isDataUri) {
                probeResult = ProbeResult.Allowed
                return@LaunchedEffect
            }
            val cached = probeCache[normalizedLink]
            if (cached != null) {
                probeResult = cached
                return@LaunchedEffect
            }
            val result =
                withContext(Dispatchers.IO) {
                    probeOnce(normalizedLink)
                }
            probeCache[normalizedLink] = result
            probeResult = result
        }

        if (probeResult is ProbeResult.Skipped) return null

        val context = LocalPlatformContext.current
        val request =
            ImageRequest
                .Builder(context)
                .data(normalizedLink)
                .httpHeaders(networkHeaders)
                .size(MAX_BITMAP_DIMENSION_PX)
                .memoryCacheKey(normalizedLink)
                .diskCacheKey(normalizedLink)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(150)
                .build()

        val painter = rememberAsyncImagePainter(model = request)

        val isBadgeLike = looksLikeBadge(normalizedLink)
        val inlineModifier =
            if (isBadgeLike) {
                Modifier
                    .heightIn(max = BADGE_MAX_HEIGHT_DP.dp)
                    .widthIn(max = BADGE_MAX_WIDTH_DP.dp)
            } else {
                Modifier
                    .heightIn(max = RASTER_MAX_HEIGHT_DP.dp)
                    .widthIn(max = RASTER_MAX_WIDTH_DP.dp)
            }

        return ImageData(
            painter = painter,
            modifier = inlineModifier,
            contentDescription = stringResource(Res.string.cd_image),
            contentScale = ContentScale.Fit,
        )
    }

    private fun looksLikeBadge(url: String): Boolean {
        val lower = url.lowercase()

        val pathOnly = lower.substringBefore('?').substringBefore('#')
        if (pathOnly.endsWith(".svg")) return true

        val host =
            lower
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
        return host in BADGE_HOSTS ||

            "/badge" in pathOnly ||
            "/badges/" in pathOnly ||
            "/workflows/" in pathOnly && pathOnly.endsWith("/badge") ||
            "/bestpractices/" in pathOnly
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size = painter.intrinsicSize

    override fun placeholderConfig(
        link: String,
        density: Density,
        containerSize: Size,
        imageWidth: ImageWidth,
        imageSize: Size,
        imageSizeChanged: ((link: String, Size) -> Unit)?,
    ): PlaceholderConfig {
        val (widthSp, heightSp) =
            when {
                imageSize.isUnspecified ||
                    imageSize.width <= 0f ||
                    imageSize.height <= 0f -> {
                    BADGE_DEFAULT_WIDTH_SP to BADGE_DEFAULT_HEIGHT_SP
                }

                else -> {
                    with(density) {
                        val containerWidthPx =
                            when {
                                containerSize.isUnspecified -> imageSize.width
                                containerSize.width <= 0f -> imageSize.width
                                else -> containerSize.width
                            }
                        val initialWidth = minOf(imageSize.width, containerWidthPx)
                        val ratio =
                            imageSize.height /
                                imageSize.width.coerceAtLeast(1f)
                        val rawHeight = initialWidth * ratio

                        val maxHeightPx = RASTER_MAX_HEIGHT_DP.dp.toPx()
                        val targetHeight = rawHeight.coerceAtMost(maxHeightPx)

                        val targetWidth =
                            if (rawHeight > maxHeightPx && ratio > 0f) {
                                targetHeight / ratio
                            } else {
                                initialWidth
                            }
                        targetWidth.toSp().value to targetHeight.toSp().value
                    }
                }
            }
        return PlaceholderConfig(
            size = Size(widthSp, heightSp),
            verticalAlign = PlaceholderVerticalAlign.Center,
        )
    }

    private suspend fun probeOnce(url: String): ProbeResult {
        probeMutex.withLock {
            probeCache[url]?.let { return it }
            val result =
                runCatching {
                    val response: HttpResponse = probeClient.head(url)
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    when {
                        contentLength == null -> ProbeResult.Allowed
                        contentLength > MAX_IMAGE_BYTES -> ProbeResult.Skipped(contentLength)
                        else -> ProbeResult.Allowed
                    }
                }.getOrElse {
                    ProbeResult.Allowed
                }
            probeCache[url] = result
            return result
        }
    }

    sealed interface ProbeResult {
        data object Pending : ProbeResult

        data object Allowed : ProbeResult

        data class Skipped(
            val contentLength: Long,
        ) : ProbeResult
    }

    companion object {
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36 GitHubStore/1.8"

        const val MAX_BITMAP_DIMENSION_PX = 2048

        const val MAX_IMAGE_BYTES = 5L * 1024 * 1024

        private const val BADGE_DEFAULT_WIDTH_SP = 120f
        private const val BADGE_DEFAULT_HEIGHT_SP = 32f

        private const val BADGE_MAX_HEIGHT_DP = 40
        private const val BADGE_MAX_WIDTH_DP = 220

        private const val RASTER_MAX_HEIGHT_DP = 320
        private const val RASTER_MAX_WIDTH_DP = 480

        private val BADGE_HOSTS =
            setOf(
                "img.shields.io",
                "shields.io",
                "badgen.net",
                "badge.fury.io",
            )

        private val networkHeaders =
            NetworkHeaders
                .Builder()
                .add("User-Agent", BROWSER_UA)
                .add(
                    "Accept",
                    "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                ).build()

        private val probeCache = mutableMapOf<String, ProbeResult>()
        private val probeMutex = Mutex()
    }
}
