package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.CompactStatusFlags
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_compact_status_filter_active
import zed.rainxch.githubstore.core.presentation.res.apps_compact_status_pending_install
import zed.rainxch.githubstore.core.presentation.res.apps_compact_status_pre_release_on
import zed.rainxch.githubstore.core.presentation.res.apps_compact_status_ready_to_install
import zed.rainxch.githubstore.core.presentation.res.apps_compact_status_updates_ignored
import zed.rainxch.githubstore.core.presentation.res.apps_compact_status_variant_pinned
import zed.rainxch.githubstore.core.presentation.res.apps_compact_status_variant_stale

@Composable
fun rememberCompactStatusFlags(appItem: AppItem): CompactStatusFlags {
    val app = appItem.installedApp
    return remember(
        app.assetFilterRegex,
        app.fallbackToOlderReleases,
        app.preferredAssetVariant,
        app.preferredVariantStale,
        app.includePreReleases,
        app.isPendingInstall,
        app.pendingInstallFilePath,
        app.updateCheckEnabled,
    ) {
        CompactStatusFlags(
            filterActive = !app.assetFilterRegex.isNullOrBlank() || app.fallbackToOlderReleases,
            variantPinned = !app.preferredAssetVariant.isNullOrBlank() && !app.preferredVariantStale,
            variantStale = app.preferredVariantStale,
            preReleaseOn = app.includePreReleases,
            pendingInstall = app.isPendingInstall && app.pendingInstallFilePath == null,
            readyToInstall = app.pendingInstallFilePath != null,
            updatesIgnored = !app.updateCheckEnabled,
        )
    }
}

@Composable
fun StatusDotCluster(
    flags: CompactStatusFlags,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    val items =
        buildList {
            if (flags.readyToInstall) add(StatusItem(DotShape.Ring, colors.primary))
            if (flags.pendingInstall) add(StatusItem(DotShape.Chevron, colors.primary))
            if (flags.variantStale) add(StatusItem(DotShape.Triangle, colors.error))
            if (flags.variantPinned) add(StatusItem(DotShape.Diamond, colors.primary))
            if (flags.filterActive) add(StatusItem(DotShape.Square, colors.primary))
            if (flags.preReleaseOn) add(StatusItem(DotShape.Circle, colors.primary))
            if (flags.updatesIgnored) add(StatusItem(DotShape.Bar, colors.outline))
        }

    if (items.isEmpty()) return

    Row(
        modifier = modifier.semantics { contentDescription = "" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            Canvas(modifier = Modifier.size(8.dp)) {
                drawShape(item.shape, item.color)
            }
        }
    }
}

private data class StatusItem(
    val shape: DotShape,
    val color: Color,
)

private enum class DotShape {
    Circle,
    Square,
    Triangle,
    Diamond,
    Ring,
    Chevron,
    Bar,
}

private fun DrawScope.drawShape(
    shape: DotShape,
    color: Color,
) {
    val s = size.minDimension
    when (shape) {
        DotShape.Circle -> {
            drawCircle(color = color, radius = s / 2f)
        }

        DotShape.Square -> {
            drawRect(
                color = color,
                size =
                    androidx.compose.ui.geometry
                        .Size(s, s),
            )
        }

        DotShape.Triangle -> {
            val path =
                Path().apply {
                    moveTo(s / 2f, 0f)
                    lineTo(s, s)
                    lineTo(0f, s)
                    close()
                }
            drawPath(path, color)
        }

        DotShape.Diamond -> {
            val path =
                Path().apply {
                    moveTo(s / 2f, 0f)
                    lineTo(s, s / 2f)
                    lineTo(s / 2f, s)
                    lineTo(0f, s / 2f)
                    close()
                }
            drawPath(path, color)
        }

        DotShape.Ring -> {
            drawCircle(
                color = color,
                radius = s / 2f - 1f,
                style = Stroke(width = 1.5f),
            )
        }

        DotShape.Chevron -> {
            val path =
                Path().apply {
                    moveTo(0f, s * 0.25f)
                    lineTo(s / 2f, s * 0.75f)
                    lineTo(s, s * 0.25f)
                }
            drawPath(path, color, style = Stroke(width = 1.5f))
        }

        DotShape.Bar -> {
            drawRect(
                color = color,
                topLeft =
                    androidx.compose.ui.geometry
                        .Offset(0f, s * 0.4f),
                size =
                    androidx.compose.ui.geometry
                        .Size(s, s * 0.2f),
            )
        }
    }
}

@Composable
fun buildCompactRowSemantics(
    appName: String,
    installedVersion: String,
    flags: CompactStatusFlags,
): String {
    val parts =
        buildList {
            add(appName)
            add(installedVersion)
            if (flags.readyToInstall) add(stringResource(Res.string.apps_compact_status_ready_to_install))
            if (flags.pendingInstall) add(stringResource(Res.string.apps_compact_status_pending_install))
            if (flags.variantStale) add(stringResource(Res.string.apps_compact_status_variant_stale))
            if (flags.variantPinned) add(stringResource(Res.string.apps_compact_status_variant_pinned))
            if (flags.filterActive) add(stringResource(Res.string.apps_compact_status_filter_active))
            if (flags.preReleaseOn) add(stringResource(Res.string.apps_compact_status_pre_release_on))
            if (flags.updatesIgnored) add(stringResource(Res.string.apps_compact_status_updates_ignored))
        }
    return parts.joinToString(", ")
}
