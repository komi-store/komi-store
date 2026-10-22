package zed.rainxch.apps.presentation.mappers

import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.core.domain.model.installation.DeviceApp

fun DeviceApp.toUi(): DeviceAppUi =
    DeviceAppUi(
        packageName = packageName,
        appName = appName,
        versionName = versionName,
        versionCode = versionCode,
        signingFingerprint = signingFingerprint,
        installerPackageName = installerPackageName,
        isUpdatedSystemApp = isUpdatedSystemApp,
    )

fun DeviceAppUi.toDomain(): DeviceApp =
    DeviceApp(
        packageName = packageName,
        appName = appName,
        versionName = versionName,
        versionCode = versionCode,
        signingFingerprint = signingFingerprint,
        installerPackageName = installerPackageName,
        isUpdatedSystemApp = isUpdatedSystemApp,
    )
