package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.model.LogResult
import zed.rainxch.details.presentation.utils.asText
import zed.rainxch.githubstore.core.presentation.res.*

fun LazyListScope.logs(state: DetailsState) {
    item {
        val colors = LocalPersonality.current.colors
        Spacer(Modifier.height(20.dp))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.install_logs),
                role = KomiTextRole.CardTitle,
                color = colors.onBackground,
            )
        }
    }

    itemsIndexed(
        items = state.installLogs,
        key = { index, log -> "$index|${log.timeIso}|${log.assetName}" },
        contentType = { _, _ -> "install_log" },
    ) { _, log ->
        val colors = LocalPersonality.current.colors
        KomiText(
            text = "> ${log.result.asText()}: ${log.assetName}",
            role = KomiTextRole.Mono,
            color =
                if (log.result is LogResult.Error) {
                    colors.error
                } else {
                    colors.outline
                },
        )
    }
}
