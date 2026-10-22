package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.installed
import zed.rainxch.githubstore.core.presentation.res.pending_install
import zed.rainxch.githubstore.core.presentation.res.update_available

@Composable
internal fun StatusChip(status: HeaderStatus) {
    KomiChip(
        label = stringResource(status.label),
        kind = KomiChipKind.Tonal,
        size = KomiChipSize.Sm,
        selected = status.emphasized,
        leadingIcon = status.icon,
    )
}

@Composable
internal fun StatusSticker(
    status: HeaderStatus,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiText(
        text = stringResource(status.label),
        role = KomiTextRole.Stamp,
        color = if (status.emphasized) colors.onPrimary else colors.onSurface,
        modifier =
            modifier
                .rotate(STICKER_TILT)
                .hardShadow(offset = DpOffset(3.dp, 3.dp), color = colors.shadow)
                .background(if (status.emphasized) colors.primary else colors.surface)
                .border(3.dp, colors.outline)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

internal enum class HeaderStatus(
    val label: StringResource,
    val icon: ImageVector,
    val emphasized: Boolean,
) {
    Pending(Res.string.pending_install, Icons.Rounded.InstallMobile, true),
    Update(Res.string.update_available, Icons.Rounded.Upgrade, true),
    Installed(Res.string.installed, Icons.Rounded.CheckCircle, false),
}

internal fun headerStatusOf(app: InstalledApp): HeaderStatus =
    when {
        app.isPendingInstall -> HeaderStatus.Pending
        app.isUpdateAvailable -> HeaderStatus.Update
        else -> HeaderStatus.Installed
    }

private const val STICKER_TILT = 6f
