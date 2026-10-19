package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.ExternalMatchRequest
import zed.rainxch.core.data.dto.ExternalMatchResponse
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.InstallerKind
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.RepoMatchSource
import zed.rainxch.core.domain.system.RepoMatchSuggestion

fun ExternalAppCandidate.toRequestItem(): ExternalMatchRequest.RequestItem =
    ExternalMatchRequest.RequestItem(
        packageName = packageName,
        appLabel = appLabel,
        signingFingerprint = signingFingerprint,
        installerKind = installerKind.toWireString(),
        manifestHint =
            manifestHint?.let {
                ExternalMatchRequest.ManifestHintDto(owner = it.owner, repo = it.repo)
            },
    )

fun ExternalMatchResponse.toRepoMatchResults(): List<RepoMatchResult> =
    matches.map { entry ->
        RepoMatchResult(
            packageName = entry.packageName,
            suggestions =
                entry.candidates.map { c ->
                    RepoMatchSuggestion(
                        owner = c.owner,
                        repo = c.repo,
                        confidence = c.confidence,
                        source = c.source.toRepoMatchSource(),
                        stars = c.stars,
                        description = c.description,
                    )
                },
        )
    }

private fun InstallerKind.toWireString(): String =
    when (this) {
        InstallerKind.STORE_OBTAINIUM -> "obtainium"
        InstallerKind.STORE_FDROID -> "fdroid"
        InstallerKind.STORE_PLAY -> "play"
        InstallerKind.STORE_AURORA -> "aurora"
        InstallerKind.STORE_GALAXY -> "galaxy"
        InstallerKind.STORE_OEM_OTHER -> "oem_other"
        InstallerKind.BROWSER -> "browser"
        InstallerKind.SIDELOAD -> "sideload"
        InstallerKind.SYSTEM -> "system"
        InstallerKind.GITHUB_STORE_SELF -> "github_store_self"
        InstallerKind.UNKNOWN -> "unknown"
    }

private fun String.toRepoMatchSource(): RepoMatchSource =
    when (this) {
        "manifest" -> RepoMatchSource.MANIFEST
        "search" -> RepoMatchSource.SEARCH
        "fingerprint" -> RepoMatchSource.FINGERPRINT
        else -> RepoMatchSource.SEARCH
    }
