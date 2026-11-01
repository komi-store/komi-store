package zed.rainxch.core.domain.utils

object UpdateVerdict {
    data class Installed(
        val tag: String?,
        val versionCode: Long,
        val versionName: String? = null,
        val publishedAt: String? = null,
        val releaseId: Long? = null,
        val assetId: Long? = null,
        val assetName: String? = null,
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
        val assetName: String? = null,
    )

    data class Bound(
        val assetId: Long?,
        val assetDigest: String?,
        val releasePublishedAt: String?,
    )

    // A Details refresh names a release but cannot read the installed file, so it has no way to
    // prove an unbound, same-tag release is a different build from the one on the device.
    fun decide(
        installed: Installed,
        stored: Stored,
        matched: Matched,
        skippedTag: String?,
        bound: Bound? = null,
        selfAttestedDigest: String? = null,
        canReadInstalledFile: Boolean = true,
        detectAssetRebuildsInRelease: Boolean = true,
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
        val distinctTagPublishedLater =
            !sameTag &&
                VersionMath.isPublishedAtAfter(matched.publishedAt, installed.publishedAt)
        val sameTagUnprovable = !canReadInstalledFile && bound == null && sameTag
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

        val distinctTagNeedsPublishTime =
            distinctTagPublishedLater &&
                (
                    VersionMath.detectScheme(matched.tag) == VersionMath.Scheme.Unknown ||
                        VersionMath.isTimestampTrackedTag(matched.tag) ||
                        !reconcilable
                )
        val tagVerdict =
            when {
                distinctTagNeedsPublishTime -> {
                    true
                }

                usedTimestampLogic -> {
                    timestampWouldReport || distinctTagPublishedLater
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
        // A forge can rebuild an asset inside the same release (same tag, same release id,
        // same publishedAt) and only the asset identity moves. Asset ids are monotonic, so a
        // newer asset id on the same release is a rebuild even when the timestamp is equal.
        // The bytes are the primary proof: an installed digest that differs from the resolved
        // one is a different build whatever the timestamps say.
        val installedDigestDiffers =
            bound?.assetDigest != null &&
                matched.assetDigest != null &&
                bound.assetDigest != matched.assetDigest
        val sameReleaseIdentity =
            matched.releaseId != null &&
                (
                    matched.releaseId == installed.releaseId ||
                        matched.releaseId == stored.latestReleaseId
                )
        val sameReleaseAssetAdvanced =
            sameReleaseIdentity &&
                matched.assetId != null &&
                installed.assetId != null &&
                matched.assetId > installed.assetId
        // Off keeps the former tag plus publish-time comparison only: no bytes-without-time,
        // no asset identity and no filename ordering.
        val rebuildWithoutNewPublishTime =
            detectAssetRebuildsInRelease && (installedDigestDiffers || sameReleaseAssetAdvanced)
        val assetVersionOrder =
            if (detectAssetRebuildsInRelease && sameTag) {
                orderAssetVersions(installed.assetName, matched.assetName)
            } else {
                null
            }
        val sameTagFilenameRegressed = assetVersionOrder != null && assetVersionOrder < 0
        val sameTagFilenameAdvanced = assetVersionOrder != null && assetVersionOrder > 0
        val sameTagRebuild =
            sameTag &&
                bound != null &&
                installedBytesDiffer &&
                (
                    VersionMath.isPublishedAtAfter(matched.publishedAt, bound.releasePublishedAt) ||
                        rebuildWithoutNewPublishTime
                )

        val isUpdateAvailable =
            when {
                skipHolds -> {
                    false
                }

                sameTagUnprovable -> {
                    false
                }

                selfAttestedSameFile != null -> {
                    !selfAttestedSameFile
                }

                // Equal bytes outrank every rebuild signal, including a filename that says the
                // release moved on.
                boundMatchesInstalledFile -> {
                    false
                }

                sameTagFilenameRegressed -> {
                    false
                }

                sameTagRebuild -> {
                    true
                }

                sameTagFilenameAdvanced -> {
                    true
                }

                deviceRunsMatchedRelease -> {
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
        // A stored identity that names different bytes must not be relabelled with the
        // matched tag: the row would then describe a release it never installed.
        identityContradicts: Boolean = false,
    ): Boolean =
        installedTag != matchedTag &&
            !identityContradicts &&
            (codesAlreadyMatch || deviceRunsMatchedRelease)

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

    // Negative when the installed name carries the newer version, positive when the resolved
    // one does, null when either name has no dotted version to order by.
    private fun orderAssetVersions(
        installedAssetName: String?,
        matchedAssetName: String?,
    ): Int? {
        val installedVersion = VersionMath.assetVersionInName(installedAssetName)
        val matchedVersion = VersionMath.assetVersionInName(matchedAssetName)
        if (installedVersion == null || matchedVersion == null) return null
        return VersionMath.compareAssetVersions(matchedVersion, installedVersion)
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
