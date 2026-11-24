package zed.rainxch.details.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.presentation.components.buttons.KomiActionRow
import zed.rainxch.core.presentation.components.buttons.KomiActionRowItem
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.add_to_favourites
import zed.rainxch.githubstore.core.presentation.res.details_refresh
import zed.rainxch.githubstore.core.presentation.res.details_refresh_cooldown
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_menu
import zed.rainxch.githubstore.core.presentation.res.open_repository
import zed.rainxch.githubstore.core.presentation.res.remove_from_favourites
import zed.rainxch.githubstore.core.presentation.res.repository_not_starred
import zed.rainxch.githubstore.core.presentation.res.repository_starred
import zed.rainxch.githubstore.core.presentation.res.share_repository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun DetailsTopBarActions(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    val cooldownSeconds = rememberCooldownSeconds(state.refreshCooldownUntilEpochMs)
    val refreshLabel =
        if (cooldownSeconds > 0) {
            stringResource(Res.string.details_refresh_cooldown, cooldownSeconds)
        } else {
            stringResource(Res.string.details_refresh)
        }
    val items =
        buildList {
            add(
                KomiActionRowItem(
                    icon = Icons.Rounded.OpenInBrowser,
                    title = stringResource(Res.string.open_repository),
                    onClick = { onAction(DetailsAction.OpenRepoInBrowser) },
                ),
            )
            add(starItem(state.isStarred, onAction))
            add(favouriteItem(state.isFavourite, onAction))
            if (state.repository?.htmlUrl != null) {
                add(
                    KomiActionRowItem(
                        icon = Icons.Rounded.Share,
                        title = stringResource(Res.string.share_repository),
                        onClick = { onAction(DetailsAction.OnShareClick) },
                    ),
                )
            }
            add(
                KomiActionRowItem(
                    icon = Icons.Rounded.Refresh,
                    title = refreshLabel,
                    onClick = { onAction(DetailsAction.Refresh) },
                    enabled = cooldownSeconds <= 0 && !state.isRefreshing,
                ),
            )
            if (state.installedApp?.installSource == InstallSource.MANUAL) {
                add(
                    KomiActionRowItem(
                        icon = Icons.Rounded.LinkOff,
                        title = stringResource(Res.string.details_unlink_external_app_menu),
                        onClick = { onAction(DetailsAction.OnUnlinkExternalApp) },
                    ),
                )
            }
        }.toImmutableList()
    KomiActionRow(items = items, maxVisible = 1)
}

@Composable
private fun starItem(
    isStarred: Boolean,
    onAction: (DetailsAction) -> Unit,
): KomiActionRowItem =
    KomiActionRowItem(
        icon = if (isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
        title = stringResource(if (isStarred) Res.string.repository_starred else Res.string.repository_not_starred),
        onClick = { onAction(DetailsAction.OnToggleStar) },
    )

@Composable
private fun favouriteItem(
    isFavourite: Boolean,
    onAction: (DetailsAction) -> Unit,
): KomiActionRowItem =
    KomiActionRowItem(
        icon = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        title = stringResource(if (isFavourite) Res.string.remove_from_favourites else Res.string.add_to_favourites),
        onClick = { onAction(DetailsAction.OnToggleFavorite) },
    )

// Seconds left on the refresh cooldown, ticking down while it lasts.
@Composable
private fun rememberCooldownSeconds(cooldownUntilMs: Long?): Int {
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(cooldownUntilMs) {
        if (cooldownUntilMs == null) return@LaunchedEffect
        while (Clock.System.now().toEpochMilliseconds() < cooldownUntilMs) {
            nowMs = Clock.System.now().toEpochMilliseconds()
            delay(COOLDOWN_TICK_MILLIS.milliseconds)
        }
        nowMs = Clock.System.now().toEpochMilliseconds()
    }
    return cooldownUntilMs?.let { until -> ceilSeconds(until - nowMs) } ?: 0
}

private fun ceilSeconds(millis: Long): Int = ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).coerceAtLeast(0L).toInt()

private const val COOLDOWN_TICK_MILLIS = 500L
private const val MILLIS_PER_SECOND = 1000L
