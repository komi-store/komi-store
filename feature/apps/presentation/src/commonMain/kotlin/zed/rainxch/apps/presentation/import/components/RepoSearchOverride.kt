package zed.rainxch.apps.presentation.import.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_search_empty
import zed.rainxch.githubstore.core.presentation.res.external_import_search_icon_label
import zed.rainxch.githubstore.core.presentation.res.external_import_search_placeholder_url

@Composable
fun RepoSearchOverride(
    query: String,
    results: ImmutableList<RepoSuggestionUi>,
    isSearching: Boolean,
    searchError: String?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPick: (RepoSuggestionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            KomiTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                error = searchError?.takeIf { it.isNotBlank() },
                placeholder = stringResource(Res.string.external_import_search_placeholder_url),
                trailing = {
                    KomiIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = stringResource(Res.string.external_import_search_icon_label),
                        onClick = onSubmit,
                        variant = KomiButtonVariant.Text,
                    )
                },
                onCommit = onSubmit,
            )

            if (isSearching) {
                KomiCircularProgress(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 56.dp)
                            .size(18.dp),
                )
            }
        }

        if (results.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                results.forEach { suggestion ->
                    RepoCandidateRow(
                        suggestion = suggestion,
                        onPick = onPick,
                    )
                }
            }
        } else if (query.isNotBlank() && !isSearching) {
            KomiText(
                text = stringResource(Res.string.external_import_search_empty),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
