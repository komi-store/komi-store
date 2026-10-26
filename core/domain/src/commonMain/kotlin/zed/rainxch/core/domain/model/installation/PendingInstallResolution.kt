package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath

sealed interface PendingInstallResolution {
    data object Keep : PendingInstallResolution

    class Reached internal constructor(
        val resolvedTag: String,
        val versionName: String,
        val versionCode: Long,
    ) : PendingInstallResolution
}

fun InstalledApp.resolvePendingFromSystem(reached: PendingInstallResolution.Reached): InstalledApp =
    withSettledInstallIdentity(reached.versionCode).copy(
        isPendingInstall = false,
        installedVersion = reached.resolvedTag,
        installedVersionName = reached.versionName,
        installedVersionCode = reached.versionCode,
        isUpdateAvailable = snapshotStillNamesNewerBuild(reached.versionCode, reached.versionName),
    )

// The device runs the parked build byte for byte even when test builds keep one versionCode; no tag proof is needed.
fun InstalledApp.settlePendingFromSystem(
    versionName: String,
    versionCode: Long,
): InstalledApp =
    resolvePendingFromSystem(
        PendingInstallResolution.Reached(
            resolvedTag = pendingInstallVersion ?: latestVersion ?: versionName,
            versionName = versionName,
            versionCode = versionCode,
        ),
    ).copy(
        pendingInstallFilePath = null,
        pendingInstallVersion = null,
        pendingInstallAssetName = null,
    )

fun InstalledApp.withSettledInstallIdentity(versionCode: Long): InstalledApp {
    val hasParkedIdentity =
        pendingInstallReleaseId != null || pendingInstallAssetId != null || pendingInstallAssetDigest != null
    val targetCode = latestVersionCode ?: 0L
    val parkedBuildLanded = hasParkedIdentity && targetCode > 0L && versionCode == targetCode
    val identity =
        when {
            parkedBuildLanded -> parkedIdentity()
            versionCode == installedVersionCode -> installedIdentity()
            else -> null
        }
    return copy(
        installedReleaseId = identity?.releaseId,
        installedAssetId = identity?.assetId,
        installedAssetDigest = identity?.assetDigest,
        installedReleasePublishedAt = identity?.releasePublishedAt,
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
        pendingInstallReleasePublishedAt = null,
    )
}

private class ReleaseIdentity(
    val releaseId: Long?,
    val assetId: Long?,
    val assetDigest: String?,
    val releasePublishedAt: String?,
)

private fun InstalledApp.parkedIdentity(): ReleaseIdentity =
    ReleaseIdentity(
        releaseId = pendingInstallReleaseId,
        assetId = pendingInstallAssetId,
        assetDigest = pendingInstallAssetDigest,
        releasePublishedAt = pendingInstallReleasePublishedAt,
    )

private fun InstalledApp.installedIdentity(): ReleaseIdentity =
    ReleaseIdentity(
        releaseId = installedReleaseId,
        assetId = installedAssetId,
        assetDigest = installedAssetDigest,
        releasePublishedAt = installedReleasePublishedAt,
    )

fun InstalledApp.pendingInstallResolution(systemInfo: SystemPackageInfo?): PendingInstallResolution {
    if (systemInfo == null) return PendingInstallResolution.Keep
    if (!installReachedTarget(systemInfo.versionName, systemInfo.versionCode)) {
        return PendingInstallResolution.Keep
    }
    return PendingInstallResolution.Reached(
        resolvedTag = pendingInstallVersion ?: latestVersion ?: systemInfo.versionName,
        versionName = systemInfo.versionName,
        versionCode = systemInfo.versionCode,
    )
}

private fun InstalledApp.installReachedTarget(
    versionName: String,
    versionCode: Long,
): Boolean {
    val targetCode = latestVersionCode ?: 0L
    return when {
        parkedBuildAlreadyOnDevice(versionCode) -> {
            true
        }

        targetCode > 0L -> {
            versionCode >= targetCode
        }

        // A rolling build reuses one tag, so a matching name proves nothing; only a version code or the parked identity can.
        parkedTagIsAmbiguous() -> {
            false
        }

        else -> {
            versionName.isNotBlank() &&
                versionName != SystemPackageInfo.UNKNOWN_VERSION_NAME &&
                (
                    installedVersionName == null ||
                        versionName != installedVersionName ||
                        matchesParkedTargetName(versionName)
                )
        }
    }
}

private fun InstalledApp.parkedTagIsAmbiguous(): Boolean {
    val parkedTag = pendingInstallVersion ?: latestVersion ?: return false
    return VersionMath.isTimestampTrackedTag(parkedTag) ||
        VersionMath.detectScheme(parkedTag) == VersionMath.Scheme.Unknown
}

// #998: when the park and the device name the same release or parked tag, a duplicate install clears the park.
private fun InstalledApp.parkedBuildAlreadyOnDevice(versionCode: Long): Boolean {
    // The installed fields count only while the device runs the recorded build; a Details park writes them early.
    if (installedVersionCode <= 0L || versionCode != installedVersionCode) return false
    val sameRelease =
        pendingInstallReleaseId != null && pendingInstallReleaseId == installedReleaseId
    val parkedTag = pendingInstallVersion ?: latestVersion
    return sameRelease ||
        (
            parkedTag != null &&
                !parkedTagIsAmbiguous() &&
                VersionMath.isSameVersion(parkedTag, installedVersion)
        )
}

private fun InstalledApp.matchesParkedTargetName(systemName: String): Boolean {
    val target = pendingInstallVersion ?: latestVersion ?: return false
    return VersionMath.isExactSameVersion(systemName, target)
}

fun InstalledApp.markPending(
    releaseId: Long?,
    assetId: Long?,
    assetDigest: String?,
    releasePublishedAt: String? = null,
): InstalledApp =
    copy(
        isPendingInstall = true,
        pendingInstallReleaseId = releaseId,
        pendingInstallAssetId = assetId,
        pendingInstallAssetDigest = assetDigest,
        pendingInstallReleasePublishedAt = releasePublishedAt,
    )

fun InstalledApp.clearPending(): InstalledApp =
    copy(
        isPendingInstall = false,
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
        pendingInstallReleasePublishedAt = null,
    )
