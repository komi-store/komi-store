package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict

fun InstalledApp.confirmInstall(
    tag: String,
    releaseId: Long? = null,
    assetId: Long? = null,
    assetDigest: String? = null,
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
        signingFingerprint = signingFingerprint,
        pendingInstallFilePath = parkedFile,
        pendingInstallVersion = parkedVersion,
        pendingInstallAssetName = parkedAsset,
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
    )
}

// The adopt write for a park the system has proven: the resolution already carries the tag the
// row should record and the build the system reported, so nothing here re-derives the gate and
// no caller can adopt without one. The gate itself lives in pendingInstallResolution.
fun InstalledApp.resolvePendingFromSystem(reached: PendingInstallResolution.Reached): InstalledApp =
    withSettledInstallIdentity(reached.versionCode).copy(
        isPendingInstall = false,
        installedVersion = reached.resolvedTag,
        installedVersionName = reached.versionName,
        installedVersionCode = reached.versionCode,
        isUpdateAvailable = updateFlagAgainstSnapshot(reached.versionCode, reached.versionName),
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
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
    )
}

// The keep/adopt decision every pending-install resolver shares: the library sync, the startup
// self-update check and the package broadcast all resolve the same park, and a park must not be
// torn down without proof the install landed. Reached carries what the adopt write needs, and its
// constructor stays module-internal: only the gate below can mint the proof, so the adopt write
// cannot be driven without one. Test source sets are friends and keep constructing it.
sealed interface PendingInstallResolution {
    data object Keep : PendingInstallResolution

    class Reached internal constructor(
        val resolvedTag: String,
        val versionName: String,
        val versionCode: Long,
    ) : PendingInstallResolution
}

// A park resolves only when the system reports the target build, or, for a code-less target (a
// tag-tracked release), when the version name moved off what the row recorded. No answer and no
// proof both keep the park: the file on disk is still what the user needs.
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

// The one "did the target land" predicate: a code proves it when the row carries one, and for a
// code-less (tag-tracked) target a moved version name stands in, the same fallback the package
// broadcast already used.
private fun InstalledApp.installReachedTarget(versionName: String, versionCode: Long): Boolean {
    val targetCode = latestVersionCode ?: 0L
    return if (targetCode > 0L) {
        versionCode >= targetCode
    } else {
        // A code-less (tag-tracked) target has no code to compare, so the version name carries the
        // proof. A row that recorded no baseline (a fresh install, or data from before the column)
        // counts as moved: the package appearing on the system at all is the change to go by. The
        // boundary's placeholder for "no name reported" is not a name and keeps the park, like a
        // blank one.
        versionName.isNotBlank() &&
            versionName != SystemPackageInfo.UNKNOWN_VERSION_NAME &&
            (
                installedVersionName == null ||
                    versionName != installedVersionName ||
                    // Or the name is the park's own target: after a confirmed install the baseline
                    // has already been rewritten to it, so "moved" can no longer be seen and this
                    // is what keeps the retry of a failed discard reachable.
                    matchesParkedTargetName(versionName)
            )
    }
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
): InstalledApp = copy(
    installedVersion = tag,
    isUpdateAvailable = isUpdateAvailable,
)

fun InstalledApp.withMigratedVersionInfo(
    versionName: String?,
    versionCode: Long,
): InstalledApp = copy(
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
        isUpdateAvailable = updateFlagAgainstSnapshot(versionCode, versionName ?: adoptedTag),
    )
}

fun InstalledApp.markPending(
    releaseId: Long?,
    assetId: Long?,
    assetDigest: String?,
): InstalledApp = copy(
    isPendingInstall = true,
    pendingInstallReleaseId = releaseId,
    pendingInstallAssetId = assetId,
    pendingInstallAssetDigest = assetDigest,
)

fun InstalledApp.clearPending(): InstalledApp = copy(
    isPendingInstall = false,
    pendingInstallReleaseId = null,
    pendingInstallAssetId = null,
    pendingInstallAssetDigest = null,
)

fun InstalledApp.withLatestSnapshot(
    version: String,
    assetName: String?,
    assetUrl: String?,
    versionName: String?,
    versionCode: Long?,
): InstalledApp = copy(
    latestVersion = version,
    latestAssetName = assetName,
    latestAssetUrl = assetUrl,
    latestVersionName = versionName,
    latestVersionCode = versionCode,
)
