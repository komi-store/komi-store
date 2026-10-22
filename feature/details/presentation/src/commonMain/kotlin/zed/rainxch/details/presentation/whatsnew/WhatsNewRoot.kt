package zed.rainxch.details.presentation.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
import zed.rainxch.core.presentation.components.markdown.MarkdownImageTransformer
import zed.rainxch.core.presentation.components.markdown.githubStoreMarkdownComponents
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownColors
import zed.rainxch.core.presentation.components.markdown.rememberMarkdownTypography
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.components.LanguagePicker
import zed.rainxch.details.presentation.components.TranslationCard
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.details_whats_new_screen_title

@Composable
fun WhatsNewRoot(
    repositoryId: Long,
    owner: String,
    repo: String,
    sourceHost: String?,
    onNavigateBack: () -> Unit,
    viewModel: DetailsWhatsNewViewModel =
        koinViewModel {
            parametersOf(repositoryId, owner, repo, sourceHost)
        },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WhatsNewScreen(
        state = state,
        onBack = onNavigateBack,
        onTranslate = viewModel::translate,
        onToggleTranslation = viewModel::toggleTranslation,
        onPickLanguage = viewModel::showLanguagePicker,
        onDismissLanguagePicker = viewModel::dismissLanguagePicker,
        onLanguageQueryChange = viewModel::onLanguageQueryChange,
        onClearTranslation = viewModel::clearTranslation,
    )
}

@Composable
private fun WhatsNewScreen(
    state: DetailsWhatsNewState,
    onBack: () -> Unit,
    onTranslate: (String) -> Unit,
    onToggleTranslation: () -> Unit,
    onPickLanguage: () -> Unit,
    onDismissLanguagePicker: () -> Unit,
    onLanguageQueryChange: (String) -> Unit,
    onClearTranslation: () -> Unit,
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
                title = state.repoName,
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
                        KomiText(
                            text = state.errorMessage,
                            role = KomiTextRole.Body,
                            color = personalityColors.error,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item(key = "header") {
                            KomiText(
                                text = stringResource(Res.string.details_whats_new_screen_title),
                                role = KomiTextRole.ScreenTitle,
                                color = personalityColors.onBackground,
                            )
                        }

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

                        items(items = state.releases, key = { it.id }) { release ->
                            val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(rowShape)
                                            .border(
                                                width = 1.dp,
                                                color = personalityColors.outline,
                                                shape = rowShape,
                                            ).background(personalityColors.surface)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    KomiText(
                                        text = release.tagName,
                                        role = KomiTextRole.CardTitle,
                                        color = personalityColors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )

                                    KomiText(
                                        text = release.publishedDate,
                                        role = KomiTextRole.Caption,
                                        color = personalityColors.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                Markdown(
                                    content = release.body,
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
