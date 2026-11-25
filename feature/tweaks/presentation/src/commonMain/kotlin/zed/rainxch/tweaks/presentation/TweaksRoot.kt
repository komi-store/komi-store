package zed.rainxch.tweaks.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.isAndroid
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.model.settings.AppLanguages
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.constrainedContentWidth
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feedback_send_failed
import zed.rainxch.githubstore.core.presentation.res.feedback_send_success_email
import zed.rainxch.githubstore.core.presentation.res.feedback_send_success_github
import zed.rainxch.githubstore.core.presentation.res.language_follow_system
import zed.rainxch.githubstore.core.presentation.res.select_language
import zed.rainxch.githubstore.core.presentation.res.tweaks_language_intro_body
import zed.rainxch.githubstore.core.presentation.res.tweaks_title
import zed.rainxch.tweaks.presentation.components.ClearDownloadsDialog
import zed.rainxch.tweaks.presentation.components.LanguagePickerSheet
import zed.rainxch.tweaks.presentation.components.desktop.TweaksDesktopContent
import zed.rainxch.tweaks.presentation.components.sections.AppSection
import zed.rainxch.tweaks.presentation.components.sections.ConnectivitySection
import zed.rainxch.tweaks.presentation.components.sections.DiagnosticsSection
import zed.rainxch.tweaks.presentation.components.sections.InstallsSection
import zed.rainxch.tweaks.presentation.components.sections.LookAndFeelSection
import zed.rainxch.tweaks.presentation.components.sections.PrivacySection
import zed.rainxch.tweaks.presentation.components.shell.TweaksDecorSlot
import zed.rainxch.tweaks.presentation.components.shell.tweaksKicker
import zed.rainxch.tweaks.presentation.feedback.components.FeedbackBottomSheet
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackChannel

@Composable
fun TweaksRoot(
    onNavigateBack: () -> Unit,
    onNavigateToHostTokens: () -> Unit,
    onNavigateToMirrorPicker: () -> Unit,
    onNavigateToSkippedUpdates: () -> Unit,
    onNavigateToHiddenRepositories: () -> Unit,
    viewModel: TweaksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onAction(TweaksAction.OnRefreshCacheSize)
                    viewModel.onAction(TweaksAction.OnReevaluateBatteryOptimizationCard)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ObserveAsEvents(viewModel.events) { event ->
        coroutineScope.launch { toastState.showTweaksEvent(event) }
    }

    TweaksScreen(
        state = state,
        onAction = viewModel::onAction,
        toastState = toastState,
        onNavigateBack = onNavigateBack,
        onNavigateToHostTokens = onNavigateToHostTokens,
        onNavigateToMirrorPicker = onNavigateToMirrorPicker,
        onNavigateToSkippedUpdates = onNavigateToSkippedUpdates,
        onNavigateToHiddenRepositories = onNavigateToHiddenRepositories,
    )

    if (state.isFeedbackSheetVisible) {
        FeedbackBottomSheet(
            onDismiss = { viewModel.onAction(TweaksAction.OnFeedbackDismiss) },
            onSendSuccess = { channel ->
                viewModel.onAction(TweaksAction.OnFeedbackDismiss)
                coroutineScope.launch {
                    val message =
                        when (channel) {
                            FeedbackChannel.EMAIL -> getString(Res.string.feedback_send_success_email)
                            FeedbackChannel.GITHUB -> getString(Res.string.feedback_send_success_github)
                        }
                    toastState.success(message)
                }
            },
            onError = {
                coroutineScope.launch { toastState.danger(getString(Res.string.feedback_send_failed)) }
            },
        )
    }
}

@Composable
private fun TweaksScreen(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    toastState: KomiToastState,
    onNavigateBack: () -> Unit,
    onNavigateToHostTokens: () -> Unit,
    onNavigateToMirrorPicker: () -> Unit,
    onNavigateToSkippedUpdates: () -> Unit,
    onNavigateToHiddenRepositories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLanguageLabel =
        state.selectedAppLanguage
            ?.let { tag -> AppLanguages.ALL.firstOrNull { it.tag == tag }?.displayName }
            ?: stringResource(Res.string.language_follow_system)

    if (isDesktop()) {
        KomiScaffold(
            modifier = modifier,
            toastState = toastState,
            grid = false,
            screentone = false,
        ) { innerPadding ->
            TweaksDesktopContent(
                state = state,
                onAction = onAction,
                personality = state.selectedPersonality,
                accent = state.selectedAccent,
                onPersonalitySelect = { onAction(TweaksAction.OnPersonalitySelected(it)) },
                onAccentSelect = { onAction(TweaksAction.OnAccentSelected(it)) },
                currentLanguageLabel = currentLanguageLabel,
                onOpenLanguage = { onAction(TweaksAction.OnLanguagePickerOpen) },
                onOpenFeedback = { onAction(TweaksAction.OnFeedbackClick) },
                onNavigateToMirrorPicker = onNavigateToMirrorPicker,
                onNavigateToHiddenRepositories = onNavigateToHiddenRepositories,
                onNavigateToHostTokens = onNavigateToHostTokens,
                onNavigateBack = onNavigateBack,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    } else {
        KomiScaffold(
            modifier = modifier,
            topBar = {
                KomiTopBar(
                    title = stringResource(Res.string.tweaks_title),
                    titleAccent = tweaksKicker(TweaksDecorSlot.Settings),
                    onBack = onNavigateBack,
                )
            },
            toastState = toastState,
            grid = true,
            screentone = true,
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                TweaksSectionList(
                    state = state,
                    onAction = onAction,
                    currentLanguageLabel = currentLanguageLabel,
                    onNavigateToHostTokens = onNavigateToHostTokens,
                    onNavigateToMirrorPicker = onNavigateToMirrorPicker,
                    onNavigateToSkippedUpdates = onNavigateToSkippedUpdates,
                    onNavigateToHiddenRepositories = onNavigateToHiddenRepositories,
                )
            }
        }
    }

    TweaksSheets(state = state, onAction = onAction)
}

@Composable
private fun TweaksSectionList(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
    currentLanguageLabel: String,
    onNavigateToHostTokens: () -> Unit,
    onNavigateToMirrorPicker: () -> Unit,
    onNavigateToSkippedUpdates: () -> Unit,
    onNavigateToHiddenRepositories: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .constrainedContentWidth()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LookAndFeelSection(
            state = state,
            onAction = onAction,
            personality = state.selectedPersonality,
            accent = state.selectedAccent,
            onPersonalitySelect = { onAction(TweaksAction.OnPersonalitySelected(it)) },
            onAccentSelect = { onAction(TweaksAction.OnAccentSelected(it)) },
            currentLanguageLabel = currentLanguageLabel,
            onOpenLanguage = { onAction(TweaksAction.OnLanguagePickerOpen) },
        )
        ConnectivitySection(
            state = state,
            onAction = onAction,
            onNavigateToMirrorPicker = onNavigateToMirrorPicker,
        )
        if (isAndroid()) {
            InstallsSection(
                state = state,
                onAction = onAction,
                onNavigateToSkippedUpdates = onNavigateToSkippedUpdates,
            )
        }
        PrivacySection(
            state = state,
            onAction = onAction,
            onNavigateToHiddenRepositories = onNavigateToHiddenRepositories,
            onNavigateToHostTokens = onNavigateToHostTokens,
        )
        AppSection(onOpenFeedback = { onAction(TweaksAction.OnFeedbackClick) })
        if (isAndroid()) {
            DiagnosticsSection(onAction = onAction)
        }

        tweaksKicker(TweaksDecorSlot.Footer)?.let { footer ->
            KomiText(
                text = footer,
                role = KomiTextRole.Caption,
                color = LocalPersonality.current.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun TweaksSheets(
    state: TweaksState,
    onAction: (TweaksAction) -> Unit,
) {
    if (state.isClearDownloadsDialogVisible) {
        ClearDownloadsDialog(
            cacheSize = state.cacheSize,
            onDismissRequest = { onAction(TweaksAction.OnClearDownloadsDismiss) },
            onConfirm = { onAction(TweaksAction.OnClearDownloadsConfirm) },
        )
    }

    if (state.isLanguageSheetVisible) {
        LanguagePickerSheet(
            title = stringResource(Res.string.select_language),
            options = state.languagePickerOptions,
            selectedId = state.selectedAppLanguage,
            query = state.languageQuery,
            onQueryChange = { onAction(TweaksAction.OnLanguageQueryChange(it)) },
            intro = stringResource(Res.string.tweaks_language_intro_body),
            onSelect = { onAction(TweaksAction.OnAppLanguageSelected(it)) },
            onDismiss = { onAction(TweaksAction.OnLanguagePickerDismiss) },
        )
    }
}

@Preview
@Composable
private fun TweaksRootPreview() {
    PersonalityPreview {
        Spacer(Modifier.height(0.dp))
    }
}
