package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.domain.model.ImportFormat
import zed.rainxch.apps.domain.model.ImportResult
import zed.rainxch.apps.presentation.model.ImportSummaryBucket
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.import_summary_already_tracked
import zed.rainxch.githubstore.core.presentation.res.import_summary_close
import zed.rainxch.githubstore.core.presentation.res.import_summary_collapse
import zed.rainxch.githubstore.core.presentation.res.import_summary_expand
import zed.rainxch.githubstore.core.presentation.res.import_summary_failed
import zed.rainxch.githubstore.core.presentation.res.import_summary_format_native
import zed.rainxch.githubstore.core.presentation.res.import_summary_format_obtainium
import zed.rainxch.githubstore.core.presentation.res.import_summary_imported
import zed.rainxch.githubstore.core.presentation.res.import_summary_non_github
import zed.rainxch.githubstore.core.presentation.res.import_summary_non_github_caption
import zed.rainxch.githubstore.core.presentation.res.import_summary_not_installed
import zed.rainxch.githubstore.core.presentation.res.import_summary_not_installed_caption
import zed.rainxch.githubstore.core.presentation.res.import_summary_title
import zed.rainxch.githubstore.core.presentation.res.import_summary_unknown_caption
import zed.rainxch.githubstore.core.presentation.res.import_summary_unknown_format

@Composable
fun ImportSummarySheet(
    summary: ImportResult,
    expandedBuckets: ImmutableSet<ImportSummaryBucket>,
    onToggleBucket: (ImportSummaryBucket) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    if (summary.sourceFormat == ImportFormat.UNKNOWN) {
        UnknownFormatSheet(
            preview = summary.unknownFormatPreview,
            onDismiss = onDismiss,
        )
        return
    }

    KomiSheet(
        onDismiss = onDismiss,
        placement = KomiSheetPlacement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val formatLabel = when (summary.sourceFormat) {
                ImportFormat.OBTAINIUM -> stringResource(Res.string.import_summary_format_obtainium)
                else -> stringResource(Res.string.import_summary_format_native)
            }
            KomiText(
                text = stringResource(Res.string.import_summary_title, formatLabel),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )

            val importedPart = stringResource(Res.string.import_summary_imported, summary.imported)
            val skippedPart = stringResource(Res.string.import_summary_already_tracked, summary.skipped)
            val nonGitHubPart = if (summary.nonGitHubSkipped > 0) {
                stringResource(Res.string.import_summary_non_github, summary.nonGitHubSkipped)
            } else {
                null
            }
            val notInstalledPart = if (summary.notInstalled > 0) {
                stringResource(Res.string.import_summary_not_installed, summary.notInstalled)
            } else {
                null
            }
            val failedPart = stringResource(Res.string.import_summary_failed, summary.failed)
            val announcement = listOfNotNull(importedPart, skippedPart, notInstalledPart, nonGitHubPart, failedPart)
                .joinToString(", ")
            KomiText(
                text = "",
                role = KomiTextRole.Body,
                color = colors.onSurface,
                modifier = Modifier
                    .height(0.dp)
                    .semantics {
                        contentDescription = announcement
                        liveRegion = LiveRegionMode.Polite
                    },
            )

            SummaryBucket(
                icon = Icons.Outlined.CheckCircle,
                tint = colors.primary,
                title = stringResource(Res.string.import_summary_imported, summary.imported),
                items = summary.importedItems,
                expanded = ImportSummaryBucket.IMPORTED in expandedBuckets,
                onToggle = { onToggleBucket(ImportSummaryBucket.IMPORTED) },
            )

            if (summary.skipped > 0) {
                SummaryBucket(
                    icon = Icons.Outlined.RemoveCircleOutline,
                    tint = colors.onSurfaceVariant,
                    title = stringResource(Res.string.import_summary_already_tracked, summary.skipped),
                    items = summary.skippedItems,
                    expanded = ImportSummaryBucket.SKIPPED in expandedBuckets,
                    onToggle = { onToggleBucket(ImportSummaryBucket.SKIPPED) },
                )
            }

            if (summary.notInstalled > 0) {
                SummaryBucket(
                    icon = Icons.Outlined.InstallMobile,
                    tint = colors.onSurfaceVariant,
                    title = stringResource(Res.string.import_summary_not_installed, summary.notInstalled),
                    caption = stringResource(Res.string.import_summary_not_installed_caption),
                    items = summary.notInstalledItems,
                    expanded = ImportSummaryBucket.NOT_INSTALLED in expandedBuckets,
                    onToggle = { onToggleBucket(ImportSummaryBucket.NOT_INSTALLED) },
                )
            }

            if (summary.nonGitHubSkipped > 0) {
                SummaryBucket(
                    icon = Icons.Outlined.WarningAmber,
                    tint = colors.primary,
                    title = stringResource(Res.string.import_summary_non_github, summary.nonGitHubSkipped),
                    caption = stringResource(Res.string.import_summary_non_github_caption),
                    items = summary.nonGitHubItems,
                    expanded = ImportSummaryBucket.NON_GITHUB in expandedBuckets,
                    onToggle = { onToggleBucket(ImportSummaryBucket.NON_GITHUB) },
                )
            }

            if (summary.failed > 0) {
                SummaryBucket(
                    icon = Icons.Outlined.ErrorOutline,
                    tint = colors.error,
                    title = stringResource(Res.string.import_summary_failed, summary.failed),
                    items = summary.failedItems,
                    expanded = ImportSummaryBucket.FAILED in expandedBuckets,
                    onToggle = { onToggleBucket(ImportSummaryBucket.FAILED) },
                )
            }

            KomiButton(
                onClick = onDismiss,
                label = stringResource(Res.string.import_summary_close),
                variant = KomiButtonVariant.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun UnknownFormatSheet(
    preview: String?,
    onDismiss: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    KomiSheet(
        onDismiss = onDismiss,
        placement = KomiSheetPlacement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KomiIcon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = colors.primary,
                )

                Spacer(Modifier.width(8.dp))

                KomiText(
                    text = stringResource(Res.string.import_summary_unknown_format),
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                )
            }

            KomiText(
                text = stringResource(Res.string.import_summary_unknown_caption),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )

            if (!preview.isNullOrBlank()) {
                SelectionContainer {
                    KomiText(
                        text = preview,
                        role = KomiTextRole.Mono,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    )
                }
            }

            KomiButton(
                onClick = onDismiss,
                label = stringResource(Res.string.import_summary_close),
                variant = KomiButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SummaryBucket(
    icon: ImageVector,
    tint: Color,
    title: String,
    items: ImmutableList<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    caption: String? = null,
) {
    val colors = LocalPersonality.current.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            KomiIcon(imageVector = icon, contentDescription = null, tint = tint)

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = title,
                    role = KomiTextRole.ItemTitle,
                    color = colors.onSurface,
                )

                if (!caption.isNullOrBlank()) {
                    KomiText(
                        text = caption,
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            if (items.isNotEmpty()) {
                val expandLabel = stringResource(
                    if (expanded) Res.string.import_summary_collapse else Res.string.import_summary_expand,
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onToggle() }
                        .semantics { contentDescription = expandLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    KomiIcon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
        }

        if (expanded && items.isNotEmpty()) {
            KomiHorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(start = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items.size) { idx ->
                    KomiText(
                        text = items[idx],
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

