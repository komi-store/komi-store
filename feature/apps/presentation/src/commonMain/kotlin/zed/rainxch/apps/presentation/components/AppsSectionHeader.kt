package zed.rainxch.apps.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_section_collapse
import zed.rainxch.githubstore.core.presentation.res.apps_section_count_suffix
import zed.rainxch.githubstore.core.presentation.res.apps_section_expand
import zed.rainxch.githubstore.core.presentation.res.apps_section_header_cd
import zed.rainxch.githubstore.core.presentation.res.apps_section_header_static_cd
import zed.rainxch.githubstore.core.presentation.res.apps_section_state_collapsed
import zed.rainxch.githubstore.core.presentation.res.apps_section_state_expanded

@Composable
fun AppsSectionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedStateLabel = stringResource(Res.string.apps_section_state_expanded)
    val collapsedStateLabel = stringResource(Res.string.apps_section_state_collapsed)
    val colors = LocalPersonality.current.colors
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = LocalPersonality.current.motion.fastSpatial(),
        label = "section-chevron",
    )

    val rowSemantic =
        if (isExpanded) {
            stringResource(Res.string.apps_section_collapse)
        } else {
            stringResource(Res.string.apps_section_expand)
        }
    val collapsibleContentDescription =
        stringResource(Res.string.apps_section_header_cd, title, count, rowSemantic)
    val staticContentDescription =
        stringResource(Res.string.apps_section_header_static_cd, title, count)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(top = 20.dp, bottom = 6.dp)
                .let { base ->
                    if (collapsible) {
                        base
                            .clickable(onClick = onToggle)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                heading()
                                contentDescription = collapsibleContentDescription
                                stateDescription =
                                    if (isExpanded) {
                                        expandedStateLabel
                                    } else {
                                        collapsedStateLabel
                                    }
                            }
                    } else {
                        base.semantics(mergeDescendants = true) {
                            heading()
                            contentDescription = staticContentDescription
                        }
                    }
                },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomiText(
                text = title,
                role = KomiTextRole.SectionTitle,
                color = colors.onBackground,
            )

            KomiText(
                text = stringResource(Res.string.apps_section_count_suffix, count),
                role = KomiTextRole.Label,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            if (collapsible) {
                KomiIcon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier =
                        Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                rotationZ = rotation
                            },
                )
            }
        }
    }
}
