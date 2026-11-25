package zed.rainxch.githubstore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.presentation.ProfileAvatarSpec
import zed.rainxch.core.presentation.personality.classic.systemWallpaperColorScheme
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.toMangaAccent
import zed.rainxch.core.presentation.personality.toMangaPaper
import zed.rainxch.core.presentation.personality.utils.PersonalityTheme
import zed.rainxch.core.presentation.utils.ObserveTimeZoneChanges
import zed.rainxch.githubstore.app.navigation.AppNavigation

@Composable
fun App(
    modifier: Modifier = Modifier,
    deepLinkUri: String? = null,
    onDeepLinkConsume: () -> Unit = {},
    onResolvedDarkTheme: (Boolean) -> Unit = {},
    onContentReady: () -> Unit = {},
) {
    val mainViewModel: MainViewModel = koinViewModel()
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()

    ObserveTimeZoneChanges()

    val pendingDeepLinks = remember { Channel<String>(Channel.UNLIMITED) }
    LaunchedEffect(deepLinkUri) {
        deepLinkUri?.let { pendingDeepLinks.trySend(it) }
    }

    setSingletonImageLoaderFactory { context ->
        ImageLoader
            .Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    if (mainState.isAppearanceLoaded) {
        AppContent(
            state = mainState,
            onAction = mainViewModel::onAction,
            deepLinkUris = pendingDeepLinks,
            onDeepLinkConsume = onDeepLinkConsume,
            onResolvedDarkTheme = onResolvedDarkTheme,
            onContentReady = onContentReady,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize())
    }
}

@Composable
private fun AppContent(
    state: MainState,
    onAction: (MainAction) -> Unit,
    deepLinkUris: ReceiveChannel<String>,
    onDeepLinkConsume: () -> Unit,
    onResolvedDarkTheme: (Boolean) -> Unit,
    onContentReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnContentReady by rememberUpdatedState(onContentReady)
    val currentOnResolvedDarkTheme by rememberUpdatedState(onResolvedDarkTheme)

    LaunchedEffect(Unit) { currentOnContentReady() }

    val imageContext = LocalPlatformContext.current
    val avatarSizePx = with(LocalDensity.current) { ProfileAvatarSpec.Size.roundToPx() }
    LaunchedEffect(state.signedInAvatarUrl, avatarSizePx) {
        state.signedInAvatarUrl?.let { url ->
            runCatching {
                SingletonImageLoader.get(imageContext).enqueue(
                    ImageRequest
                        .Builder(imageContext)
                        .data(url)
                        .size(avatarSizePx)
                        .build(),
                )
            }
        }
    }

    val resolvedDarkTheme = state.isDarkTheme ?: isSystemInDarkTheme()
    LaunchedEffect(resolvedDarkTheme) { currentOnResolvedDarkTheme(resolvedDarkTheme) }

    val personality =
        when (state.personality) {
            AppPersonality.MANGA -> {
                mangaPersonality(
                    paper = state.mangaPaper.toMangaPaper(),
                    accent = state.accent.toMangaAccent(),
                )
            }

            AppPersonality.CLASSIC -> {
                val wallpaper = if (state.accent == AccentId.WALLPAPER) systemWallpaperColorScheme(resolvedDarkTheme) else null
                remember(resolvedDarkTheme, state.isAmoledTheme, state.accent, wallpaper) {
                    classicPersonality(
                        dark = resolvedDarkTheme,
                        amoled = state.isAmoledTheme,
                        accent = state.accent,
                        wallpaper = wallpaper,
                    )
                }
            }
        }

    AppNavigation(
        deepLinkUris = deepLinkUris,
        onDeepLinkConsume = onDeepLinkConsume,
        isScrollbarEnabled = state.isScrollbarEnabled,
        contentWidth = state.contentWidth,
        theme = { content -> PersonalityTheme(personality, languageTag = state.appLanguageTag, content = content) },
        modifier = modifier,
    ) { currentScreen, onNavigate ->
        AppOverlays(
            state = state,
            currentScreen = currentScreen,
            onAction = onAction,
            onNavigate = onNavigate,
        )
    }
}
