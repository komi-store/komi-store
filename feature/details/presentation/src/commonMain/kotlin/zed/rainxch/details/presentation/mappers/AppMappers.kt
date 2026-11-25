package zed.rainxch.details.presentation.mappers

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.apk.ApkInspection
import zed.rainxch.core.domain.model.apk.ApkPermission
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.utils.ReleaseChange
import zed.rainxch.details.presentation.model.ApkInspectionUi
import zed.rainxch.details.presentation.model.ApkPermissionUi
import zed.rainxch.details.presentation.model.InstalledAppUi
import zed.rainxch.details.presentation.model.SigningKeyPromptUi
import zed.rainxch.details.presentation.model.SigningKeyWarning

internal fun InstalledApp.toUi(): InstalledAppUi =
    InstalledAppUi(
        packageName = packageName,
        appName = appName,
        installedVersion = installedVersion,
        latestVersion = latestVersion,
        isPendingInstall = isPendingInstall,
        isUpdateAvailable = isUpdateAvailable,
        installSource = installSource,
        includePreReleases = includePreReleases,
        latestBuildDiffers =
            ReleaseChange.assetBuildChanged(
                matched = ReleaseChange.BuildIdentity(assetId = latestAssetId, digest = latestAssetDigest),
                stored = ReleaseChange.BuildIdentity(assetId = installedAssetId, digest = installedAssetDigest),
            ),
    )

internal fun ApkInspection.toUi(): ApkInspectionUi =
    ApkInspectionUi(
        appLabel = appLabel,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        signingFingerprint = signingFingerprint,
        minSdk = minSdk,
        targetSdk = targetSdk,
        permissions = permissions.map { it.toUi() }.toImmutableList(),
        mainActivity = mainActivity,
        activityCount = activityCount,
        serviceCount = serviceCount,
        receiverCount = receiverCount,
        fileSizeBytes = fileSizeBytes,
        filePath = filePath,
        debuggable = debuggable,
        source = source,
    )

private fun ApkPermission.toUi(): ApkPermissionUi =
    ApkPermissionUi(
        name = name,
        displayName = displayName,
        description = description,
        protectionLevel = protectionLevel,
    )

internal fun SigningKeyWarning.toUi(): SigningKeyPromptUi =
    SigningKeyPromptUi(expectedFingerprint = expectedFingerprint, actualFingerprint = actualFingerprint)
