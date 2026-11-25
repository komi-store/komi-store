package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.cd_back
import zed.rainxch.githubstore.core.presentation.res.enter_repo_url
import zed.rainxch.githubstore.core.presentation.res.link_and_track
import zed.rainxch.githubstore.core.presentation.res.link_app_title
import zed.rainxch.githubstore.core.presentation.res.repo_url_hint
import zed.rainxch.githubstore.core.presentation.res.validating_repo

@Composable
internal fun EnterUrlStep(
    selectedApp: DeviceAppUi?,
    repoUrl: String,
    isValidating: Boolean,
    validationError: String?,
    validationStatus: String?,
    onUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                onClick = onBack,
            )

            KomiText(
                text = stringResource(Res.string.link_app_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (selectedApp != null) {
            SelectedAppSummary(app = selectedApp)
        }

        Spacer(Modifier.height(16.dp))

        KomiTextField(
            value = repoUrl,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.enter_repo_url),
            placeholder = stringResource(Res.string.repo_url_hint),
            error = validationError,
        )

        Spacer(Modifier.height(20.dp))

        KomiButton(
            onClick = onConfirm,
            label =
                if (isValidating) {
                    stringResource(Res.string.validating_repo)
                } else {
                    stringResource(Res.string.link_and_track)
                },
            variant = KomiButtonVariant.Tonal,
            enabled = repoUrl.isNotBlank() && !isValidating,
            loading = isValidating,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isValidating && validationStatus != null) {
            Spacer(Modifier.height(8.dp))

            KomiText(
                text = validationStatus,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SelectedAppSummary(app: DeviceAppUi) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalPersonality.current.shape.corner))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = app.appName,
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )

            KomiText(
                text = app.packageName,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
        }

        app.versionName?.let {
            KomiText(
                text = it,
                role = KomiTextRole.Caption,
                color = colors.primary,
            )
        }
    }
}
