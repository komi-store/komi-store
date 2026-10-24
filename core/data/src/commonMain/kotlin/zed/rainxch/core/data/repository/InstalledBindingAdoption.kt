package zed.rainxch.core.data.repository

import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.adoptMatchedTag
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease

// When the digest is already proven equal, adoption also refreshes the release identity so
// the tag and the identity fields can never describe different releases.
@Suppress("LongParameterList")
internal suspend fun adoptTagWhenProven(
    dao: InstalledAppDao,
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    isUpdateAvailable: Boolean,
    boundAt: Long,
) {
    val provenDigest = matchedAsset.digest
    if (provenDigest != null &&
        app.installedAssetDigest != null &&
        app.installedAssetDigest == provenDigest
    ) {
        dao.adoptMatchedTagWithIdentity(
            packageName = app.packageName,
            installedVersion = matchedRelease.tagName,
            installedVersionName = app.installedVersionName,
            installedVersionCode = app.installedVersionCode,
            releaseId = matchedRelease.id,
            assetId = matchedAsset.id,
            digest = provenDigest,
            releasePublishedAt = matchedRelease.publishedAt,
            boundAt = boundAt,
        )
    } else if (app.installedAssetId == null &&
        app.installedAssetDigest == null &&
        app.installedReleaseId == null
    ) {
        // Legacy row without any identity: there is nothing to contradict, so the tag can
        // still be adopted. A row that already names bytes must not be relabelled without
        // proof; otherwise the tag and the identity describe different releases.
        dao.adoptMatchedTag(
            app = app,
            matchedTag = matchedRelease.tagName,
            isUpdateAvailable = isUpdateAvailable,
        )
    }
}

// The installed bytes match the matched asset: bind the tag and the identity together.
@Suppress("LongParameterList")
internal suspend fun rebindProvenIdentity(
    dao: InstalledAppDao,
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    matchedDigest: String,
    boundAt: Long,
) {
    val identityCurrent =
        app.installedAssetDigest == matchedDigest &&
            app.installedAssetId == matchedAsset.id &&
            app.installedReleaseId == matchedRelease.id &&
            app.installedVersion == matchedRelease.tagName
    if (identityCurrent) return
    dao.adoptMatchedTagWithIdentity(
        packageName = app.packageName,
        installedVersion = matchedRelease.tagName,
        installedVersionName = app.installedVersionName,
        installedVersionCode = app.installedVersionCode,
        releaseId = matchedRelease.id,
        assetId = matchedAsset.id,
        digest = matchedDigest,
        releasePublishedAt = matchedRelease.publishedAt,
        boundAt = boundAt,
    )
}

// Adoption may rewrite the tag only when the stored identity does not contradict the release.
internal fun identityContradicts(
    app: InstalledAppEntity,
    matchedAsset: GithubAsset,
): Boolean =
    (
        app.installedAssetDigest != null &&
            matchedAsset.digest != null &&
            app.installedAssetDigest != matchedAsset.digest
    ) ||
        (
            app.installedAssetDigest == null &&
                app.installedAssetId != null &&
                app.installedAssetId != matchedAsset.id
        )
