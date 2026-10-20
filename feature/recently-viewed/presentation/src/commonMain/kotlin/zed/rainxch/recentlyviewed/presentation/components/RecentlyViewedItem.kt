package zed.rainxch.recentlyviewed.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.recently_viewed_remove_cd
import zed.rainxch.recentlyviewed.presentation.model.RecentlyViewedRepo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentlyViewedItem(
    repo: RecentlyViewedRepo,
    onItemClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onDevProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiSurface(
        modifier = modifier,
        onClick = onItemClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .clickable(onClick = onDevProfileClick)
                        .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GitHubStoreImage(
                    imageModel = { repo.repoOwnerAvatarUrl },
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(shape.cornerSmall)),
                )

                KomiText(
                    text = repo.repoOwner,
                    role = KomiTextRole.LabelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = repo.repoName,
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    repo.repoDescription?.let {
                        Spacer(Modifier.height(4.dp))

                        KomiText(
                            text = it,
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                KomiIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.recently_viewed_remove_cd),
                    onClick = onRemoveClick,
                    variant = KomiButtonVariant.Tonal,
                    size = KomiIconButtonSize.Sm,
                )
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repo.primaryLanguage?.let { language ->
                    KomiChip(
                        label = language,
                        kind = KomiChipKind.Info,
                        size = KomiChipSize.Sm,
                        leadingContent = {
                            KomiIcon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        },
                    )
                }

                KomiChip(
                    label = repo.viewedAtFormatted,
                    kind = KomiChipKind.Info,
                    size = KomiChipSize.Sm,
                    leadingContent = {
                        KomiIcon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
