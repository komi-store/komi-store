package zed.rainxch.apps.presentation.starred.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.starred.StarredCandidateUi
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCompactCount
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.starred_picker_already_tracked
import zed.rainxch.githubstore.core.presentation.res.starred_picker_apk_badge
import zed.rainxch.githubstore.core.presentation.res.starred_picker_cd_already_tracked
import zed.rainxch.githubstore.core.presentation.res.starred_picker_cd_latest
import zed.rainxch.githubstore.core.presentation.res.starred_picker_cd_ships_apk

@Composable
fun StarredCandidateRow(
    candidate: StarredCandidateUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val shipsApkLabel = stringResource(Res.string.starred_picker_cd_ships_apk)
    val alreadyTrackedLabel = stringResource(Res.string.starred_picker_cd_already_tracked)
    val latestLabel = candidate.latestReleaseTag?.let { stringResource(Res.string.starred_picker_cd_latest, it) }
    val a11yLabel =
        buildString {
            append(candidate.owner)
            append(" / ")
            append(candidate.name)
            if (candidate.hasApkRelease) append(", ").append(shipsApkLabel)
            if (candidate.isAlreadyTracked) append(", ").append(alreadyTrackedLabel)
            latestLabel?.let { append(", ").append(it) }
        }

    KomiSurface(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(12.dp)
                    .semantics { contentDescription = a11yLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(shape.cornerSmall)),
            ) {
                GitHubStoreImage(
                    imageModel = { candidate.ownerAvatarUrl },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = "${candidate.owner}/${candidate.name}",
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!candidate.description.isNullOrBlank()) {
                    KomiText(
                        text = candidate.description,
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KomiIcon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(14.dp),
                        )

                        Spacer(Modifier.width(2.dp))

                        KomiText(
                            text = formatCompactCount(candidate.stargazersCount),
                            role = KomiTextRole.Caption,
                            color = colors.onSurfaceVariant,
                        )
                    }

                    candidate.latestReleaseTag?.let { tag ->
                        KomiText(
                            text = tag,
                            role = KomiTextRole.Caption,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (candidate.hasApkRelease) {
                Badge(
                    icon = Icons.Outlined.GetApp,
                    label = stringResource(Res.string.starred_picker_apk_badge),
                )
            }

            if (candidate.isAlreadyTracked) {
                Badge(
                    icon = Icons.Outlined.CheckCircle,
                    label = stringResource(Res.string.starred_picker_already_tracked),
                )
            }
        }
    }
}

@Composable
private fun Badge(
    icon: ImageVector,
    label: String,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(colors.primaryTint)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiIcon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))

        Spacer(Modifier.width(4.dp))

        KomiText(text = label, role = KomiTextRole.Caption, color = colors.primary)
    }
}
