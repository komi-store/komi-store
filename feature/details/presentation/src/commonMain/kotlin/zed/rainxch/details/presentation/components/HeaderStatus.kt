package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiTooltip
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.installed
import zed.rainxch.githubstore.core.presentation.res.pending_install
import zed.rainxch.githubstore.core.presentation.res.update_available

// Classic's status: a burst on the picture's corner, its label in the tooltip and for screen readers.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatusBadge(
    status: HeaderStatus,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val label = stringResource(status.label)
    val shape = MaterialShapes.SoftBurst.toShape()
    KomiTooltip(text = label, modifier = modifier) {
        // No shadow: a shadow on this burst hangs the RenderThread on many devices (1.9.3 froze Details).
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .background(if (status.emphasized) colors.primary else colors.primaryContainer, shape),
            contentAlignment = Alignment.Center,
        ) {
            KomiIcon(
                imageVector = status.icon,
                contentDescription = label,
                tint = if (status.emphasized) colors.onPrimary else colors.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
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

// The plain "Installed" status gives way to the app switcher; pending and update stay.
internal fun headerStatusOf(
    app: InstalledApp?,
    showAppSwitcher: Boolean,
): HeaderStatus? =
    when {
        app == null -> null
        app.isPendingInstall -> HeaderStatus.Pending
        app.isUpdateAvailable -> HeaderStatus.Update
        showAppSwitcher -> null
        else -> HeaderStatus.Installed
    }

private const val STICKER_TILT = 6f
