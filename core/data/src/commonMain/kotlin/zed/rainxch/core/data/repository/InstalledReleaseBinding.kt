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

// What proves which build the device runs: the stored binding, or the installed file's own digest.
internal class InstalledReleaseBinder(
    private val dao: InstalledAppDao,
    private val packageMonitor: PackageMonitor,
    private val verifier: DigestVerifier,
) {
    // A legacy row without a digest gets one local confirmation, only when the timestamp moved (a suspected re-release).
    suspend fun resolve(
        app: InstalledAppEntity,
        releases: List<GithubRelease>,
        matchedRelease: GithubRelease,
        matchedAsset: GithubAsset,
    ): UpdateVerdict.Bound? {
        if (app.installedAssetId == null && app.installedAssetDigest == null) return null
        val installedPublishedAt = dao.resolveInstalledReleasePublishedAt(app, releases)
        val confirmedDigest = confirmInstalledDigest(app, matchedRelease, matchedAsset, installedPublishedAt)
        if (confirmedDigest != null && confirmedDigest == matchedAsset.digest) {
            // Bound once, so later checks compare digests instead of hashing the APK again.
            dao.setInstalledAssetDigest(app.packageName, confirmedDigest)
        }
        return UpdateVerdict.Bound(
            assetId = app.installedAssetId,
            assetDigest = app.installedAssetDigest ?: confirmedDigest,
            releasePublishedAt = installedPublishedAt,
        )
    }

    // A rolling tag reuses one tag string across builds, so without a binding only the installed file can tell.
    suspend fun selfAttestedDigest(
        app: InstalledAppEntity,
        matchedRelease: GithubRelease,
        matchedAsset: GithubAsset,
        bound: UpdateVerdict.Bound?,
    ): String? {
        val tag = matchedRelease.tagName
        val tagNeedsSelfAttestation =
            VersionMath.isTimestampTrackedTag(tag) || VersionMath.detectScheme(tag) == VersionMath.Scheme.Unknown
        val applies =
            bound == null &&
                matchedAsset.digest != null &&
                VersionMath.isExactSameVersion(tag, app.installedVersion) &&
                tagNeedsSelfAttestation
        return if (applies) installedApkDigest(app.packageName) else null
    }

    private suspend fun confirmInstalledDigest(
        app: InstalledAppEntity,
        matchedRelease: GithubRelease,
        matchedAsset: GithubAsset,
        installedPublishedAt: String?,
    ): String? {
        val confirmation =
            DigestConfirmationInput(
                installedAssetId = app.installedAssetId,
                installedAssetDigest = app.installedAssetDigest,
                installedVersion = app.installedVersion,
                matchedTag = matchedRelease.tagName,
                matchedDigest = matchedAsset.digest,
                matchedPublishedAt = matchedRelease.publishedAt,
                installedPublishedAt = installedPublishedAt,
            )
        // Only after the trigger rules ask: on Android the lookup rethrows anything but not-found.
        return if (shouldConfirmDigest(confirmation)) installedApkDigest(app.packageName) else null
    }

    private suspend fun installedApkDigest(packageName: String): String? =
        packageMonitor.getInstalledPackageInfo(packageName)?.apkPath?.let { verifier.computeSha256(it) }
}

// A pure decision, so the trigger rules are testable without a device.
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
