package zed.rainxch.core.data.repository

import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.resolveInstalledReleasePublishedAt
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.UpdateVerdict
import zed.rainxch.core.domain.utils.VersionMath

// The verdict compares the installed build with the matched release. The stored identity is
// preferred, the fetched window covers deletes/rotation, and a legacy row without a digest gets
// one local confirmation only when the timestamp moved (a suspected re-release).
@Suppress("LongParameterList")
internal suspend fun resolveInstalledBinding(
    app: InstalledAppEntity,
    releases: List<GithubRelease>,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    dao: InstalledAppDao,
    packageMonitor: PackageMonitor,
    verifier: DigestVerifier,
): UpdateVerdict.Bound? {
    if (app.installedAssetId == null && app.installedAssetDigest == null) return null
    val installedPublishedAt = dao.resolveInstalledReleasePublishedAt(app, releases)
    val confirmedDigest =
        confirmInstalledDigest(
            app = app,
            matchedTag = matchedRelease.tagName,
            matchedPublishedAt = matchedRelease.publishedAt,
            installedPublishedAt = installedPublishedAt,
            matchedDigest = matchedAsset.digest,
            packageMonitor = packageMonitor,
            verifier = verifier,
        )
    if (confirmedDigest != null && confirmedDigest == matchedAsset.digest) {
        // The installed bytes are the release's bytes: bind them once so later checks compare
        // digests instead of hashing the APK again.
        dao.setInstalledAssetDigest(app.packageName, confirmedDigest)
    }
    return UpdateVerdict.Bound(
        assetId = app.installedAssetId,
        assetDigest = app.installedAssetDigest ?: confirmedDigest,
        releasePublishedAt = installedPublishedAt,
    )
}

// The decision is a pure function so the trigger rules are testable without a device; only
// the hashing itself needs the platform.
internal data class DigestConfirmationInput(
    val installedAssetId: Long?,
    val installedAssetDigest: String?,
    val installedVersion: String,
    val matchedTag: String,
    val matchedDigest: String?,
    val matchedPublishedAt: String?,
    val installedPublishedAt: String?,
)

internal fun shouldConfirmDigest(input: DigestConfirmationInput): Boolean {
    val legacyWithoutDigest =
        input.installedAssetDigest == null &&
            input.installedAssetId != null &&
            input.matchedDigest != null
    return legacyWithoutDigest &&
        VersionMath.isExactSameVersion(input.matchedTag, input.installedVersion) &&
        VersionMath.publishedAtAfter(input.matchedPublishedAt, input.installedPublishedAt)
}

@Suppress("LongParameterList")
private suspend fun confirmInstalledDigest(
    app: InstalledAppEntity,
    matchedTag: String,
    matchedPublishedAt: String?,
    installedPublishedAt: String?,
    matchedDigest: String?,
    packageMonitor: PackageMonitor,
    verifier: DigestVerifier,
): String? {
    val confirmation =
        DigestConfirmationInput(
            installedAssetId = app.installedAssetId,
            installedAssetDigest = app.installedAssetDigest,
            installedVersion = app.installedVersion,
            matchedTag = matchedTag,
            matchedDigest = matchedDigest,
            matchedPublishedAt = matchedPublishedAt,
            installedPublishedAt = installedPublishedAt,
        )
    if (!shouldConfirmDigest(confirmation)) return null
    // The platform lookup is only safe to run once the trigger rules asked for it: on Android it
    // rethrows anything but not-found, which would fail the whole update check.
    val installedApkPath = packageMonitor.getInstalledPackageInfo(app.packageName)?.apkPath
    return installedApkPath?.let { verifier.computeSha256(it) }
}
