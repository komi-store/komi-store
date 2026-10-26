package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath

fun InstalledApp.confirmInstall(
    build: InstalledBuild,
    at: Long,
    isPending: Boolean = false,
): InstalledApp {
    val timestampTrackedTag = VersionMath.isTimestampTrackedTag(build.tag)
    val landedCodeBelowTarget =
        latestVersionCode != null && latestVersionCode > 0L && build.versionCode < latestVersionCode
    val isUpdateStillAvailable = targetVersionStillNewerThan(build) || landedCodeBelowTarget
    val latestIsSkipped = VersionMath.isExactSameVersion(latestVersion, skippedReleaseTag)

    return copy(
        installedVersion = build.tag,
        installedAssetName = build.assetName,
        installedAssetUrl = build.assetUrl,
        installedVersionName = build.versionName,
        installedVersionCode = build.versionCode,
        installedReleaseId = build.releaseId,
        installedAssetId = build.assetId,
        installedAssetDigest = build.assetDigest,
        installedReleasePublishedAt = build.releasePublishedAt,
        isUpdateAvailable =
            when {
                latestIsSkipped -> false
                !timestampTrackedTag -> isUpdateStillAvailable
                else -> landedCodeBelowTarget
            },
        latestVersionCode =
            if (timestampTrackedTag || isUpdateStillAvailable) latestVersionCode else build.versionCode,
        isPendingInstall = isPending,
        lastUpdatedAt = at,
        lastCheckedAt = at,
        signingFingerprint = build.signingFingerprint ?: this.signingFingerprint,
        pendingInstallFilePath = pendingInstallFilePath.takeIf { isPending },
        pendingInstallVersion = pendingInstallVersion.takeIf { isPending },
        pendingInstallAssetName = pendingInstallAssetName.takeIf { isPending },
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
        pendingInstallReleasePublishedAt = null,
    )
}

private fun InstalledApp.targetVersionStillNewerThan(build: InstalledBuild): Boolean {
    val installedSide =
        build.versionName.takeIf {
            it.isNotBlank() && VersionMath.versionsReconcilable(latestVersion, it)
        } ?: build.tag
    return !latestVersion.isNullOrBlank() && VersionMath.isVersionNewer(latestVersion, installedSide)
}

fun InstalledApp.snapshotStillNamesNewerBuild(
    installedCode: Long,
    installedVersion: String?,
): Boolean {
    val snapshotCode = latestVersionCode
    if (snapshotCode != null && snapshotCode > 0L) return snapshotCode > installedCode
    return VersionMath.isVersionNewer(latestVersion, installedVersion)
}

fun InstalledApp.normalizeInstalledTag(
    tag: String,
    isUpdateAvailable: Boolean,
): InstalledApp =
    copy(
        installedVersion = tag,
        isUpdateAvailable = isUpdateAvailable,
    )

fun InstalledApp.withMigratedVersionInfo(
    versionName: String?,
    versionCode: Long,
): InstalledApp =
    copy(
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        latestVersionName = if (versionCode > 0L) versionName else latestVersionName,
        latestVersionCode = if (versionCode > 0L) versionCode else latestVersionCode,
    )

fun InstalledApp.withLatestSnapshot(
    version: String,
    assetName: String?,
    assetUrl: String?,
    versionName: String?,
    versionCode: Long?,
): InstalledApp =
    copy(
        latestVersion = version,
        latestAssetName = assetName,
        latestAssetUrl = assetUrl,
        latestVersionName = versionName,
        latestVersionCode = versionCode,
    )
