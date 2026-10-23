package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict

@Suppress("LongParameterList", "CyclomaticComplexMethod")
fun InstalledApp.confirmInstall(
    tag: String,
    releaseId: Long? = null,
    assetId: Long? = null,
    assetDigest: String? = null,
    releasePublishedAt: String? = null,
    // Unknown must be null, never "": "" never equals a real asset name.
    assetName: String?,
    assetUrl: String?,
    versionName: String,
    versionCode: Long,
    signingFingerprint: String?,
    at: Long,
    isPending: Boolean = false,
): InstalledApp {
    val timestampTrackedTag = VersionMath.isTimestampTrackedTag(tag)
    val previousSnapshotCode = latestVersionCode

    val installedSide =
        versionName.takeIf {
            it.isNotBlank() && VersionMath.versionsReconcilable(latestVersion, it)
        } ?: tag
    val targetVersionStillNewer =
        !latestVersion.isNullOrBlank() &&
            VersionMath.isVersionNewer(latestVersion, installedSide)
    val landedCodeBelowTarget =
        latestVersionCode != null && latestVersionCode > 0L && versionCode < latestVersionCode
    val isUpdateStillAvailable = targetVersionStillNewer || landedCodeBelowTarget
    val latestIsSkipped = VersionMath.isExactSameVersion(latestVersion, skippedReleaseTag)

    val parkedFile = if (isPending) pendingInstallFilePath else null
    val parkedVersion = if (isPending) pendingInstallVersion else null
    val parkedAsset = if (isPending) pendingInstallAssetName else null

    return copy(
        installedVersion = tag,
        installedAssetName = assetName,
        installedAssetUrl = assetUrl,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        installedReleaseId = releaseId,
        installedAssetId = assetId,
        installedAssetDigest = assetDigest,
        installedReleasePublishedAt = releasePublishedAt,
        isUpdateAvailable =
            when {
                latestIsSkipped -> false
                !timestampTrackedTag -> isUpdateStillAvailable
                landedCodeBelowTarget -> true
                else -> false
            },
        latestVersionCode =
            if (timestampTrackedTag || isUpdateStillAvailable) previousSnapshotCode else versionCode,
        isPendingInstall = isPending,
        lastUpdatedAt = at,
        lastCheckedAt = at,
        signingFingerprint = signingFingerprint ?: this.signingFingerprint,
        pendingInstallFilePath = parkedFile,
        pendingInstallVersion = parkedVersion,
        pendingInstallAssetName = parkedAsset,
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
        pendingInstallReleasePublishedAt = null,
    )
}

fun InstalledApp.resolvePendingFromSystem(reached: PendingInstallResolution.Reached): InstalledApp =
    withSettledInstallIdentity(reached.versionCode).copy(
        isPendingInstall = false,
        installedVersion = reached.resolvedTag,
        installedVersionName = reached.versionName,
        installedVersionCode = reached.versionCode,
        isUpdateAvailable = updateFlagAgainstSnapshot(reached.versionCode, reached.versionName),
    )

// The device runs the parked build byte for byte even when the version number did not
// change (test builds keep one versionCode). Settle the park without asking the tag to prove it.
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
    val recordedBuildStands = !parkedBuildLanded && versionCode == installedVersionCode
    return copy(
        installedReleaseId =
            when {
                parkedBuildLanded -> pendingInstallReleaseId
                recordedBuildStands -> installedReleaseId
                else -> null
            },
        installedAssetId =
            when {
                parkedBuildLanded -> pendingInstallAssetId
                recordedBuildStands -> installedAssetId
                else -> null
            },
        installedAssetDigest =
            when {
                parkedBuildLanded -> pendingInstallAssetDigest
                recordedBuildStands -> installedAssetDigest
                else -> null
            },
        installedReleasePublishedAt =
            when {
                parkedBuildLanded -> pendingInstallReleasePublishedAt
                recordedBuildStands -> installedReleasePublishedAt
                else -> null
            },
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
        pendingInstallReleasePublishedAt = null,
    )
}

sealed interface PendingInstallResolution {
    data object Keep : PendingInstallResolution

    class Reached internal constructor(
        val resolvedTag: String,
        val versionName: String,
        val versionCode: Long,
    ) : PendingInstallResolution
}

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

        // A rolling build reuses one tag across builds, so a matching name proves nothing.
        // Only a version code or the parked release identity can settle those.
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

// #998 records both what the park points at and what the device runs. When the two name the
// same release, or the device already runs the parked version tag, there is nothing left to
// install: a duplicate install has to clear the park instead of leaving it on screen forever.
private fun InstalledApp.parkedBuildAlreadyOnDevice(versionCode: Long): Boolean {
    // The installed fields count only while the device runs the recorded build: a park from
    // Details writes the downloaded build into them before the system installs it.
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

private fun InstalledApp.updateFlagAgainstSnapshot(
    installedCode: Long,
    installedVersion: String?,
): Boolean {
    val snapshotCode = latestVersionCode
    if (snapshotCode != null && snapshotCode > 0L) return snapshotCode > installedCode
    return VersionMath.isVersionNewer(latestVersion, installedVersion)
}

fun InstalledApp.snapshotStillNamesNewerBuild(
    installedCode: Long,
    installedVersion: String?,
): Boolean = updateFlagAgainstSnapshot(installedCode, installedVersion)

fun InstalledApp.externalInstallUpdateFlag(
    newVersionName: String,
    newVersionCode: Long,
): Boolean =
    when (resolveExternalInstallVerdict(this, newVersionName, newVersionCode)) {
        VersionVerdict.UP_TO_DATE -> false
        VersionVerdict.UPDATE_AVAILABLE -> true
        VersionVerdict.UNKNOWN -> isUpdateAvailable
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

fun InstalledApp.tagForObservedBuild(
    versionName: String?,
    versionCode: Long,
): String {
    val snapshotTag = latestVersion?.takeIf { it.isNotBlank() } ?: return installedVersion
    val codeProvesSnapshot =
        latestVersionCode != null && latestVersionCode > 0L && versionCode == latestVersionCode
    val nameProvesSnapshot =
        versionName != null &&
            VersionMath.versionsReconcilable(versionName, snapshotTag) &&
            VersionMath.isSameVersion(versionName, snapshotTag)
    return if (codeProvesSnapshot || nameProvesSnapshot) snapshotTag else installedVersion
}

fun InstalledApp.deviceChangeAgainst(local: SystemPackageInfo): DeviceChange {
    val signerDrifted = signerDiffersFrom(local)
    val versionMatches =
        local.versionCode == installedVersionCode && local.versionName == installedVersionName
    return when {
        versionMatches && !signerDrifted -> DeviceChange.NONE
        local.versionCode < installedVersionCode -> DeviceChange.DOWNGRADE
        versionMatches -> DeviceChange.SIGNER_CHANGE
        else -> DeviceChange.VERSION_CHANGE
    }
}

fun InstalledApp.bindingStatusAgainst(local: SystemPackageInfo): BindingStatus {
    if (local.packageName != packageName) {
        return BindingStatus.Broken(BindingStatus.BreakReason.PACKAGE_NAME)
    }

    if (local.versionCode > 0L &&
        installedVersionCode > 0L &&
        local.versionCode != installedVersionCode
    ) {
        return BindingStatus.Broken(BindingStatus.BreakReason.VERSION_CODE)
    }

    if (local.versionName.isNotBlank() &&
        !installedVersionName.isNullOrBlank() &&
        local.versionName != installedVersionName
    ) {
        return BindingStatus.Broken(BindingStatus.BreakReason.VERSION_NAME)
    }

    if (signerDiffersFrom(local)) {
        return BindingStatus.Broken(BindingStatus.BreakReason.SIGNING_FINGERPRINT)
    }

    return BindingStatus.Intact
}

private fun InstalledApp.signerDiffersFrom(local: SystemPackageInfo): Boolean {
    val localSign = local.signingFingerprint
    return !localSign.isNullOrBlank() &&
        !signingFingerprint.isNullOrBlank() &&
        !localSign.equals(signingFingerprint, ignoreCase = true)
}

fun InstalledApp.observeExternalInstall(
    versionName: String?,
    versionCode: Long,
    signingFingerprint: String? = null,
): InstalledApp {
    val adoptedTag = tagForObservedBuild(versionName, versionCode)
    return copy(
        installedVersion = adoptedTag,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        signingFingerprint = signingFingerprint ?: this.signingFingerprint,
        installedReleaseId = null,
        installedAssetId = null,
        installedAssetDigest = null,
        installedReleasePublishedAt = null,
        isUpdateAvailable = updateFlagAgainstSnapshot(versionCode, versionName ?: adoptedTag),
    )
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
