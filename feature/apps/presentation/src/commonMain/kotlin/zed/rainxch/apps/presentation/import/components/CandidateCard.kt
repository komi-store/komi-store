package zed.rainxch.apps.presentation.import.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.InstalledAppIcon
import zed.rainxch.apps.presentation.import.model.CandidateUi
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi
import zed.rainxch.apps.presentation.import.util.LocalReducedMotion
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_card_action_less
import zed.rainxch.githubstore.core.presentation.res.external_import_card_action_link
import zed.rainxch.githubstore.core.presentation.res.external_import_card_action_more
import zed.rainxch.githubstore.core.presentation.res.external_import_card_action_skip
import zed.rainxch.githubstore.core.presentation.res.external_import_card_collapse_label
import zed.rainxch.githubstore.core.presentation.res.external_import_card_expand_label
import zed.rainxch.githubstore.core.presentation.res.external_import_card_installer_chip
import zed.rainxch.githubstore.core.presentation.res.external_import_card_owner_byline
import zed.rainxch.githubstore.core.presentation.res.external_import_card_preselect_known_prefix
import zed.rainxch.githubstore.core.presentation.res.external_import_card_preselect_unknown
import zed.rainxch.githubstore.core.presentation.res.external_import_match_confidence_chip

@Composable
fun CandidateCard(
    candidate: CandidateUi,
    expanded: Boolean,
    searchQuery: String,
    searchResults: ImmutableList<RepoSuggestionUi>,
    isSearching: Boolean,
    searchError: String?,
    onToggleExpanded: () -> Unit,
    onPick: (RepoSuggestionUi) -> Unit,
    onSkip: () -> Unit,
    onLink: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val reducedMotion = LocalReducedMotion.current
    val clickActionLabel = if (expanded) {
        stringResource(Res.string.external_import_card_collapse_label)
    } else {
        stringResource(Res.string.external_import_card_expand_label)
    }

    KomiSurface(
        onClick = onToggleExpanded,
        contentPadding = PaddingValues(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                onClick(label = clickActionLabel, action = null)
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InstalledAppIcon(
                    packageName = candidate.packageName,
                    appName = candidate.appLabel,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(shape.corner)),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    KomiText(
                        text = candidate.appLabel,
                        role = KomiTextRole.Title,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        uppercase = false,
                    )

                    KomiText(
                        text = candidate.packageName,
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        uppercase = false,
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .background(colors.primaryContainer),
                    ) {
                        KomiText(
                            text = stringResource(
                                Res.string.external_import_card_installer_chip,
                                candidate.installerLabel,
                            ),
                            role = KomiTextRole.Label,
                            fontSize = 11.sp,
                            color = colors.onPrimaryContainer,
                            uppercase = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            PreselectedRow(suggestion = candidate.preselectedSuggestion)

            if (!expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (candidate.preselectedSuggestion != null) {
                        KomiButton(
                            onClick = onLink,
                            label = stringResource(Res.string.external_import_card_action_link),
                            variant = KomiButtonVariant.Primary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    KomiButton(
                        onClick = onToggleExpanded,
                        label = stringResource(Res.string.external_import_card_action_more),
                        variant = KomiButtonVariant.Text,
                        trailingIcon = Icons.Default.KeyboardArrowDown,
                        modifier = if (candidate.preselectedSuggestion != null) {
                            Modifier
                        } else {
                            Modifier.weight(1f)
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter =
                    if (reducedMotion) fadeIn() else fadeIn() + expandVertically(),
                exit =
                    if (reducedMotion) fadeOut() else fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (candidate.suggestions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            candidate.suggestions.take(3).forEach { suggestion ->
                                RepoCandidateRow(
                                    suggestion = suggestion,
                                    onPick = onPick,
                                )
                            }
                        }
                    }

                    RepoSearchOverride(
                        query = searchQuery,
                        results = searchResults,
                        isSearching = isSearching,
                        searchError = searchError,
                        onQueryChange = onSearchQueryChange,
                        onSubmit = onSearchSubmit,
                        onPick = onPick,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiButton(
                            onClick = onSkip,
                            label = stringResource(Res.string.external_import_card_action_skip),
                            variant = KomiButtonVariant.Outline,
                            modifier = Modifier.weight(1f),
                        )

                        KomiButton(
                            onClick = onToggleExpanded,
                            label = stringResource(Res.string.external_import_card_action_less),
                            variant = KomiButtonVariant.Text,
                            trailingIcon = Icons.Default.KeyboardArrowUp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreselectedRow(suggestion: RepoSuggestionUi?) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val containerColor =
        if (suggestion != null) {
            colors.primaryContainer
        } else {
            colors.surfaceVariant
        }
    val contentColor =
        if (suggestion != null) {
            colors.onPrimaryContainer
        } else {
            colors.onSurfaceVariant
        }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shape.corner))
            .background(containerColor),
    ) {
        if (suggestion == null) {
            KomiText(
                text = stringResource(Res.string.external_import_card_preselect_unknown),
                role = KomiTextRole.Body,
                color = contentColor,
                uppercase = false,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                KomiText(
                    text = stringResource(Res.string.external_import_card_preselect_known_prefix),
                    role = KomiTextRole.Label,
                    fontSize = 11.sp,
                    color = contentColor,
                )

                KomiText(
                    text = suggestion.repo,
                    role = KomiTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    uppercase = false,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KomiText(
                        text = stringResource(
                            Res.string.external_import_card_owner_byline,
                            suggestion.owner,
                        ),
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        uppercase = false,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    KomiText(
                        text = stringResource(
                            Res.string.external_import_match_confidence_chip,
                            suggestion.confidencePercent,
                        ),
                        role = KomiTextRole.Label,
                        fontSize = 11.sp,
                        color = contentColor,
                        uppercase = false,
                    )
                }
            }
        }
    }
}
