package zed.rainxch.tweaks.presentation.skipped

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.mirror_picker_title
import zed.rainxch.githubstore.core.presentation.res.skipped_updates_empty_description
import zed.rainxch.githubstore.core.presentation.res.skipped_updates_empty_title
import zed.rainxch.githubstore.core.presentation.res.skipped_updates_installed_label
import zed.rainxch.githubstore.core.presentation.res.skipped_updates_skipped_label
import zed.rainxch.githubstore.core.presentation.res.skipped_updates_title
import zed.rainxch.githubstore.core.presentation.res.skipped_updates_unskip_action
import zed.rainxch.githubstore.core.presentation.res.skipped_updates_unskip_failure
import zed.rainxch.githubstore.core.presentation.res.skipped_updates_unskipped_snackbar
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot
import zed.rainxch.tweaks.presentation.components.shell.tweaksKicker

@Composable
fun SkippedUpdatesRoot(
    onNavigateBack: () -> Unit,
    viewModel: SkippedUpdatesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SkippedUpdatesEvent.Unskipped ->
                coroutineScope.launch {
                    toastState.info(getString(Res.string.skipped_updates_unskipped_snackbar, event.appName))
                }

            SkippedUpdatesEvent.Failure ->
                coroutineScope.launch {
                    toastState.danger(getString(Res.string.skipped_updates_unskip_failure))
                }
        }
    }

    KomiScaffold(
        toastState = toastState,
        grid = true,
        screentone = true,
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.skipped_updates_title),
                titleAccent = tweaksKicker(TweaksDecorSlot.Skipped),
                onBack = onNavigateBack,
            )
        },
    ) { padding ->
        val colors = LocalPersonality.current.colors
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> KomiCircularProgress(modifier = Modifier.align(Alignment.Center))

                state.items.isEmpty() ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        KomiText(
                            text = stringResource(Res.string.skipped_updates_empty_title),
                            role = KomiTextRole.SectionTitle,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.size(8.dp))
                        KomiText(
                            text = stringResource(Res.string.skipped_updates_empty_description),
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                        )
                    }

                else ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsGroup {
                            state.items.forEachIndexed { index, item ->
                                val skipped = stringResource(Res.string.skipped_updates_skipped_label, item.skippedTag)
                                val sub =
                                    if (item.installedVersion.isBlank()) {
                                        skipped
                                    } else {
                                        skipped + " · " + stringResource(Res.string.skipped_updates_installed_label, item.installedVersion)
                                    }
                                SettingsRow(
                                    title = item.appName,
                                    subtitle = sub,
                                    last = index == state.items.lastIndex,
                                    trailing = {
                                        KomiButton(
                                            onClick = { viewModel.onAction(SkippedUpdatesAction.OnUnskip(item.packageName)) },
                                            label = stringResource(Res.string.skipped_updates_unskip_action),
                                            variant = KomiButtonVariant.Outline,
                                            size = KomiButtonSize.Sm,
                                        )
                                    },
                                )
                            }
                        }
                    }
            }
        }
    }
}
