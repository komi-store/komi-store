package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality

@Composable
fun AppGroupHeader(
    app: InstalledAppUi,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GitHubStoreImage(
            imageModel = { app.repoOwnerAvatarUrl },
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(shape.cornerSmall)),
        )

        KomiText(
            text = "${app.repoOwner}/${app.repoName}",
            role = KomiTextRole.Label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            uppercase = false,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        app.sourceHost?.let { SourceChip(host = it) }
    }
}

@Composable
fun AppGroupCard(
    header: InstalledAppUi,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = RoundedCornerShape(LocalPersonality.current.shape.corner)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = colors.outline, shape = shape)
            .background(colors.surface),
    ) {
        AppGroupHeader(
            app = header,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 2.dp),
        )
        content()
    }
}

@Composable
fun AppGroupDivider(modifier: Modifier = Modifier) {
    KomiHorizontalDivider(modifier = modifier.padding(start = 74.dp, end = 14.dp))
}

@Composable
fun AppGroupStack(
    header: InstalledAppUi,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppGroupHeader(
            app = header,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        content()
    }
}
