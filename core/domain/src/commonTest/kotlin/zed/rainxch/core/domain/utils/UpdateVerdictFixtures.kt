package zed.rainxch.core.domain.utils

internal fun decideVerdict(
    installedTag: String = "1.0.0",
    installedVersionCode: Long = 100L,
    installedVersionName: String? = null,
    storedLatestTag: String? = null,
    storedLatestVersionCode: Long? = null,
    storedPublishedAt: String? = null,
    wasUpdateAvailable: Boolean = false,
    skippedTag: String? = null,
    matchedTag: String = "1.1.0",
    matchedPublishedAt: String? = "2026-08-01T00:00:00Z",
    matchedIsPrerelease: Boolean = false,
    storedAssetDigest: String? = null,
    storedAssetSize: Long? = null,
    matchedAssetDigest: String? = null,
    matchedAssetSize: Long? = null,
    storedReleaseId: Long? = null,
    storedAssetId: Long? = null,
    matchedReleaseId: Long? = null,
    matchedAssetId: Long? = null,
    bound: UpdateVerdict.Bound? = null,
    selfAttestedDigest: String? = null,
): UpdateVerdict.Result =
    UpdateVerdict.decide(
        installed = UpdateVerdict.Installed(installedTag, installedVersionCode, installedVersionName),
        stored =
            UpdateVerdict.Stored(
                latestTag = storedLatestTag,
                latestVersionCode = storedLatestVersionCode,
                publishedAt = storedPublishedAt,
                wasUpdateAvailable = wasUpdateAvailable,
                latestReleaseId = storedReleaseId,
                latestAssetId = storedAssetId,
                latestAssetDigest = storedAssetDigest,
                latestAssetSize = storedAssetSize,
            ),
        matched =
            UpdateVerdict.Matched(
                tag = matchedTag,
                publishedAt = matchedPublishedAt,
                isPrerelease = matchedIsPrerelease,
                releaseId = matchedReleaseId,
                assetId = matchedAssetId,
                assetDigest = matchedAssetDigest,
                assetSize = matchedAssetSize,
            ),
        skippedTag = skippedTag,
        bound = bound,
        selfAttestedDigest = selfAttestedDigest,
    )
