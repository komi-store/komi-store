package zed.rainxch.apps.presentation.import

import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.import.model.CandidateUi
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi
import zed.rainxch.apps.presentation.import.model.SuggestionSource
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.InstallerKind
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.RepoMatchSource
import zed.rainxch.core.domain.system.RepoMatchSuggestion
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_browser
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_fdroid
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_obtainium
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_self
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_sideload
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_unknown

private const val PRESELECT_MIN = 0.5
private const val PRESELECT_MAX = 0.85
private const val CARD_SUGGESTIONS = 3

// A review card preselects a middling top match; a stronger one was auto-linked, a weaker one is left for the user.
internal suspend fun candidateCard(
    candidate: ExternalAppCandidate,
    match: RepoMatchResult?,
): CandidateUi {
    val suggestions = match?.suggestions.orEmpty()
    val top = suggestions.maxByOrNull { it.confidence }
    return CandidateUi(
        packageName = candidate.packageName,
        appLabel = candidate.appLabel,
        versionName = candidate.versionName,
        installerLabel = candidate.installerKind.label(),
        suggestions = suggestions.take(CARD_SUGGESTIONS).map { it.toUi() }.toImmutableList(),
        preselectedSuggestion = top?.takeIf { it.confidence in PRESELECT_MIN..PRESELECT_MAX }?.toUi(),
    )
}

internal fun RepoMatchSuggestion.toUi(): RepoSuggestionUi =
    RepoSuggestionUi(
        owner = owner,
        repo = repo,
        confidence = confidence,
        source =
            when (source) {
                RepoMatchSource.MANIFEST -> SuggestionSource.MANIFEST
                RepoMatchSource.SEARCH -> SuggestionSource.SEARCH
                RepoMatchSource.FINGERPRINT -> SuggestionSource.FINGERPRINT
                RepoMatchSource.MANUAL -> SuggestionSource.MANUAL
                RepoMatchSource.FORGEJO_SEARCH -> SuggestionSource.SEARCH
                RepoMatchSource.STARRED -> SuggestionSource.STARRED
            },
        stars = stars,
        description = description,
        sourceHost = sourceHost,
    )

private suspend fun InstallerKind.label(): String =
    when (this) {
        InstallerKind.STORE_OBTAINIUM -> getString(Res.string.external_import_installer_obtainium)

        InstallerKind.STORE_FDROID -> getString(Res.string.external_import_installer_fdroid)

        InstallerKind.BROWSER -> getString(Res.string.external_import_installer_browser)

        InstallerKind.SIDELOAD -> getString(Res.string.external_import_installer_sideload)

        InstallerKind.GITHUB_STORE_SELF -> getString(Res.string.external_import_installer_self)

        InstallerKind.UNKNOWN,
        InstallerKind.STORE_PLAY,
        InstallerKind.STORE_AURORA,
        InstallerKind.STORE_GALAXY,
        InstallerKind.STORE_OEM_OTHER,
        InstallerKind.SYSTEM,
        -> getString(Res.string.external_import_installer_unknown)
    }
