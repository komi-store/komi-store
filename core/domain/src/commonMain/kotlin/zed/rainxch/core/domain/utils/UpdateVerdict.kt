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
        val reconcilable = VersionMath.versionsReconcilable(installed.tag, matched.tag)
        val codesAlreadyMatch =
            installed.versionCode > 0L &&
                stored.latestVersionCode != null &&
                stored.latestVersionCode > 0L &&
                installed.versionCode == stored.latestVersionCode &&
                VersionMath.isExactSameVersion(matched.tag, stored.latestTag)

        val matchesSkipped =
            skippedTag != null && VersionMath.isExactSameVersion(matched.tag, skippedTag)
        val publishedAtAdvanced =
            VersionMath.isPublishedAtAfter(matched.publishedAt, stored.publishedAt)
        val identityAdvanced =
            VersionMath.assetBuildChanged(
                matchedReleaseId = matched.releaseId,
                matchedAssetId = matched.assetId,
                matchedDigest = matched.assetDigest,
                matchedSize = matched.assetSize,
                storedReleaseId = stored.latestReleaseId,
                storedAssetId = stored.latestAssetId,
                storedDigest = stored.latestAssetDigest,
                storedSize = stored.latestAssetSize,
            )
        val skipSupersededByNewBuild =
            matchesSkipped &&
                VersionMath.isTimestampTrackedTag(matched.tag) &&
                (publishedAtAdvanced || identityAdvanced)
        val skipHolds = matchesSkipped && !skipSupersededByNewBuild
        val skipBecameStale =
            skipSupersededByNewBuild ||
                (
                    skippedTag != null &&
                        !matchesSkipped &&
                        !VersionMath.isTimestampTrackedTag(matched.tag) &&
                        !VersionMath.isTimestampTrackedTag(skippedTag) &&
                        VersionMath.isVersionNewer(matched.tag, skippedTag)
                )

        val timestampTracked = VersionMath.isTimestampTrackedTag(matched.tag)
        val sameTag = VersionMath.isExactSameVersion(matched.tag, installed.tag)
        val usedTimestampLogic =
            timestampTracked ||
                (sameTag && !reconcilable) ||
                (!reconcilable && (matched.isPrerelease || VersionMath.isPreReleaseTag(matched.tag)))

        val timestampWouldReport =
            if (usedTimestampLogic) {
                VersionMath.shouldReportTimestampUpdate(
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
            } else {
                false
            }

        val deviceRunsMatchedRelease =
            !timestampTracked && VersionMath.isExactSameVersion(installed.versionName, matched.tag)

        val tagVerdict =
            when {
                usedTimestampLogic -> {
                    timestampWouldReport
                }

                codesAlreadyMatch -> {
                    false
                }

                !reconcilable -> {
                    false
                }

                else -> {
                    VersionMath.isVersionNewer(
                        candidate = matched.tag,
                        current = installed.tag,
                    )
                }
            }

        // Different installed bytes report; equal bytes keep the other paths from reporting a
        // rebuild the device already runs. Null leaves the decision to those paths.
        val tagNeedsSelfAttestation =
            timestampTracked || VersionMath.detectScheme(matched.tag) == VersionMath.Scheme.Unknown
        val hasBothDigests = selfAttestedDigest != null && matched.assetDigest != null
        val selfAttestedSameFile =
            if (hasBothDigests && sameTag && tagNeedsSelfAttestation) {
                selfAttestedDigest == matched.assetDigest
            } else {
                null
            }

        val boundUsesTimestamp =
            timestampTracked ||
                VersionMath.detectScheme(matched.tag) == VersionMath.Scheme.Unknown

        // The attached file identity outranks every tag heuristic: when the installed build is
        // byte-for-byte the asset being offered, no tag comparison can turn it into an update.
        val boundMatchesInstalledFile =
            bound != null &&
                isSameFile(
                    installedAssetId = bound.assetId,
                    installedAssetDigest = bound.assetDigest,
                    matchedAssetId = matched.assetId,
                    matchedAssetDigest = matched.assetDigest,
                )

        // Same tag, different bytes, published later: a re-released build. Version comparison
        // cannot see it, and it is the one stable-tag case where the timestamp decides.
        val installedBytesDiffer =
            bound != null &&
                VersionMath.assetBuildChanged(
                    matchedReleaseId = null,
                    matchedAssetId = matched.assetId,
                    matchedDigest = matched.assetDigest,
                    matchedSize = null,
                    storedReleaseId = null,
                    storedAssetId = bound.assetId,
                    storedDigest = bound.assetDigest,
                    storedSize = null,
                )
        val sameTagRebuild =
            sameTag &&
                bound != null &&
                installedBytesDiffer &&
                VersionMath.isPublishedAtAfter(matched.publishedAt, bound.releasePublishedAt)

        val isUpdateAvailable =
            when {
                skipHolds -> {
                    false
                }

                selfAttestedSameFile != null -> {
                    !selfAttestedSameFile
                }

                sameTagRebuild -> {
                    true
                }

                deviceRunsMatchedRelease -> {
                    false
                }

                boundMatchesInstalledFile -> {
                    false
                }

                // A binding only speaks where the tags cannot be ordered: a rolling tag, an
                // unknown scheme, or an installed tag the offered one does not reconcile with
                // (a nightly install offered a stable release). Otherwise the version comparison
                // is authoritative: a stable release can legitimately be published before the
                // pre-release the device installed.
                bound == null || (reconcilable && !boundUsesTimestamp) -> {
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

        return Result(
            isUpdateAvailable = isUpdateAvailable,
            skipBecameStale = skipBecameStale,
            codesAlreadyMatch = codesAlreadyMatch,
            deviceRunsMatchedRelease = deviceRunsMatchedRelease,
        )
    }

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
            else -> VersionMath.isPublishedAtAfter(matchedPublishedAt, installedReleasePublishedAt)
        }

    data class Result(
        val isUpdateAvailable: Boolean,
        val skipBecameStale: Boolean,
        val codesAlreadyMatch: Boolean,
        val deviceRunsMatchedRelease: Boolean,
    )
}
