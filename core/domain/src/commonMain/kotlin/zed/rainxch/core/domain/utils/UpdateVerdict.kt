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
                (skippedTag != null &&
                    !matchesSkipped &&
                    !VersionMath.isTimestampTrackedTag(matched.tag) &&
                    !VersionMath.isTimestampTrackedTag(skippedTag) &&
                    VersionMath.isVersionNewer(matched.tag, skippedTag))

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
                usedTimestampLogic -> timestampWouldReport
                codesAlreadyMatch -> false
                !reconcilable -> false
                else ->
                    VersionMath.isVersionNewer(
                        candidate = matched.tag,
                        current = installed.tag,
                    )
            }

        // What the device itself runs, for the case no binding can speak for: a rolling tag reuses
        // one tag string across builds, so when the tag is not a version at all — an unknown
        // scheme, or a known opaque marker — the installed file is the only side that can say
        // whether the release still offers something the device does not run. Difference is
        // evidence — it reports; sameness is also evidence — it keeps the other voices from
        // reporting. A digest that cannot be compared drops out and leaves the decision to the
        // existing paths.
        val selfAttestedSameFile =
            if (
                selfAttestedDigest != null &&
                matched.assetDigest != null &&
                sameTag &&
                (timestampTracked || VersionMath.detectScheme(matched.tag) == VersionMath.Scheme.Unknown)
            ) {
                selfAttestedDigest == matched.assetDigest
            } else {
                null
            }

        val isUpdateAvailable =
            when {
                skipHolds -> false
                selfAttestedSameFile != null -> !selfAttestedSameFile
                deviceRunsMatchedRelease -> false
                bound == null -> tagVerdict
                else ->
                    decideBound(
                        sameFile =
                            isSameFile(
                                installedAssetId = bound.assetId,
                                installedAssetDigest = bound.assetDigest,
                                matchedAssetId = matched.assetId,
                                matchedAssetDigest = matched.assetDigest,
                            ),
                        matchedPublishedAt = matched.publishedAt,
                        installedReleasePublishedAt = bound.releasePublishedAt,
                        fallback = tagVerdict,
                    )
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
    ): Boolean = when {
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
