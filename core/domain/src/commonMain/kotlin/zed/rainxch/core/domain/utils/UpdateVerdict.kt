package zed.rainxch.core.domain.utils

object UpdateVerdict {
    data class Installed(
        val tag: String?,
        val versionCode: Long,
        val versionName: String? = null,
    )

    data class Stored(
        val latestTag: String?,
        val latestVersionCode: Long?,
        val publishedAt: String?,
        val wasUpdateAvailable: Boolean,
        val latestReleaseId: Long? = null,
        val latestAssetId: Long? = null,
        val latestAssetDigest: String? = null,
        val latestAssetSize: Long? = null,
    )

    data class Matched(
        val tag: String,
        val publishedAt: String?,
        val isPrerelease: Boolean,
        val releaseId: Long? = null,
        val assetId: Long? = null,
        val assetDigest: String? = null,
        val assetSize: Long? = null,
    )

    data class Bound(
        val assetId: Long?,
        val assetDigest: String?,
        val releasePublishedAt: String?,
    )

    fun decide(
        installed: Installed,
        stored: Stored,
        matched: Matched,
        skippedTag: String?,
        bound: Bound? = null,
        selfAttestedDigest: String? = null,
    ): Result {
        val tags = TagReading(installed, matched)
        val skip = skipState(stored, matched, skippedTag)
        val codesAlreadyMatch = codesAlreadyMatch(installed, stored, matched)
        val deviceRunsMatchedRelease =
            !tags.timestampTracked && VersionMath.isExactSameVersion(installed.versionName, matched.tag)
        val tagVerdict = tagVerdict(tags, installed, stored, matched, codesAlreadyMatch)
        val selfAttestedSameFile = selfAttestedSameFile(tags, matched, selfAttestedDigest)

        val isUpdateAvailable =
            when {
                skip.holds -> false
                selfAttestedSameFile != null -> !selfAttestedSameFile
                else -> boundOrTagVerdict(tags, matched, bound, deviceRunsMatchedRelease, tagVerdict)
            }

        return Result(
            isUpdateAvailable = isUpdateAvailable,
            skipBecameStale = skip.becameStale,
            codesAlreadyMatch = codesAlreadyMatch,
            deviceRunsMatchedRelease = deviceRunsMatchedRelease,
        )
    }

    private class TagReading(
        installed: Installed,
        matched: Matched,
    ) {
        val reconcilable = VersionMath.versionsReconcilable(installed.tag, matched.tag)
        val timestampTracked = VersionMath.isTimestampTrackedTag(matched.tag)
        val sameTag = VersionMath.isExactSameVersion(matched.tag, installed.tag)
        val usedTimestampLogic =
            timestampTracked ||
                (sameTag && !reconcilable) ||
                (!reconcilable && (matched.isPrerelease || PreReleaseTags.isPreReleaseTag(matched.tag)))

        // Rolling tags and unknown schemes cannot order builds, so only file bytes or publish times can.
        val needsBuildEvidence =
            timestampTracked || VersionMath.detectScheme(matched.tag) == VersionMath.Scheme.Unknown
    }

    private class SkipState(
        val holds: Boolean,
        val becameStale: Boolean,
    )

    private fun skipState(
        stored: Stored,
        matched: Matched,
        skippedTag: String?,
    ): SkipState {
        val matchesSkipped =
            skippedTag != null && VersionMath.isExactSameVersion(matched.tag, skippedTag)
        val supersededByNewBuild =
            matchesSkipped &&
                VersionMath.isTimestampTrackedTag(matched.tag) &&
                (
                    ReleaseChange.isPublishedAtAfter(matched.publishedAt, stored.publishedAt) ||
                        ReleaseChange.assetBuildChanged(matched.buildIdentity(), stored.latestBuildIdentity())
                )
        val overtakenByNewerVersion =
            skippedTag != null &&
                !matchesSkipped &&
                !VersionMath.isTimestampTrackedTag(matched.tag) &&
                !VersionMath.isTimestampTrackedTag(skippedTag) &&
                VersionMath.isVersionNewer(matched.tag, skippedTag)
        return SkipState(
            holds = matchesSkipped && !supersededByNewBuild,
            becameStale = supersededByNewBuild || overtakenByNewerVersion,
        )
    }

    private fun codesAlreadyMatch(
        installed: Installed,
        stored: Stored,
        matched: Matched,
    ): Boolean =
        installed.versionCode > 0L &&
            stored.latestVersionCode != null &&
            stored.latestVersionCode > 0L &&
            installed.versionCode == stored.latestVersionCode &&
            VersionMath.isExactSameVersion(matched.tag, stored.latestTag)

    private fun tagVerdict(
        tags: TagReading,
        installed: Installed,
        stored: Stored,
        matched: Matched,
        codesAlreadyMatch: Boolean,
    ): Boolean =
        when {
            tags.usedTimestampLogic -> {
                ReleaseChange.shouldReportTimestampUpdate(
                    matchedTag = matched.tag,
                    matchedPublishedAt = matched.publishedAt,
                    previousLatestPublishedAt = stored.publishedAt,
                    previousWasUpdateAvailable = stored.wasUpdateAvailable,
                    previousLatestTag = stored.latestTag,
                    matchedAssetDigest = matched.assetDigest,
                    matchedAssetSize = matched.assetSize,
                    previousAssetDigest = stored.latestAssetDigest,
                    previousAssetSize = stored.latestAssetSize,
                    matchedReleaseId = matched.releaseId,
                    matchedAssetId = matched.assetId,
                    previousReleaseId = stored.latestReleaseId,
                    previousAssetId = stored.latestAssetId,
                    installedTag = installed.tag,
                )
            }

            codesAlreadyMatch -> {
                false
            }

            !tags.reconcilable -> {
                false
            }

            else -> {
                VersionMath.isVersionNewer(
                    candidate = matched.tag,
                    current = installed.tag,
                )
            }
        }

    // Different installed bytes report, equal bytes block a rebuild report; null leaves the call to the other paths.
    private fun selfAttestedSameFile(
        tags: TagReading,
        matched: Matched,
        selfAttestedDigest: String?,
    ): Boolean? {
        val hasBothDigests = selfAttestedDigest != null && matched.assetDigest != null
        return if (hasBothDigests && tags.sameTag && tags.needsBuildEvidence) {
            selfAttestedDigest == matched.assetDigest
        } else {
            null
        }
    }

    private fun boundOrTagVerdict(
        tags: TagReading,
        matched: Matched,
        bound: Bound?,
        deviceRunsMatchedRelease: Boolean,
        tagVerdict: Boolean,
    ): Boolean =
        when {
            bound != null && isSameTagRebuild(tags, matched, bound) -> {
                true
            }

            deviceRunsMatchedRelease -> {
                false
            }

            bound == null -> {
                tagVerdict
            }

            // The installed file outranks every tag heuristic: byte-for-byte the offered asset is never an update.
            isSameFile(
                installedAssetId = bound.assetId,
                installedAssetDigest = bound.assetDigest,
                matchedAssetId = matched.assetId,
                matchedAssetDigest = matched.assetDigest,
            ) -> {
                false
            }

            // A binding speaks only where tags cannot be ordered; a stable can be published before an installed pre-release.
            tags.reconcilable && !tags.needsBuildEvidence -> {
                tagVerdict
            }

            else -> {
                decideBound(
                    sameFile = false,
                    matchedPublishedAt = matched.publishedAt,
                    installedReleasePublishedAt = bound.releasePublishedAt,
                    fallback = tagVerdict,
                )
            }
        }

    // Same tag, different bytes, published later: a re-released build that only the timestamp can reveal.
    private fun isSameTagRebuild(
        tags: TagReading,
        matched: Matched,
        bound: Bound,
    ): Boolean =
        tags.sameTag &&
            ReleaseChange.assetBuildChanged(
                matched = ReleaseChange.BuildIdentity(assetId = matched.assetId, digest = matched.assetDigest),
                stored = ReleaseChange.BuildIdentity(assetId = bound.assetId, digest = bound.assetDigest),
            ) &&
            ReleaseChange.isPublishedAtAfter(matched.publishedAt, bound.releasePublishedAt)

    private fun Matched.buildIdentity(): ReleaseChange.BuildIdentity =
        ReleaseChange.BuildIdentity(releaseId = releaseId, assetId = assetId, digest = assetDigest, size = assetSize)

    private fun Stored.latestBuildIdentity(): ReleaseChange.BuildIdentity =
        ReleaseChange.BuildIdentity(
            releaseId = latestReleaseId,
            assetId = latestAssetId,
            digest = latestAssetDigest,
            size = latestAssetSize,
        )

    fun shouldAdoptMatchedTag(
        codesAlreadyMatch: Boolean,
        installedTag: String?,
        matchedTag: String,
        deviceRunsMatchedRelease: Boolean = false,
    ): Boolean = installedTag != matchedTag && (codesAlreadyMatch || deviceRunsMatchedRelease)

    fun isSameFile(
        installedAssetId: Long?,
        installedAssetDigest: String?,
        matchedAssetId: Long?,
        matchedAssetDigest: String?,
    ): Boolean {
        if (installedAssetDigest != null && matchedAssetDigest != null) {
            return installedAssetDigest == matchedAssetDigest
        }
        return installedAssetId != null && matchedAssetId != null && installedAssetId == matchedAssetId
    }

    fun decideBound(
        sameFile: Boolean,
        matchedPublishedAt: String?,
        installedReleasePublishedAt: String?,
        fallback: Boolean,
    ): Boolean =
        when {
            sameFile -> false
            installedReleasePublishedAt == null -> fallback
            else -> ReleaseChange.isPublishedAtAfter(matchedPublishedAt, installedReleasePublishedAt)
        }

    data class Result(
        val isUpdateAvailable: Boolean,
        val skipBecameStale: Boolean,
        val codesAlreadyMatch: Boolean,
        val deviceRunsMatchedRelease: Boolean,
    )
}
