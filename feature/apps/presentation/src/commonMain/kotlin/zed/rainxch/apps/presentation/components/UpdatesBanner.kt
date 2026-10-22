package zed.rainxch.apps.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.UpdateAllProgress
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_updates_banner_hide
import zed.rainxch.githubstore.core.presentation.res.apps_updates_banner_show
import zed.rainxch.githubstore.core.presentation.res.apps_updates_banner_subtitle
import zed.rainxch.githubstore.core.presentation.res.apps_updates_banner_title_one
import zed.rainxch.githubstore.core.presentation.res.apps_updates_banner_title_other
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.currently_updating
import zed.rainxch.githubstore.core.presentation.res.update_all
import zed.rainxch.githubstore.core.presentation.res.updating_x_of_y

@Composable
fun UpdatesBanner(
    count: Int,
    isExpanded: Boolean,
    isUpdatingAll: Boolean,
    updateAllProgress: UpdateAllProgress?,
    updateAllEnabled: Boolean,
    onUpdateAll: () -> Unit,
    onCancelUpdateAll: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = LocalPersonality.current.motion.fastSpatial(),
        label = "updates-banner-chevron",
    )
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val title = if (count == 1) {
        stringResource(Res.string.apps_updates_banner_title_one)
    } else {
        stringResource(Res.string.apps_updates_banner_title_other, count)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shape.corner))
            .background(colors.primaryContainer)
            .border(
                width = 0.5.dp,
                color = colors.divider,
                shape = RoundedCornerShape(shape.corner),
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onPrimaryContainer) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(shape.corner))
                                .background(colors.primaryTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            KomiIcon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null,
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        KomiText(
                            text = title,
                            role = KomiTextRole.CardTitle,
                            color = colors.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        KomiText(
                            text = stringResource(Res.string.apps_updates_banner_subtitle),
                            role = KomiTextRole.Body,
                            color = colors.onPrimaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    KomiIcon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotation),
                    )
                }

                if (isUpdatingAll && updateAllProgress != null) {
                    Spacer(Modifier.height(14.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                KomiText(
                                    text = stringResource(
                                        Res.string.updating_x_of_y,
                                        updateAllProgress.current,
                                        updateAllProgress.total,
                                    ),
                                    role = KomiTextRole.ItemTitle,
                                    color = colors.onPrimaryContainer,
                                )

                                KomiText(
                                    text = stringResource(
                                        Res.string.currently_updating,
                                        updateAllProgress.currentAppName,
                                    ),
                                    role = KomiTextRole.Body,
                                    color = colors.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            KomiButton(
                                onClick = onCancelUpdateAll,
                                label = stringResource(Res.string.cancel),
                                variant = KomiButtonVariant.Outline,
                                size = KomiButtonSize.Sm,
                                modifier = Modifier.height(38.dp),
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        KomiLinearProgress(
                            progress = { updateAllProgress.current.toFloat() / updateAllProgress.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.onPrimaryContainer,
                        )
                    }
                } else {
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KomiButton(
                            onClick = onUpdateAll,
                            label = stringResource(Res.string.update_all),
                            variant = KomiButtonVariant.Primary,
                            enabled = updateAllEnabled,
                            leadingIcon = Icons.Default.Update,
                            modifier = Modifier.weight(1f),
                        )

                        KomiButton(
                            onClick = onToggleExpanded,
                            label = if (isExpanded) {
                                stringResource(Res.string.apps_updates_banner_hide)
                            } else {
                                stringResource(Res.string.apps_updates_banner_show)
                            },
                            variant = KomiButtonVariant.Outline,
                            modifier = Modifier.height(44.dp),
                        )
                    }
                }
            }
        }
    }
}

