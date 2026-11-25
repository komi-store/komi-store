package zed.rainxch.tweaks.presentation.mirror

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.mirror.MirrorStatus
import zed.rainxch.core.domain.model.mirror.MirrorType
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.inputs.KomiRadioButton
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.mirror_custom_label
import zed.rainxch.githubstore.core.presentation.res.mirror_fastest_badge
import zed.rainxch.githubstore.core.presentation.res.mirror_fastest_chosen
import zed.rainxch.githubstore.core.presentation.res.mirror_latency_local
import zed.rainxch.githubstore.core.presentation.res.mirror_measure_latency
import zed.rainxch.githubstore.core.presentation.res.mirror_measuring
import zed.rainxch.githubstore.core.presentation.res.mirror_no_mirror_responded
import zed.rainxch.githubstore.core.presentation.res.mirror_pick_fastest
import zed.rainxch.githubstore.core.presentation.res.mirror_pick_fastest_sub
import zed.rainxch.githubstore.core.presentation.res.mirror_picker_description
import zed.rainxch.githubstore.core.presentation.res.mirror_picker_title
import zed.rainxch.githubstore.core.presentation.res.mirror_removed_toast
import zed.rainxch.githubstore.core.presentation.res.mirror_section_community
import zed.rainxch.githubstore.core.presentation.res.mirror_section_official
import zed.rainxch.githubstore.core.presentation.res.mirror_status_degraded
import zed.rainxch.githubstore.core.presentation.res.mirror_status_down
import zed.rainxch.githubstore.core.presentation.res.mirror_status_ok
import zed.rainxch.githubstore.core.presentation.res.mirror_status_unknown
import zed.rainxch.githubstore.core.presentation.res.mirror_test_button
import zed.rainxch.githubstore.core.presentation.res.mirror_test_dns_fail
import zed.rainxch.githubstore.core.presentation.res.mirror_test_failed
import zed.rainxch.githubstore.core.presentation.res.mirror_test_http_error
import zed.rainxch.githubstore.core.presentation.res.mirror_test_success
import zed.rainxch.githubstore.core.presentation.res.mirror_test_timeout
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow
import zed.rainxch.tweaks.presentation.components.shell.SettingsSectionHead
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot
import zed.rainxch.tweaks.presentation.components.shell.tweaksKicker
import zed.rainxch.tweaks.presentation.mirror.components.CustomMirrorDialog
import zed.rainxch.tweaks.presentation.mirror.components.DeployYourOwnHint

@Composable
fun MirrorPickerRoot(
    onNavigateBack: () -> Unit,
    viewModel: MirrorPickerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is MirrorPickerEvent.MirrorRemovedNotice -> {
                coroutineScope.launch {
                    toastState.warning(getString(Res.string.mirror_removed_toast, event.displayName))
                }
            }

            is MirrorPickerEvent.FastestMirrorChosen -> {
                coroutineScope.launch {
                    toastState.success(getString(Res.string.mirror_fastest_chosen, event.displayName, event.latencyMs))
                }
            }

            MirrorPickerEvent.NoMirrorResponded -> {
                coroutineScope.launch {
                    toastState.warning(getString(Res.string.mirror_no_mirror_responded))
                }
            }

            is MirrorPickerEvent.OpenUrl -> {
                uriHandler.openUri(event.url)
            }
        }
    }

    MirrorPickerScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        toastState = toastState,
    )
}

@Composable
private fun MirrorPickerScreen(
    state: MirrorPickerState,
    onAction: (MirrorPickerAction) -> Unit,
    onNavigateBack: () -> Unit,
    toastState: KomiToastState,
    modifier: Modifier = Modifier,
) {
    KomiScaffold(
        modifier = modifier,
        toastState = toastState,
        grid = true,
        screentone = true,
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.mirror_picker_title),
                titleAccent = tweaksKicker(TweaksDecorSlot.Mirror),
                onBack = onNavigateBack,
            )
        },
    ) { padding ->
        val colors = LocalPersonality.current.colors
        val official = state.mirrors.filter { it.type == MirrorType.OFFICIAL }
        val community = state.mirrors.filter { it.type == MirrorType.COMMUNITY }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.mirror_picker_description),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )

            SettingsSectionHead(stringResource(Res.string.mirror_section_official), TweaksDecorSlot.MirrorOfficial)
            SettingsGroup {
                SettingsRow(
                    title = stringResource(Res.string.mirror_pick_fastest),
                    subtitle =
                        stringResource(
                            if (state.isMeasuringLatencies) Res.string.mirror_measuring else Res.string.mirror_pick_fastest_sub,
                        ),
                    last = official.isEmpty(),
                    onClick = { onAction(MirrorPickerAction.OnAutoPickFastest) },
                    trailing = {
                        KomiIconButton(
                            icon = Icons.Filled.Speed,
                            contentDescription = stringResource(Res.string.mirror_measure_latency),
                            enabled = !state.isMeasuringLatencies,
                            onClick = { onAction(MirrorPickerAction.OnMeasureLatencies) },
                        )
                    },
                )
                official.forEachIndexed { index, mirror ->
                    MirrorSettingsRow(
                        mirror = mirror,
                        measuredMs = state.measuredLatencies[mirror.id],
                        isFastest = state.fastestMirrorId == mirror.id,
                        selected = state.selectedMirrorId == mirror.id,
                        last = index == official.lastIndex,
                        onClick = { onAction(MirrorPickerAction.OnSelectMirror(mirror.id)) },
                    )
                }
            }

            SettingsSectionHead(stringResource(Res.string.mirror_section_community), TweaksDecorSlot.MirrorCommunity)
            SettingsGroup {
                community.forEach { mirror ->
                    MirrorSettingsRow(
                        mirror = mirror,
                        measuredMs = state.measuredLatencies[mirror.id],
                        isFastest = state.fastestMirrorId == mirror.id,
                        selected = state.selectedMirrorId == mirror.id,
                        last = false,
                        onClick = { onAction(MirrorPickerAction.OnSelectMirror(mirror.id)) },
                    )
                }
                SettingsRow(
                    title = stringResource(Res.string.mirror_custom_label),
                    last = true,
                    onClick = { onAction(MirrorPickerAction.OnCustomMirrorClicked) },
                    trailing = { KomiRadioButton(selected = state.customTemplate != null, onClick = null) },
                )
            }

            KomiButton(
                onClick = { onAction(MirrorPickerAction.OnTestConnection) },
                label = stringResource(Res.string.mirror_test_button),
                variant = KomiButtonVariant.Primary,
                enabled = !state.isTesting,
                loading = state.isTesting,
                modifier = Modifier.fillMaxWidth(),
            )
            state.testResult?.let { result ->
                KomiText(
                    text = formatTestResult(result),
                    role = KomiTextRole.Body,
                    color = colors.onSurface,
                )
            }

            Spacer(Modifier.height(4.dp))
            DeployYourOwnHint(onClick = { onAction(MirrorPickerAction.OnDeployYourOwnClicked) })
        }
    }

    if (state.isCustomDialogVisible) {
        CustomMirrorDialog(
            draft = state.customDraft,
            error = state.customDraftError,
            onDraftChange = { onAction(MirrorPickerAction.OnCustomDraftChanged(it)) },
            onConfirm = { onAction(MirrorPickerAction.OnCustomMirrorConfirm) },
            onDismiss = { onAction(MirrorPickerAction.OnCustomMirrorDismiss) },
        )
    }
}

@Composable
private fun MirrorSettingsRow(
    mirror: MirrorUi,
    measuredMs: Int?,
    isFastest: Boolean,
    selected: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val label =
        when (mirror.status) {
            MirrorStatus.OK -> mirror.latencyMs?.let { stringResource(Res.string.mirror_status_ok, it) }
            MirrorStatus.DEGRADED -> mirror.latencyMs?.let { stringResource(Res.string.mirror_status_degraded, it) }
            MirrorStatus.DOWN -> stringResource(Res.string.mirror_status_down)
            MirrorStatus.UNKNOWN -> stringResource(Res.string.mirror_status_unknown)
        }
    val title = if (isFastest) stringResource(Res.string.mirror_fastest_badge, mirror.name) else mirror.name
    val subtitle = measuredMs?.let { stringResource(Res.string.mirror_latency_local, it) } ?: label
    SettingsRow(
        title = title,
        subtitle = subtitle,
        last = last,
        onClick = onClick,
        trailing = { KomiRadioButton(selected = selected, onClick = null) },
    )
}

@Composable
private fun formatTestResult(result: TestResult): String =
    when (result) {
        is TestResult.Success -> stringResource(Res.string.mirror_test_success, result.latencyMs)
        is TestResult.HttpError -> stringResource(Res.string.mirror_test_http_error, result.code)
        TestResult.Timeout -> stringResource(Res.string.mirror_test_timeout)
        TestResult.DnsFailure -> stringResource(Res.string.mirror_test_dns_fail)
        TestResult.Other -> stringResource(Res.string.mirror_test_failed)
    }
