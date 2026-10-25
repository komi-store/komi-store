package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.reviews.CompactRating
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality

@Composable
fun AppGroupHeader(
    app: InstalledAppUi,
    modifier: Modifier = Modifier,
    ratingAverage: Double? = null,
    ratingCount: Int = 0,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GitHubStoreImage(
            imageModel = { app.repoOwnerAvatarUrl },
            modifier =
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(shape.cornerSmall)),
        )

        KomiText(
            text = "${app.repoOwner}/${app.repoName}",
            role = KomiTextRole.LabelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        app.sourceHost?.let { SourceChip(host = it) }
        ratingAverage?.let { CompactRating(average = it, count = ratingCount) }
    }
}
