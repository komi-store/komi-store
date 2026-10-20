package zed.rainxch.devprofile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCount
import zed.rainxch.devprofile.domain.model.DeveloperProfile
import zed.rainxch.devprofile.presentation.DeveloperProfileAction
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.dev_profile_org_badge
import zed.rainxch.githubstore.core.presentation.res.followers
import zed.rainxch.githubstore.core.presentation.res.following
import zed.rainxch.githubstore.core.presentation.res.profile_repos

private val MentionRegex = Regex("(?<![A-Za-z0-9-])@([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}[A-Za-z0-9])?)")

@Composable
fun ProfileInfoCard(
    profile: DeveloperProfile,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GitHubStoreImage(
                    imageModel = { profile.avatarUrl },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .background(colors.surfaceContainerHigh),
                )

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KomiText(
                            text = profile.name ?: profile.login,
                            role = KomiTextRole.CardTitle,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        if (profile.isOrganization) {
                            Spacer(Modifier.width(8.dp))

                            OrgPill()
                        }
                    }

                    KomiText(
                        text = "@${profile.login}",
                        role = KomiTextRole.Caption,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            profile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Spacer(Modifier.height(12.dp))
                BioText(bio = bio, onMention = { user ->
                    onAction(DeveloperProfileAction.OnNavigateToUser(user))
                })
            }

            Spacer(Modifier.height(16.dp))

            MetricsStrip(
                repos = profile.publicRepos,
                followers = profile.followers,
                following = profile.following,
            )
        }
    }
}

@Composable
private fun BioText(bio: String, onMention: (String) -> Unit) {
    val colors = LocalPersonality.current.colors
    val annotated = remember(bio, colors.primary) {
        buildAnnotatedString {
            var cursor = 0
            for (match in MentionRegex.findAll(bio)) {
                if (match.range.first > cursor) {
                    append(bio.substring(cursor, match.range.first))
                }
                val handle = match.groupValues[1]
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "mention:$handle",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ),
                        linkInteractionListener = { onMention(handle) },
                    ),
                ) {
                    append("@$handle")
                }
                cursor = match.range.last + 1
            }
            if (cursor < bio.length) append(bio.substring(cursor))
        }
    }
    KomiText(
        text = annotated,
        role = KomiTextRole.Body,
        color = colors.onSurfaceVariant,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MetricsStrip(repos: Int, followers: Int, following: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Metric(value = formatCount(repos), label = stringResource(Res.string.profile_repos), modifier = Modifier.weight(1f))

        MetricDivider()

        Metric(value = formatCount(followers), label = stringResource(Res.string.followers), modifier = Modifier.weight(1f))

        MetricDivider()

        Metric(value = formatCount(following), label = stringResource(Res.string.following), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KomiText(
            text = value,
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
            maxLines = 1,
        )

        KomiText(
            text = label,
            role = KomiTextRole.Caption,
            color = colors.onSurfaceVariant,
            maxLines = 1,
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
            .background(colors.divider),
    )
}

@Composable
private fun OrgPill() {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(shape.cornerSmall))
            .background(colors.primaryContainer),
    ) {
        KomiText(
            text = stringResource(Res.string.dev_profile_org_badge),
            role = KomiTextRole.Overline,
            color = colors.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentityRailCard(
    profile: DeveloperProfile,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    val hasAny = profile.company?.isNotBlank() == true ||
        profile.location?.isNotBlank() == true ||
        profile.blog?.isNotBlank() == true ||
        profile.twitterUsername?.isNotBlank() == true
    if (!hasAny) return

    KomiSurface(
        modifier = Modifier.fillMaxWidth(),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            profile.company?.takeIf { it.isNotBlank() }?.let { company ->
                val trimmed = company.trim()
                if (trimmed.startsWith("@") && trimmed.length > 1) {
                    val handle = trimmed.removePrefix("@")
                    LinkChip(
                        icon = Icons.Default.Business,
                        text = trimmed,
                        onClick = { onAction(DeveloperProfileAction.OnNavigateToUser(handle)) },
                    )
                } else {
                    StaticChip(icon = Icons.Default.Business, text = trimmed)
                }
            }

            profile.location?.takeIf { it.isNotBlank() }?.let { location ->
                StaticChip(icon = Icons.Default.LocationOn, text = location)
            }

            profile.blog?.takeIf { it.isNotBlank() }?.let { blog ->
                val display = blog.removePrefix("https://").removePrefix("http://")
                LinkChip(
                    icon = Icons.Default.Link,
                    text = display,
                    onClick = {
                        val url = if (!blog.startsWith("http")) "https://$blog" else blog
                        onAction(DeveloperProfileAction.OnOpenLink(url))
                    },
                )
            }

            profile.twitterUsername?.takeIf { it.isNotBlank() }?.let { twitter ->
                LinkChip(
                    icon = Icons.Default.Tag,
                    text = "@$twitter",
                    onClick = {
                        onAction(DeveloperProfileAction.OnOpenLink("https://twitter.com/$twitter"))
                    },
                )
            }
        }
    }
}

@Composable
private fun StaticChip(icon: ImageVector, text: String) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(shape.cornerSmall))
            .background(colors.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = colors.divider,
                shape = RoundedCornerShape(shape.cornerSmall),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KomiIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.onSurfaceVariant,
            )

            KomiText(
                text = text,
                role = KomiTextRole.LabelSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LinkChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(shape.cornerSmall))
            .background(colors.primaryTint)
            .border(
                width = 1.dp,
                color = colors.primary.copy(alpha = 0.4f),
                shape = RoundedCornerShape(shape.cornerSmall),
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KomiIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.primary,
            )

            KomiText(
                text = text,
                role = KomiTextRole.LabelSmall,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
