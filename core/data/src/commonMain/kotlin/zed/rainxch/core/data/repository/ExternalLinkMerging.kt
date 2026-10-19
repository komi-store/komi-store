package zed.rainxch.core.data.repository

import zed.rainxch.core.data.local.db.entities.ExternalLinkEntity
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalLinkState
import zed.rainxch.core.domain.system.RepoMatchSource

internal fun mergeCandidate(
    existing: ExternalLinkEntity?,
    candidate: ExternalAppCandidate,
    now: Long,
): ExternalLinkEntity {
    if (existing != null && shouldPreserveDecision(existing, now)) {
        return existing.copy(
            signingFingerprint = candidate.signingFingerprint ?: existing.signingFingerprint,
            installerKind = candidate.installerKind.name,
        )
    }

    val hint = candidate.manifestHint
    return ExternalLinkEntity(
        packageName = candidate.packageName,
        state = ExternalLinkState.PENDING_REVIEW.name,
        repoOwner = hint?.owner ?: existing?.repoOwner,
        repoName = hint?.repo ?: existing?.repoName,
        matchSource = if (hint != null) RepoMatchSource.MANIFEST.name else existing?.matchSource,
        matchConfidence = hint?.confidence ?: existing?.matchConfidence,
        signingFingerprint = candidate.signingFingerprint,
        installerKind = candidate.installerKind.name,
        firstSeenAt = existing?.firstSeenAt ?: now,
        lastReviewedAt = now,
        skipExpiresAt = null,
    )
}

private fun shouldPreserveDecision(
    existing: ExternalLinkEntity,
    now: Long,
): Boolean =
    when (existing.state) {
        ExternalLinkState.MATCHED.name -> true
        ExternalLinkState.NEVER_ASK.name -> true
        ExternalLinkState.SKIPPED.name -> (existing.skipExpiresAt ?: 0) > now
        else -> false
    }
