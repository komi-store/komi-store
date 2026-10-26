package zed.rainxch.core.domain.utils

import kotlin.time.Instant

object ReleaseChange {
    data class BuildIdentity(
        val releaseId: Long? = null,
        val assetId: Long? = null,
        val digest: String? = null,
        val size: Long? = null,
    )

    internal fun parsePublishedAtToInstant(raw: String?): Instant? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { Instant.parse(trimmed) }.getOrNull()
    }

    // Public comparison for callers outside this module (the update check needs it).
    fun publishedAtAfter(
        candidate: String?,
        baseline: String?,
    ): Boolean = isPublishedAtAfter(candidate, baseline)

    // A stored release timestamp counts only while it parses; a corrupt value must not win over the fetched window.
    fun publishedAtOrNull(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.takeIf { it.isNotEmpty() && parsePublishedAtToInstant(it) != null }
    }

    internal fun isPublishedAtAfter(
        candidate: String?,
        baseline: String?,
    ): Boolean {
        val candidateInstant = parsePublishedAtToInstant(candidate) ?: return false
        val baselineInstant = parsePublishedAtToInstant(baseline) ?: return false
        return candidateInstant > baselineInstant
    }

    fun assetBuildChanged(
        matched: BuildIdentity,
        stored: BuildIdentity,
    ): Boolean {
        if (matched.digest != null && stored.digest != null) return matched.digest != stored.digest
        return releaseObjectChanged(
            matchedReleaseId = matched.releaseId,
            matchedAssetId = matched.assetId,
            storedReleaseId = stored.releaseId,
            storedAssetId = stored.assetId,
        ) ||
            assetIdentityChanged(
                matchedDigest = matched.digest,
                matchedSize = matched.size,
                storedDigest = stored.digest,
                storedSize = stored.size,
            )
    }

    fun releaseObjectChanged(
        matchedReleaseId: Long?,
        matchedAssetId: Long?,
        storedReleaseId: Long?,
        storedAssetId: Long?,
    ): Boolean {
        if (matchedReleaseId != null && storedReleaseId != null && matchedReleaseId != storedReleaseId) {
            return true
        }
        if (matchedAssetId != null && storedAssetId != null && matchedAssetId != storedAssetId) {
            return true
        }
        return false
    }

    fun assetIdentityChanged(
        matchedDigest: String?,
        matchedSize: Long?,
        storedDigest: String?,
        storedSize: Long?,
    ): Boolean {
        if (matchedDigest != null && storedDigest != null) return matchedDigest != storedDigest
        if (matchedSize != null && storedSize != null) return matchedSize != storedSize
        return false
    }

    fun shouldReportTimestampUpdate(
        matchedTag: String?,
        matchedPublishedAt: String?,
        previousLatestPublishedAt: String?,
        previousWasUpdateAvailable: Boolean,
        previousLatestTag: String?,
        matchedAssetDigest: String? = null,
        matchedAssetSize: Long? = null,
        previousAssetDigest: String? = null,
        previousAssetSize: Long? = null,
        matchedReleaseId: Long? = null,
        matchedAssetId: Long? = null,
        previousReleaseId: Long? = null,
        previousAssetId: Long? = null,
        installedTag: String? = null,
    ): Boolean {
        if (previousLatestPublishedAt.isNullOrBlank() && matchedPublishedAt != null) {
            return !VersionMath.isExactSameVersion(matchedTag, installedTag)
        }
        val newerByTimestamp = isPublishedAtAfter(matchedPublishedAt, previousLatestPublishedAt)
        val newerByBuild =
            assetBuildChanged(
                matched =
                    BuildIdentity(
                        releaseId = matchedReleaseId,
                        assetId = matchedAssetId,
                        digest = matchedAssetDigest,
                        size = matchedAssetSize,
                    ),
                stored =
                    BuildIdentity(
                        releaseId = previousReleaseId,
                        assetId = previousAssetId,
                        digest = previousAssetDigest,
                        size = previousAssetSize,
                    ),
            )
        return newerByTimestamp || newerByBuild ||
            (previousWasUpdateAvailable && VersionMath.isExactSameVersion(matchedTag, previousLatestTag))
    }
}
