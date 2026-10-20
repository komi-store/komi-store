package zed.rainxch.details.presentation.about

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.components.markdown.MarkdownImageTransformer
import zed.rainxch.core.presentation.components.markdown.githubStoreMarkdownComponents
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownColors
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownTypography
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.details.presentation.components.LanguagePicker
import zed.rainxch.details.presentation.components.TranslationCard
import zed.rainxch.details.presentation.utils.ProvideLanguageLinkInterceptor
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.details_about_screen_title
import zed.rainxch.githubstore.core.presentation.res.retry

@Composable
fun AboutRoot(
    repositoryId: Long,
    owner: String,
    repo: String,
    sourceHost: String?,
    translateTo: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToMarkdownViewer: (String) -> Unit,
    viewModel: DetailsAboutViewModel = koinViewModel {
        parametersOf(repositoryId, owner, repo, sourceHost)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(translateTo, state.readmeMarkdown.isNotBlank()) {
        if (translateTo != null && state.readmeMarkdown.isNotBlank()) {
            viewModel.translate(translateTo)
        }
    }

    AboutScreen(
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
private fun AboutScreen(
    state: DetailsAboutState,
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
    val components = remember(isDark, imageTransformer) {
        githubStoreMarkdownComponents(imageTransformer, isDark)
    }
    val personalityColors = LocalPersonality.current.colors

    KomiScaffold(
        topBar = {
            KomiTopBar(
                onBack = onBack,
                title = state.repoName,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { KomiLoadingIndicator() }

                state.errorMessage != null -> Box(
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
                            modifier = Modifier
                                .padding(8.dp)
                                .background(personalityColors.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            KomiText(
                                text = stringResource(Res.string.details_about_screen_title),
                                role = KomiTextRole.ScreenTitle,
                                color = personalityColors.onBackground,
                            )

                            state.readmeLanguage?.let { lang ->
                                Spacer(Modifier.height(4.dp))

                                KomiText(
                                    text = lang,
                                    role = KomiTextRole.Caption,
                                    color = personalityColors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item(key = "translation_card") {
                        Spacer(Modifier.height(4.dp))

                        TranslationCard(
                            state = state.translation,
                            deviceLanguageCode = state.deviceLanguageCode,
                            onPickLanguage = onPickLanguage,
                            onTranslate = onTranslate,
                            onToggle = onToggleTranslation,
                            onCancel = onClearTranslation,
                        )
                    }

                    item(key = "markdown") {
                        Spacer(Modifier.height(4.dp))

                        ProvideLanguageLinkInterceptor(
                            onTranslate = onTranslate,
                            onClearTranslation = onClearTranslation,
                            onOpenInternalMarkdown = onNavigateToMarkdownViewer,
                        ) {
                            Markdown(
                                content = state.displayedMarkdown,
                                colors = colors,
                                typography = typography,
                                imageTransformer = imageTransformer,
                                components = components,
                                modifier = Modifier.fillMaxWidth(),
                            )
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
