package zed.rainxch.apps.presentation.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.LinkSuggestionUi
import zed.rainxch.core.domain.system.RepoMatchSource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.cd_back
import zed.rainxch.githubstore.core.presentation.res.link_smart_search_enter_manually
import zed.rainxch.githubstore.core.presentation.res.link_smart_search_failed
import zed.rainxch.githubstore.core.presentation.res.link_smart_search_no_matches
import zed.rainxch.githubstore.core.presentation.res.link_smart_search_searching
import zed.rainxch.githubstore.core.presentation.res.link_smart_search_title
import zed.rainxch.githubstore.core.presentation.res.match_source_fingerprint
import zed.rainxch.githubstore.core.presentation.res.match_source_manifest
import zed.rainxch.githubstore.core.presentation.res.match_source_manual
import zed.rainxch.githubstore.core.presentation.res.match_source_search
import zed.rainxch.githubstore.core.presentation.res.match_source_starred
import zed.rainxch.githubstore.core.presentation.res.retry
import zed.rainxch.githubstore.core.presentation.res.validating_repo

@Composable
internal fun SmartMatchStep(
    selectedApp: DeviceAppUi?,
    loading: Boolean,
    suggestions: ImmutableList<LinkSuggestionUi>,
    error: String?,
    isValidating: Boolean,
    validationStatus: String?,
    onSuggestionSelect: (LinkSuggestionUi) -> Unit,
    onEnterUrlManually: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                onClick = onBack,
                enabled = !isValidating,
            )

            KomiText(
                text = stringResource(Res.string.link_smart_search_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (selectedApp != null) {
            KomiText(
                text = selectedApp.appName,
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            KomiText(
                text = selectedApp.packageName,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        when {
            loading -> ProgressLine(text = stringResource(Res.string.link_smart_search_searching))
            isValidating -> ProgressLine(text = validationStatus ?: stringResource(Res.string.validating_repo))
            error != null -> SearchFailed(onRetry = onRetry)
            suggestions.isEmpty() -> NoMatches()
            else -> SuggestionList(suggestions = suggestions, onSuggestionSelect = onSuggestionSelect)
        }

        Spacer(Modifier.height(12.dp))

        KomiButton(
            onClick = onEnterUrlManually,
            label = stringResource(Res.string.link_smart_search_enter_manually),
            variant = KomiButtonVariant.Tonal,
            enabled = !isValidating,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProgressLine(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiCircularProgress(
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.width(12.dp))

        KomiText(
            text = text,
            role = KomiTextRole.Body,
            color = LocalPersonality.current.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchFailed(onRetry: () -> Unit) {
    Column {
        KomiText(
            text = stringResource(Res.string.link_smart_search_failed),
            role = KomiTextRole.Body,
            color = LocalPersonality.current.colors.error,
        )

        Spacer(Modifier.height(8.dp))

        KomiButton(
            onClick = onRetry,
            label = stringResource(Res.string.retry),
            variant = KomiButtonVariant.Tonal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NoMatches() {
    KomiText(
        text = stringResource(Res.string.link_smart_search_no_matches),
        role = KomiTextRole.Body,
        color = LocalPersonality.current.colors.onSurfaceVariant,
    )
}

@Composable
private fun SuggestionList(
    suggestions: ImmutableList<LinkSuggestionUi>,
    onSuggestionSelect: (LinkSuggestionUi) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(320.dp),
    ) {
        items(
            items = suggestions,
            key = { "${it.sourceHost ?: "github"}|${it.owner}/${it.repo}" },
        ) { suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                onClick = { onSuggestionSelect(suggestion) },
            )

            KomiHorizontalDivider(
                color = LocalPersonality.current.colors.divider,
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: LinkSuggestionUi,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = "${suggestion.owner}/${suggestion.repo}",
                role = KomiTextRole.Label,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            val description = suggestion.description?.takeIf { it.isNotBlank() }
            if (description != null) {
                KomiText(
                    text = description,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SuggestionMeta(suggestion = suggestion)
        }
    }
}

@Composable
private fun SuggestionMeta(suggestion: LinkSuggestionUi) {
    val colors = LocalPersonality.current.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        SourceHostChip(sourceHost = suggestion.sourceHost)

        Spacer(Modifier.width(6.dp))

        SuggestionChip(
            text = stringResource(suggestion.source.label()),
            background = colors.primaryContainer,
            content = colors.onPrimaryContainer,
        )

        Spacer(Modifier.width(6.dp))

        KomiText(
            text = "${suggestion.confidencePercent}%",
            role = KomiTextRole.Caption,
            color = colors.onSurfaceVariant,
        )

        suggestion.stars?.let { stars ->
            Spacer(Modifier.width(8.dp))

            KomiIcon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )

            Spacer(Modifier.width(2.dp))

            KomiText(
                text = "$stars",
                role = KomiTextRole.Caption,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceHostChip(sourceHost: String?) {
    val colors = LocalPersonality.current.colors
    when {
        sourceHost == null -> {
            SuggestionChip("GitHub", colors.surfaceVariant, colors.onSurfaceVariant)
        }

        sourceHost.equals("codeberg.org", ignoreCase = true) -> {
            SuggestionChip("Codeberg", colors.primaryContainer, colors.onPrimaryContainer)
        }

        else -> {
            SuggestionChip(sourceHost, colors.primaryContainer, colors.onPrimaryContainer)
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    background: Color,
    content: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                .background(background),
    ) {
        KomiText(
            text = text,
            role = KomiTextRole.Caption,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun RepoMatchSource.label(): StringResource =
    when (this) {
        RepoMatchSource.MANIFEST -> Res.string.match_source_manifest
        RepoMatchSource.FINGERPRINT -> Res.string.match_source_fingerprint
        RepoMatchSource.SEARCH -> Res.string.match_source_search
        RepoMatchSource.MANUAL -> Res.string.match_source_manual
        RepoMatchSource.FORGEJO_SEARCH -> Res.string.match_source_search
        RepoMatchSource.STARRED -> Res.string.match_source_starred
    }
