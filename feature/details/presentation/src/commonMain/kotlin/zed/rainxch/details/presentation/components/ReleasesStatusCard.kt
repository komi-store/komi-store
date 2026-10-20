package zed.rainxch.details.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.loading_releases
import zed.rainxch.githubstore.core.presentation.res.no_releases_published
import zed.rainxch.githubstore.core.presentation.res.releases_load_failed
import zed.rainxch.githubstore.core.presentation.res.releases_load_failed_description
import zed.rainxch.githubstore.core.presentation.res.retry

enum class ReleasesStatus {
    FAILED,
    RETRYING,
    EMPTY,
}

@Composable
fun ReleasesStatusCard(
    status: ReleasesStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Flat,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                ReleasesStatus.FAILED -> {
                    KomiIcon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    KomiText(
                        text = stringResource(Res.string.releases_load_failed),
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                    )
                    KomiText(
                        text = stringResource(Res.string.releases_load_failed_description),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    KomiButton(
                        label = stringResource(Res.string.retry),
                        onClick = onRetry,
                    )
                }

                ReleasesStatus.RETRYING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KomiCircularProgress(
                            modifier = Modifier.size(20.dp),
                        )
                        KomiText(
                            text = stringResource(Res.string.loading_releases),
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }

                ReleasesStatus.EMPTY -> {
                    KomiIcon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    KomiText(
                        text = stringResource(Res.string.no_releases_published),
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                    )
                }
            }
        }
    }
}
