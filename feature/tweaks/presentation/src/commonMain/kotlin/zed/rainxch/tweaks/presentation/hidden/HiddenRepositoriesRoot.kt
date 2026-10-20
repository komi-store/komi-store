package zed.rainxch.tweaks.presentation.hidden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_empty_description
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_empty_title
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_title
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_unhide_action
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_unhide_all
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_unhide_failure
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_unhidden_all_snackbar
import zed.rainxch.githubstore.core.presentation.res.hidden_repositories_unhidden_snackbar
import zed.rainxch.tweaks.presentation.components.shell.SettingsGroup
import zed.rainxch.tweaks.presentation.components.shell.SettingsRow
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot
import zed.rainxch.tweaks.presentation.components.shell.tweaksKicker

@Composable
fun HiddenRepositoriesRoot(
    onNavigateBack: () -> Unit,
    viewModel: HiddenRepositoriesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HiddenRepositoriesEvent.Unhidden ->
                toastState.info(getString(Res.string.hidden_repositories_unhidden_snackbar, event.repoFullName))

            HiddenRepositoriesEvent.UnhiddenAll ->
                toastState.info(getString(Res.string.hidden_repositories_unhidden_all_snackbar))

            HiddenRepositoriesEvent.Failure ->
                toastState.danger(getString(Res.string.hidden_repositories_unhide_failure))
        }
    }

    KomiScaffold(
        toastState = toastState,
        grid = true,
        screentone = true,
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.hidden_repositories_title),
                titleAccent = tweaksKicker(TweaksDecorSlot.Hidden),
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
                            text = stringResource(Res.string.hidden_repositories_empty_title),
                            role = KomiTextRole.SectionTitle,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.size(8.dp))
                        KomiText(
                            text = stringResource(Res.string.hidden_repositories_empty_description),
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
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            KomiButton(
                                onClick = { viewModel.onAction(HiddenRepositoriesAction.OnUnhideAll) },
                                label = stringResource(Res.string.hidden_repositories_unhide_all),
                                variant = KomiButtonVariant.Destructive,
                                size = KomiButtonSize.Sm,
                            )
                        }
                        SettingsGroup {
                            state.items.forEachIndexed { index, item ->
                                SettingsRow(
                                    title = item.repoName,
                                    subtitle = item.repoOwner,
                                    last = index == state.items.lastIndex,
                                    trailing = {
                                        KomiButton(
                                            onClick = { viewModel.onAction(HiddenRepositoriesAction.OnUnhide(item.repoId)) },
                                            label = stringResource(Res.string.hidden_repositories_unhide_action),
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
