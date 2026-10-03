package zed.rainxch.profile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.ProfileAvatarSpec
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.lists.KomiList
import zed.rainxch.core.presentation.components.lists.KomiListItem
import zed.rainxch.core.presentation.components.lists.KomiListTrailing
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCount
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.profile.presentation.ProfileAction
import zed.rainxch.profile.presentation.ProfileState

fun LazyListScope.profileSections(
    state: ProfileState,
    hasUnreadAnnouncements: Boolean,
    onAction: (ProfileAction) -> Unit,
) {
    item(key = "identity") {
        HeroIdentityCard(state = state, onAction = onAction)
    }

    item(key = "library_header") {
        Spacer(Modifier.height(8.dp))

        if (state.isUserLoggedIn) {
            KomiText(
                text = stringResource(Res.string.profile_section_library),
                role = KomiTextRole.Title,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
    item(key = "library_list") {
        KomiList(
            items = buildList {
                if (state.isUserLoggedIn) {
                    add(
                        KomiListItem(
                            title = stringResource(Res.string.stars),
                            subtitle = stringResource(Res.string.profile_stars_description),
                            icon = Icons.Outlined.Star,
                            onClick = { onAction(ProfileAction.OnStarredReposClick) },
                        ),
                    )
                }

                add(
                    KomiListItem(
                        title = stringResource(Res.string.favourites),
                        subtitle = stringResource(Res.string.profile_favourites_description),
                        icon = Icons.Outlined.Favorite,
                        onClick = { onAction(ProfileAction.OnFavouriteReposClick) },
                    ),
                )

                add(
                    KomiListItem(
                        title = stringResource(Res.string.recently_viewed),
                        subtitle = stringResource(Res.string.profile_recently_viewed_description),
                        icon = Icons.Outlined.Schedule,
                        onClick = { onAction(ProfileAction.OnRecentlyViewedClick) },
                    ),
                )
            }.toImmutableList(),
        )
    }

    item(key = "updates_header") {
        Spacer(Modifier.height(8.dp))

        KomiText(
            text = stringResource(Res.string.profile_section_updates),
            role = KomiTextRole.Title,
        )

        Spacer(Modifier.height(8.dp))
    }
    item(key = "updates_list") {
        KomiList(
            items = persistentListOf(
                KomiListItem(
                    title = stringResource(Res.string.whats_new_title),
                    subtitle = stringResource(Res.string.whats_new_profile_description),
                    icon = Icons.Outlined.Campaign,
                    onClick = { onAction(ProfileAction.OnWhatsNewClick) },
                ),
                KomiListItem(
                    title = stringResource(Res.string.announcements_title),
                    subtitle = stringResource(Res.string.announcements_profile_description),
                    icon = Icons.Outlined.Notifications,
                    trailing = if (hasUnreadAnnouncements) KomiListTrailing.UnreadDot else KomiListTrailing.Chevron,
                    onClick = { onAction(ProfileAction.OnAnnouncementsClick) },
                ),
            ),
        )
    }

    item(key = "app_header") {
        Spacer(Modifier.height(8.dp))

        KomiText(
            text = stringResource(Res.string.section_app_block),
            role = KomiTextRole.Title,
        )

        Spacer(Modifier.height(8.dp))
    }
    item(key = "app_list") {
        KomiList(
            items = persistentListOf(
                KomiListItem(
                    title = stringResource(Res.string.tweaks_title),
                    subtitle = stringResource(Res.string.profile_tweaks_description),
                    icon = Icons.Outlined.Tune,
                    onClick = { onAction(ProfileAction.OnTweaksClick) },
                ),
                KomiListItem(
                    title = stringResource(Res.string.profile_entry_about_title),
                    subtitle = stringResource(Res.string.profile_entry_about_subtitle),
                    icon = Icons.Outlined.Info,
                    onClick = { onAction(ProfileAction.OnAboutClick) },
                ),
            ),
        )
    }

    if (state.isUserLoggedIn) {
        item(key = "account_header") {
            Spacer(Modifier.height(8.dp))

            KomiText(
                text = stringResource(Res.string.profile_section_account),
                role = KomiTextRole.Title,
            )

            Spacer(Modifier.height(8.dp))
        }
        item(key = "account_list") {
            KomiList(
                items = persistentListOf(
                    KomiListItem(
                        title = stringResource(Res.string.logout),
                        icon = Icons.AutoMirrored.Filled.Logout,
                        trailing = KomiListTrailing.None,
                        destructive = true,
                        onClick = { onAction(ProfileAction.OnLogoutClick) },
                    ),
                ),
            )
        }
    }
}

@Composable
private fun HeroIdentityCard(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isUserLoggedIn) {
                SignedInContent(state = state, onAction = onAction)
            } else {
                SignedOutContent(onAction = onAction)
            }
        }
    }
}

@Composable
private fun SignedOutContent(onAction: (ProfileAction) -> Unit) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            KomiIcon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        KomiText(
            text = stringResource(Res.string.profile_sign_in_title),
            role = KomiTextRole.Title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            uppercase = false,
        )
        KomiText(
            text = stringResource(Res.string.profile_sign_in_description),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        KomiButton(
            onClick = { onAction(ProfileAction.OnLoginClick) },
            label = stringResource(Res.string.profile_login),
            variant = KomiButtonVariant.Primary,
            size = KomiButtonSize.Lg,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SignedInContent(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val profile = state.userProfile
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ProfileAvatarSpec.Size)
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (profile == null) {
                KomiIcon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = colors.onSurfaceVariant,
                )
            } else {
                GitHubStoreImage(
                    imageModel = { profile.imageUrl },
                    modifier = Modifier.fillMaxSize(),
                    showLoadingIndicator = false,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (profile == null) {
                PlaceholderLine(widthFraction = 0.55f, height = 16.dp)
                Spacer(Modifier.height(2.dp))
                PlaceholderLine(widthFraction = 0.35f, height = 12.dp)
            } else {
                val displayName = profile.name.takeIf { it.isNotBlank() } ?: profile.username
                KomiText(
                    text = displayName,
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    uppercase = false,
                )
                KomiText(
                    text = "@${profile.username}",
                    role = KomiTextRole.Label,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    uppercase = false,
                )
                profile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Spacer(Modifier.height(2.dp))
                    KomiText(
                        text = bio,
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        uppercase = false,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    MetricsStrip(
        repos = profile?.repositoryCount,
        followers = profile?.followers,
        following = profile?.following,
        onReposClick = {
            profile?.let { onAction(ProfileAction.OnRepositoriesClick(it.username)) }
        },
    )
}

@Composable
private fun PlaceholderLine(
    widthFraction: Float,
    height: Dp,
) {
    val colors = LocalPersonality.current.colors
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
            .background(colors.surfaceContainerHigh),
    )
}

@Composable
private fun MetricsStrip(
    repos: Int?,
    followers: Int?,
    following: Int?,
    onReposClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Metric(
            value = repos?.let { formatCount(it) } ?: METRIC_PLACEHOLDER,
            label = stringResource(Res.string.profile_repos),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                .clickable(onClick = onReposClick)
                .padding(vertical = 6.dp),
        )
        MetricDivider()
        Metric(
            value = followers?.let { formatCount(it) } ?: METRIC_PLACEHOLDER,
            label = stringResource(Res.string.followers),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        )
        MetricDivider()
        Metric(
            value = following?.let { formatCount(it) } ?: METRIC_PLACEHOLDER,
            label = stringResource(Res.string.following),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        )
    }
}

private const val METRIC_PLACEHOLDER = "—"

@Composable
private fun Metric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KomiText(
            text = value,
            role = KomiTextRole.Title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            uppercase = false,
        )
        KomiText(
            text = label,
            role = KomiTextRole.Label,
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetricDivider() {
    val colors = LocalPersonality.current.colors
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(colors.outlineVariant.copy(alpha = 0.55f)),
    )
}
