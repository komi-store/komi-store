package zed.rainxch.details.presentation.components.states

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.error_state_go_back
import zed.rainxch.githubstore.core.presentation.res.error_state_subtitle_generic
import zed.rainxch.githubstore.core.presentation.res.error_state_subtitle_not_found
import zed.rainxch.githubstore.core.presentation.res.error_state_subtitle_offline
import zed.rainxch.githubstore.core.presentation.res.error_state_subtitle_rate_limit
import zed.rainxch.githubstore.core.presentation.res.error_state_title_generic
import zed.rainxch.githubstore.core.presentation.res.error_state_title_not_found
import zed.rainxch.githubstore.core.presentation.res.error_state_title_offline
import zed.rainxch.githubstore.core.presentation.res.error_state_title_rate_limit
import zed.rainxch.githubstore.core.presentation.res.retry

@Composable
fun ErrorState(
    errorMessage: String,
    onAction: (DetailsAction) -> Unit,
) {
    val kind = classify(errorMessage)
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        KomiSurface(
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val colors = LocalPersonality.current.colors
                val shape = LocalPersonality.current.shape
                IconBadge(icon = kind.icon, tint = kind.tint())

                KomiText(
                    text = stringResource(kind.titleRes),
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )

                KomiText(
                    text = stringResource(kind.subtitleRes),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (kind == ErrorKind.RATE_LIMIT && errorMessage.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    KomiText(
                        text = errorMessage,
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(shape.corner))
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KomiButton(
                        onClick = { onAction(DetailsAction.OnNavigateBackClick) },
                        label = stringResource(Res.string.error_state_go_back),
                        variant = KomiButtonVariant.Text,
                        modifier = Modifier.weight(1f),
                    )
                    KomiButton(
                        onClick = { onAction(DetailsAction.Retry) },
                        label = stringResource(Res.string.retry),
                        variant = KomiButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    val shape = LocalPersonality.current.shape
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(shape.cornerSmall))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        KomiIcon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(36.dp),
        )
    }
}

private enum class ErrorKind(
    val icon: ImageVector,
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val hideRawMessage: Boolean = false,
) {
    OFFLINE(
        icon = Icons.Outlined.CloudOff,
        titleRes = Res.string.error_state_title_offline,
        subtitleRes = Res.string.error_state_subtitle_offline,
        hideRawMessage = true,
    ),
    RATE_LIMIT(
        icon = Icons.Outlined.HourglassEmpty,
        titleRes = Res.string.error_state_title_rate_limit,
        subtitleRes = Res.string.error_state_subtitle_rate_limit,
        hideRawMessage = false,
    ),
    NOT_FOUND(
        icon = Icons.Outlined.SearchOff,
        titleRes = Res.string.error_state_title_not_found,
        subtitleRes = Res.string.error_state_subtitle_not_found,
        hideRawMessage = true,
    ),
    GENERIC(
        icon = Icons.Outlined.SentimentDissatisfied,
        titleRes = Res.string.error_state_title_generic,
        subtitleRes = Res.string.error_state_subtitle_generic,
    ),
}

@Composable
private fun ErrorKind.tint(): Color {
    val colors = LocalPersonality.current.colors
    return when (this) {
        ErrorKind.OFFLINE -> colors.primary
        ErrorKind.RATE_LIMIT -> colors.primary
        ErrorKind.NOT_FOUND -> colors.primary
        ErrorKind.GENERIC -> colors.error
    }
}

private fun classify(message: String): ErrorKind {
    val lower = message.lowercase()
    return when {
        lower.contains("rate limit") || lower.contains("retry in") || lower.contains("429") ->
            ErrorKind.RATE_LIMIT
        lower.contains("404") || lower.contains("not found") -> ErrorKind.NOT_FOUND
        lower.contains("unable to resolve host") ||
            lower.contains("unknownhost") ||
            lower.contains("connection refused") ||
            lower.contains("network is unreachable") ||
            lower.contains("timeout") ||
            lower.contains("offline") -> ErrorKind.OFFLINE
        else -> ErrorKind.GENERIC
    }
}
