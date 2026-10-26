package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict

fun InstalledApp.externalInstallUpdateFlag(
    newVersionName: String,
    newVersionCode: Long,
): Boolean =
    when (resolveExternalInstallVerdict(this, newVersionName, newVersionCode)) {
        VersionVerdict.UP_TO_DATE -> false
        VersionVerdict.UPDATE_AVAILABLE -> true
        VersionVerdict.UNKNOWN -> isUpdateAvailable
    }

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
        isUpdateAvailable = snapshotStillNamesNewerBuild(versionCode, versionName ?: adoptedTag),
    )
}
