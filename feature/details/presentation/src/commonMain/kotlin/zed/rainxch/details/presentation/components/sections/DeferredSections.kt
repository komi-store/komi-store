package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.details_sections_deferred_body
import zed.rainxch.githubstore.core.presentation.res.details_sections_deferred_load
import zed.rainxch.githubstore.core.presentation.res.details_sections_deferred_title

fun LazyListScope.deferredSections(
    isLoading: Boolean,
    onLoad: () -> Unit,
) {
    item(key = "details-deferred-sections") {
        DeferredSectionsCard(isLoading = isLoading, onLoad = onLoad)
    }
}

@Composable
private fun DeferredSectionsCard(
    isLoading: Boolean,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Flat,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomiIcon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            KomiText(
                text = stringResource(Res.string.details_sections_deferred_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
            KomiText(
                text = stringResource(Res.string.details_sections_deferred_body),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
            KomiButton(
                label = stringResource(Res.string.details_sections_deferred_load),
                onClick = onLoad,
                enabled = !isLoading,
                loading = isLoading,
            )
        }
    }
}
