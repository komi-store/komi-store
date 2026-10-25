package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.presentation.components.InstalledAppIcon
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.reviews.CompactRating
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_version_update_cd

// How a Library row's status line reads.
enum class LibraryStatusTone {
    Plain,
    Accent,
    Error,
    Downgrade,
}

// A Library row's status line. With a target it names an update or install: the text, an arrow, the target.
data class LibraryStatus(
    val text: String,
    val tone: LibraryStatusTone,
    val target: String? = null,
)

// A Library row's progress bar, when it shows one.
sealed interface LibraryProgress {
    data object Working : LibraryProgress

    data class Download(
        val fraction: Float,
        val paused: Boolean,
    ) : LibraryProgress
}

// The app a row is about, as the row shows it.
data class LibraryRowApp(
    val packageName: String,
    val name: String,
    val owner: String,
    val avatarUrl: String?,
    val apkFilePath: String?,
    val sourceHost: String?,
    val ratingAverage: Double? = null,
    val ratingCount: Int = 0,
)

// Every Library item is this row: the icon, the name, the owner and source, one status line, tags
// for the app's own settings, a progress bar while something runs, and its actions at the end.
// On its own it sits on a card; inside a repo's group the group is the card.
@Composable
fun LibraryRow(
    app: LibraryRowApp,
    status: LibraryStatus,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tags: ImmutableList<String> = persistentListOf(),
    progress: LibraryProgress? = null,
    framed: Boolean = true,
    actions: @Composable RowScope.() -> Unit,
) {
    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        LibraryRowContent(
            app = app,
            status = status,
            tags = tags,
            progress = progress,
            showOwner = framed,
            actions = actions,
            modifier = contentModifier,
        )
    }
    if (framed) {
        KomiSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
            content(Modifier)
        }
    } else {
        content(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryRowContent(
    app: LibraryRowApp,
    status: LibraryStatus,
    tags: ImmutableList<String>,
    progress: LibraryProgress?,
    showOwner: Boolean,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Column(modifier = modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InstalledAppIcon(
                packageName = app.packageName,
                appName = app.name,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(LocalPersonality.current.shape.corner)),
                apkFilePath = app.apkFilePath,
                avatarFallbackUrl = app.avatarUrl,
            )

            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = app.name,
                    role = KomiTextRole.ItemTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OwnerLine(
                    owner = app.owner.takeIf { showOwner },
                    sourceHost = app.sourceHost,
                    ratingAverage = app.ratingAverage.takeIf { showOwner && app.ratingCount > 0 },
                    ratingCount = app.ratingCount,
                )
                StatusLine(status = status, modifier = Modifier.padding(top = 2.dp))
            }

            // Centred on the icon, which sits at the top when tags make the row taller.
            Row(
                modifier = Modifier.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions,
            )
        }

        // Under the name, but as wide as the row: the actions would squeeze them otherwise.
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(start = 60.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tags.forEach { tag -> LibraryTag(text = tag) }
            }
        }

        when (progress) {
            null -> {}

            LibraryProgress.Working -> {
                Spacer(Modifier.height(10.dp))
                KomiLinearProgress(modifier = Modifier.fillMaxWidth())
            }

            is LibraryProgress.Download -> {
                Spacer(Modifier.height(10.dp))
                KomiLinearProgress(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (progress.paused) colors.onSurfaceVariant else colors.primary,
                )
            }
        }
    }
}

@Composable
private fun OwnerLine(
    owner: String?,
    sourceHost: String?,
    ratingAverage: Double?,
    ratingCount: Int,
) {
    if (owner == null && sourceHost == null && ratingAverage == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        owner?.let {
            KomiText(
                text = it,
                role = KomiTextRole.Caption,
                color = LocalPersonality.current.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        sourceHost?.let { SourceChip(host = it) }
        ratingAverage?.let { CompactRating(average = it, count = ratingCount) }
    }
}

// The arrow is inline content, so a long line still wraps onto the second line.
@Composable
private fun StatusLine(
    status: LibraryStatus,
    modifier: Modifier = Modifier,
) {
    val color = status.tone.color()
    val target = status.target
    if (target == null) {
        KomiText(
            text = status.text,
            role = KomiTextRole.Body,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    } else {
        val description = stringResource(Res.string.apps_version_update_cd, status.text, target)
        val arrow =
            remember(color) {
                persistentMapOf(
                    STATUS_ARROW to
                        InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                            KomiIcon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = color,
                            )
                        },
                )
            }
        KomiText(
            text =
                buildAnnotatedString {
                    append(status.text)
                    append(' ')
                    appendInlineContent(STATUS_ARROW)
                    append(' ')
                    append(target)
                },
            role = KomiTextRole.Body,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            inlineContent = arrow,
            modifier = modifier.semantics { contentDescription = description },
        )
    }
}

private const val STATUS_ARROW = "status_arrow"

@Composable
private fun LibraryTag(text: String) {
    val personality = LocalPersonality.current
    KomiText(
        text = text,
        role = KomiTextRole.LabelSmall,
        color = personality.colors.onSurfaceVariant,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(personality.shape.cornerSmall))
                .background(personality.colors.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun LibraryStatusTone.color(): Color {
    val colors = LocalPersonality.current.colors
    return when (this) {
        LibraryStatusTone.Plain -> colors.onSurfaceVariant
        LibraryStatusTone.Accent -> colors.primary
        LibraryStatusTone.Error -> colors.error
        LibraryStatusTone.Downgrade -> DowngradeWarningColor
    }
}

// Several apps from one repo: one card with the repo above its apps' rows.
@Composable
fun LibraryGroup(
    header: InstalledAppUi,
    modifier: Modifier = Modifier,
    ratingAverage: Double? = null,
    ratingCount: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    KomiSurface(modifier = modifier.fillMaxWidth()) {
        Column {
            AppGroupHeader(
                app = header,
                ratingAverage = ratingAverage.takeIf { ratingCount > 0 },
                ratingCount = ratingCount,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 2.dp),
            )
            content()
        }
    }
}

@Composable
fun LibraryGroupDivider(modifier: Modifier = Modifier) {
    KomiHorizontalDivider(modifier = modifier.padding(start = 74.dp, end = 14.dp))
}
