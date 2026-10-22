package zed.rainxch.apps.presentation.import.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.import.model.CandidateUi
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_list_add_manually
import zed.rainxch.githubstore.core.presentation.res.external_import_list_remaining

@Composable
fun WizardList(
    cards: ImmutableList<CandidateUi>,
    expandedPackages: ImmutableSet<String>,
    activeSearchPackage: String?,
    searchQuery: String,
    searchResults: ImmutableList<RepoSuggestionUi>,
    isSearching: Boolean,
    searchError: String?,
    onToggleExpanded: (packageName: String) -> Unit,
    onPick: (packageName: String, RepoSuggestionUi) -> Unit,
    onSkip: (packageName: String) -> Unit,
    onLink: (packageName: String) -> Unit,
    onSearchQueryChange: (packageName: String, query: String) -> Unit,
    onSearchSubmit: (packageName: String) -> Unit,
    onAddManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "progress-header") {
            val colors = LocalPersonality.current.colors
            val shape = LocalPersonality.current.shape
            Box(
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .clip(RoundedCornerShape(shape.corner))
                    .background(colors.surfaceVariant),
            ) {
                KomiText(
                    text = pluralStringResource(
                        Res.plurals.external_import_list_remaining,
                        cards.size,
                        cards.size,
                    ),
                    role = KomiTextRole.LabelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        items(
            items = cards,
            key = { it.packageName },
        ) { card ->
            val expanded = card.packageName in expandedPackages
            val isActive = activeSearchPackage == card.packageName
            CandidateCard(
                candidate = card,
                expanded = expanded,
                searchQuery = if (isActive) searchQuery else "",
                searchResults = if (isActive) searchResults else persistentListOf(),
                isSearching = isActive && isSearching,
                searchError = if (isActive) searchError else null,
                onToggleExpanded = { onToggleExpanded(card.packageName) },
                onPick = { suggestion -> onPick(card.packageName, suggestion) },
                onSkip = { onSkip(card.packageName) },
                onLink = { onLink(card.packageName) },
                onSearchQueryChange = { q -> onSearchQueryChange(card.packageName, q) },
                onSearchSubmit = { onSearchSubmit(card.packageName) },
            )
        }

        item(key = "add-manually-footer") {
            KomiButton(
                onClick = onAddManually,
                label = stringResource(Res.string.external_import_list_add_manually),
                variant = KomiButtonVariant.Text,
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
