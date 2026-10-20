package zed.rainxch.details.presentation.markdownviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.compose.Markdown
import io.ktor.client.HttpClient
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.markdown.MarkdownImageTransformer
import zed.rainxch.core.presentation.components.markdown.githubStoreMarkdownComponents
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownColors
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownTypography
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.components.LanguagePicker
import zed.rainxch.details.presentation.components.TranslationCard
import zed.rainxch.details.presentation.utils.ProvideLanguageLinkInterceptor
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.retry

@Composable
fun MarkdownViewerRoot(
    onNavigateBack: () -> Unit,
    onNavigateToMarkdownViewer: (String) -> Unit,
    viewModel: MarkdownViewerViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MarkdownViewerScreen(
        state = state,
        onBack = onNavigateBack,
        onRetry = viewModel::retry,
        onTranslate = viewModel::translate,
        onToggleTranslation = viewModel::toggleTranslation,
        onPickLanguage = viewModel::showLanguagePicker,
        onDismissLanguagePicker = viewModel::dismissLanguagePicker,
        onLanguageQueryChange = viewModel::onLanguageQueryChange,
        onClearTranslation = viewModel::clearTranslation,
        onNavigateToMarkdownViewer = onNavigateToMarkdownViewer,
    )
}

@Composable
private fun MarkdownViewerScreen(
    state: MarkdownViewerState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTranslate: (String) -> Unit,
    onToggleTranslation: () -> Unit,
    onPickLanguage: () -> Unit,
    onDismissLanguagePicker: () -> Unit,
    onLanguageQueryChange: (String) -> Unit,
    onClearTranslation: () -> Unit,
    onNavigateToMarkdownViewer: (String) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val probeClient = koinInject<HttpClient>(qualifier = named("test"))
    val imageTransformer = remember(probeClient) { MarkdownImageTransformer(probeClient) }
    val colors = rememberMarkdownColors()
    val typography = rememberMarkdownTypography()
    val components =
        remember(isDark, imageTransformer) {
            githubStoreMarkdownComponents(imageTransformer, isDark)
        }
    val personalityColors = LocalPersonality.current.colors

    KomiScaffold(
        topBar = {
            KomiTopBar(
                onBack = onBack,
                title = state.filename,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { KomiLoadingIndicator() }
                }

                state.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            KomiText(
                                text = state.errorMessage,
                                role = KomiTextRole.Body,
                                color = personalityColors.error,
                            )

                            Spacer(Modifier.size(8.dp))

                            KomiText(
                                text = stringResource(Res.string.retry),
                                role = KomiTextRole.Overline,
                                color = personalityColors.primary,
                                modifier =
                                    Modifier
                                        .padding(8.dp)
                                        .background(personalityColors.surfaceContainerHigh)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .clickable { onRetry() },
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "translation_card") {
                            TranslationCard(
                                state = state.translation,
                                deviceLanguageCode = state.deviceLanguageCode,
                                onPickLanguage = onPickLanguage,
                                onTranslate = onTranslate,
                                onToggle = onToggleTranslation,
                                onCancel = onClearTranslation,
                            )
                        }

                        if (state.isReadyToRender) {
                            items(items = state.markdownChunks) { chunk ->
                                ProvideLanguageLinkInterceptor(
                                    onTranslate = onTranslate,
                                    onClearTranslation = onClearTranslation,
                                    onOpenInternalMarkdown = onNavigateToMarkdownViewer,
                                ) {
                                    Markdown(
                                        content = chunk,
                                        colors = colors,
                                        typography = typography,
                                        imageTransformer = imageTransformer,
                                        components = components,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    )
                                }
                            }
                        } else {
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    KomiCircularProgress(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LanguagePicker(
        isVisible = state.isLanguagePickerVisible,
        query = state.languagePickerQuery,
        languages = state.filteredLanguages,
        selectedLanguageCode = state.translation.targetLanguageCode ?: state.deviceLanguageCode,
        deviceLanguageCode = state.deviceLanguageCode,
        onQueryChange = onLanguageQueryChange,
        onLanguageSelected = { lang ->
            onDismissLanguagePicker()
            onTranslate(lang.code)
        },
        onDismiss = onDismissLanguagePicker,
    )
}
