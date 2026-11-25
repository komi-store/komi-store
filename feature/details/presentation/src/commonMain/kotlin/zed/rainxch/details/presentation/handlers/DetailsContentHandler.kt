package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.details.domain.model.TranslationProviderNotConfiguredException
import zed.rainxch.details.domain.repository.TranslationRepository
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.RawDetailsState
import zed.rainxch.details.presentation.model.SupportedLanguages
import zed.rainxch.details.presentation.model.TranslationState
import zed.rainxch.details.presentation.utils.autoTranslateTargetLanguage
import zed.rainxch.details.presentation.utils.shouldAutoTranslate
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.translation_failed

// The README and release-notes sections: expanding, measuring and translating them, by hand or automatically.
internal class DetailsContentHandler(
    private val translationRepository: TranslationRepository,
    private val tweaksRepository: TweaksRepository,
    private val logger: KomiStoreLogger,
) {
    internal fun deviceLanguageCode(): String = translationRepository.getDeviceLanguageCode()

    internal fun onAction(
        session: DetailsSession,
        action: DetailsAction.Content,
    ) {
        when (action) {
            DetailsAction.ToggleAboutExpanded -> {
                session.update { it.copy(isAboutExpanded = !it.isAboutExpanded) }
            }

            DetailsAction.ToggleWhatsNewExpanded -> {
                session.update { it.copy(isWhatsNewExpanded = !it.isWhatsNewExpanded) }
            }

            is DetailsAction.OnAboutMeasured -> {
                onAboutMeasured(session, action.heightPx)
            }

            is DetailsAction.OnWhatsNewMeasured -> {
                onWhatsNewMeasured(session, action.heightPx)
            }

            is DetailsAction.TranslateAbout -> {
                translateAbout(session, action.targetLanguageCode)
            }

            is DetailsAction.TranslateWhatsNew -> {
                translateWhatsNew(session, action.targetLanguageCode)
            }

            DetailsAction.ToggleAboutTranslation -> {
                session.update { it.copy(aboutTranslation = it.aboutTranslation.toggled()) }
            }

            DetailsAction.ToggleWhatsNewTranslation -> {
                session.update { it.copy(whatsNewTranslation = it.whatsNewTranslation.toggled()) }
            }

            is DetailsAction.ShowLanguagePicker -> {
                session.update { it.copy(isLanguagePickerVisible = true, languagePickerTarget = action.target) }
            }

            DetailsAction.DismissLanguagePicker -> {
                session.update { it.copy(isLanguagePickerVisible = false, languagePickerTarget = null) }
            }
        }
    }

    private fun TranslationState.toggled(): TranslationState = copy(isShowingTranslation = !isShowingTranslation)

    private fun onAboutMeasured(
        session: DetailsSession,
        heightPx: Float,
    ) {
        val current = session.current.aboutMeasuredHeightPx
        if (current == null || heightPx > current) session.update { it.copy(aboutMeasuredHeightPx = heightPx) }
    }

    private fun onWhatsNewMeasured(
        session: DetailsSession,
        heightPx: Float,
    ) {
        val current = session.current.whatsNewMeasuredHeightPx
        if (current == null || heightPx > current) session.update { it.copy(whatsNewMeasuredHeightPx = heightPx) }
    }

    private fun translateAbout(
        session: DetailsSession,
        targetLanguageCode: String,
    ) {
        val readme = session.current.readmeMarkdown ?: return
        session.aboutTranslationJob?.cancel()
        session.aboutTranslationJob = translate(session, readme, targetLanguageCode, AboutSection)
    }

    private fun translateWhatsNew(
        session: DetailsSession,
        targetLanguageCode: String,
    ) {
        val description = session.current.selectedRelease?.description ?: return
        session.whatsNewTranslationJob?.cancel()
        session.whatsNewTranslationJob = translate(session, description, targetLanguageCode, WhatsNewSection)
    }

    internal fun observeAutoTranslateSetting(session: DetailsSession) {
        session.launch {
            tweaksRepository
                .getAutoTranslateEnabled()
                .distinctUntilChanged()
                .drop(1)
                .collect { enabled ->
                    session.aboutTranslationJob?.cancel()
                    session.whatsNewTranslationJob?.cancel()
                    if (enabled) {
                        maybeAutoTranslate(
                            session = session,
                            readmeBody = session.current.readmeMarkdown,
                            releaseDescription = session.current.selectedRelease?.description,
                        )
                    } else {
                        session.update {
                            it.copy(aboutTranslation = TranslationState(), whatsNewTranslation = TranslationState())
                        }
                    }
                }
        }
    }

    internal fun maybeAutoTranslate(
        session: DetailsSession,
        readmeBody: String?,
        releaseDescription: String?,
    ) {
        if (readmeBody.isNullOrBlank() && releaseDescription.isNullOrBlank()) return
        session.launch {
            val target = autoTranslateTarget() ?: return@launch
            val sourceLanguage = session.current.readmeLanguage
            val readme =
                readmeBody?.takeIf { body ->
                    shouldAutoTranslate(
                        text = body,
                        targetLanguage = target,
                        sourceLanguage = sourceLanguage,
                        alreadyTranslated = session.current.aboutTranslation.translatedText != null,
                    )
                }
            if (readme != null) {
                session.aboutTranslationJob?.cancel()
                session.aboutTranslationJob = translate(session, readme, target, AboutSection, notifyFailure = false)
            }
            val releaseNotes =
                releaseDescription?.takeIf { notes ->
                    notes == session.current.selectedRelease?.description &&
                        shouldAutoTranslate(
                            text = notes,
                            targetLanguage = target,
                            sourceLanguage = sourceLanguage,
                            alreadyTranslated = session.current.whatsNewTranslation.translatedText != null,
                        )
                }
            if (releaseNotes != null) {
                session.whatsNewTranslationJob?.cancel()
                session.whatsNewTranslationJob = translate(session, releaseNotes, target, WhatsNewSection, notifyFailure = false)
            }
        }
    }

    // The settings flows fall back to their defaults on a storage failure, so these reads can't fail.
    private suspend fun autoTranslateTarget(): String? =
        if (!tweaksRepository.getAutoTranslateEnabled().first()) {
            null
        } else {
            autoTranslateTargetLanguage(
                explicitTarget = tweaksRepository.getAutoTranslateTargetLang().first(),
                appLanguage = tweaksRepository.getAppLanguage().first(),
                deviceLanguage = translationRepository.getDeviceLanguageCode(),
            )
        }

    private fun translate(
        session: DetailsSession,
        text: String,
        targetLanguageCode: String,
        section: Section,
        notifyFailure: Boolean = true,
    ): Job =
        session.launch {
            val pending = section.get(session.current).copy(isTranslating = true, error = null, targetLanguageCode = targetLanguageCode)
            section.set(session, pending)
            try {
                val result = translationRepository.translate(text = text, targetLanguage = targetLanguageCode)
                section.set(
                    session,
                    TranslationState(
                        isTranslating = false,
                        translatedText = result.translatedText,
                        isShowingTranslation = true,
                        targetLanguageCode = targetLanguageCode,
                        targetLanguageDisplayName =
                            SupportedLanguages.all.find { it.code == targetLanguageCode }?.displayName ?: targetLanguageCode,
                        detectedSourceLanguage = result.detectedSourceLanguage,
                    ),
                )
            } catch (e: NetworkException) {
                onTranslationFailed(session, section, notifyFailure, e)
            } catch (e: TranslationProviderNotConfiguredException) {
                onTranslationFailed(session, section, notifyFailure, e)
            }
        }

    private suspend fun onTranslationFailed(
        session: DetailsSession,
        section: Section,
        notifyFailure: Boolean,
        error: Exception,
    ) {
        logger.error("Translation failed: ${error.message}")
        val message = getString(Res.string.translation_failed)
        section.set(session, section.get(session.current).copy(isTranslating = false, error = message.takeIf { notifyFailure }))
        if (notifyFailure) session.message(message)
    }

    // Which translation state a request reads and writes.
    private interface Section {
        fun get(state: RawDetailsState): TranslationState

        fun set(
            session: DetailsSession,
            translation: TranslationState,
        )
    }

    private object AboutSection : Section {
        override fun get(state: RawDetailsState): TranslationState = state.aboutTranslation

        override fun set(
            session: DetailsSession,
            translation: TranslationState,
        ) = session.update { it.copy(aboutTranslation = translation) }
    }

    private object WhatsNewSection : Section {
        override fun get(state: RawDetailsState): TranslationState = state.whatsNewTranslation

        override fun set(
            session: DetailsSession,
            translation: TranslationState,
        ) = session.update { it.copy(whatsNewTranslation = translation) }
    }
}
