package zed.rainxch.tweaks.presentation.appinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.app_icon
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.utils.constrainedContentWidth
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_app_name
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_community_business_cta
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_community_business_subtitle
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_community_business_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_community_section
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_community_subtitle
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_community_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_licenses_subtitle
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_licenses_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_privacy_policy_subtitle
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_privacy_policy_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_source_code_subtitle
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_source_code_title
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_tagline
import zed.rainxch.githubstore.core.presentation.res.tweaks_app_info_website
import zed.rainxch.githubstore.core.presentation.res.tweaks_entry_app_info
import zed.rainxch.tweaks.presentation.utils.Constants.BUSINESS_EMAIL
import zed.rainxch.tweaks.presentation.utils.Constants.DISCORD_URL
import zed.rainxch.tweaks.presentation.utils.Constants.GITHUB_ORG_URL
import zed.rainxch.tweaks.presentation.utils.Constants.MASTODON_URL
import zed.rainxch.tweaks.presentation.utils.Constants.PRIVACY_POLICY_URL
import zed.rainxch.tweaks.presentation.utils.Constants.REDDIT_URL
import zed.rainxch.tweaks.presentation.utils.Constants.SOURCE_CODE_URL
import zed.rainxch.tweaks.presentation.utils.Constants.TELEGRAM_URL
import zed.rainxch.tweaks.presentation.utils.Constants.WEBSITE_URL

@Composable
fun AppInfoRoot(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    viewModel: AppInfoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val uriHandler = LocalUriHandler.current

    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.tweaks_entry_app_info),
                onBack = onNavigateBack,
            )
        },
        grid = true,
        screentone = true,
        toastState = toastState
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column (
                modifier = Modifier
                    .constrainedContentWidth()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 28.dp)
            ) {
                AppIdentityCard(versionName = state.versionName)
                Spacer(Modifier.height(20.dp))

                KomiText(
                    text = stringResource(Res.string.tweaks_app_info_community_section),
                    role = KomiTextRole.SectionTitle
                )
                Spacer(Modifier.height(8.dp))

                CommunityCard(
                    onTelegram = { runCatching { uriHandler.openUri(TELEGRAM_URL) } },
                    onDiscord = { runCatching { uriHandler.openUri(DISCORD_URL) } },
                    onMastodon = { runCatching { uriHandler.openUri(MASTODON_URL) } },
                    onReddit = { runCatching { uriHandler.openUri(REDDIT_URL) } },
                    onGithub = { runCatching { uriHandler.openUri(GITHUB_ORG_URL) } },
                    onWebsite = { runCatching { uriHandler.openUri(WEBSITE_URL) } },
                    onBusiness = { runCatching { uriHandler.openUri(BUSINESS_EMAIL) } },
                )
                Spacer(Modifier.height(20.dp))

                ActionRow(
                    icon = Icons.Outlined.Code,
                    title = stringResource(Res.string.tweaks_app_info_licenses_title),
                    subtitle = stringResource(Res.string.tweaks_app_info_licenses_subtitle),
                    accent = LocalPersonality.current.colors.primary,
                    onClick = onNavigateToLicenses,
                )
                Spacer(Modifier.height(8.dp))

                ActionRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(Res.string.tweaks_app_info_privacy_policy_title),
                    subtitle = stringResource(Res.string.tweaks_app_info_privacy_policy_subtitle),
                    accent = LocalPersonality.current.colors.primary,
                    onClick = {
                        runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) }
                    },
                )
                Spacer(Modifier.height(8.dp))

                ActionRow(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    title = stringResource(Res.string.tweaks_app_info_source_code_title),
                    subtitle = stringResource(Res.string.tweaks_app_info_source_code_subtitle),
                    accent = LocalPersonality.current.colors.primary,
                    onClick = {
                        runCatching { uriHandler.openUri(SOURCE_CODE_URL) }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppIdentityCard(versionName: String) {
    val colors = LocalPersonality.current.colors
    KomiSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                KomiText(
                    text = stringResource(Res.string.tweaks_app_info_app_name),
                    role = KomiTextRole.SectionTitle,
                    color = colors.onSurface,
                )
                KomiText(
                    text = versionName.ifBlank { "—" },
                    role = KomiTextRole.Mono,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                KomiText(
                    text = stringResource(Res.string.tweaks_app_info_tagline),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CommunityCard(
    onTelegram: () -> Unit,
    onDiscord: () -> Unit,
    onMastodon: () -> Unit,
    onReddit: () -> Unit,
    onGithub: () -> Unit,
    onWebsite: () -> Unit,
    onBusiness: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(
                    text = stringResource(Res.string.tweaks_app_info_community_title),
                    role = KomiTextRole.SectionTitle,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                KomiText(
                    text = stringResource(Res.string.tweaks_app_info_community_subtitle),
                    role = KomiTextRole.Overline,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SocialTile(
                    label = "Telegram",
                    iconUrl = "https://cdn.simpleicons.org/telegram/000000",
                    accent = LocalPersonality.current.colors.primary,
                    onClick = onTelegram,
                    modifier = Modifier.weight(1f),
                )
                SocialTile(
                    label = "Discord",
                    iconUrl = "https://cdn.simpleicons.org/discord/000000",
                    accent = LocalPersonality.current.colors.primary,
                    onClick = onDiscord,
                    modifier = Modifier.weight(1f),
                )
                SocialTile(
                    label = "Mastodon",
                    iconUrl = "https://cdn.simpleicons.org/mastodon/000000",
                    accent = LocalPersonality.current.colors.primary,
                    onClick = onMastodon,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SocialTile(
                    label = "Reddit",
                    iconUrl = "https://cdn.simpleicons.org/reddit/000000",
                    accent = LocalPersonality.current.colors.primary,
                    onClick = onReddit,
                    modifier = Modifier.weight(1f),
                )
                SocialTile(
                    label = "GitHub",
                    iconUrl = "https://cdn.simpleicons.org/github/000000",
                    accent = LocalPersonality.current.colors.primary,
                    onClick = onGithub,
                    modifier = Modifier.weight(1f),
                )
                SocialTile(
                    label = stringResource(Res.string.tweaks_app_info_website),
                    iconFallback = Icons.Outlined.Language,
                    accent = LocalPersonality.current.colors.primary,
                    onClick = onWebsite,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            KomiHorizontalDivider(color = colors.divider)
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = stringResource(Res.string.tweaks_app_info_community_business_title),
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                    )
                    KomiText(
                        text = stringResource(Res.string.tweaks_app_info_community_business_subtitle),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }
                KomiButton(
                    onClick = onBusiness,
                    label = stringResource(Res.string.tweaks_app_info_community_business_cta),
                    variant = KomiButtonVariant.Primary,
                    size = KomiButtonSize.Sm,
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                )
            }
        }
    }
}

@Composable
private fun SocialTile(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    iconFallback: ImageVector? = null,
) {
    val shape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                if (iconUrl != null) {
                    GitHubStoreImage(
                        imageModel = { iconUrl },
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(accent),
                    )
                } else if (iconFallback != null) {
                    KomiIcon(
                        imageVector = iconFallback,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            KomiText(
                text = label,
                role = KomiTextRole.LabelSmall,
                color = LocalPersonality.current.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accent: Color = Color.Unspecified,
) {
    val colors = LocalPersonality.current.colors
    val tileBg = if (accent == Color.Unspecified) {
        colors.surfaceContainerHigh
    } else {
        accent.copy(alpha = 0.14f)
    }
    val tint = if (accent == Color.Unspecified) {
        colors.onSurfaceVariant
    } else {
        accent
    }
    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                KomiText(
                    text = title,
                    role = KomiTextRole.ItemTitle,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                KomiText(
                    text = subtitle,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KomiIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
