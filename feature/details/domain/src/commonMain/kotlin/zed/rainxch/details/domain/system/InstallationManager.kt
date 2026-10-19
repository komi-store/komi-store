package zed.rainxch.details.domain.system

import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.details.domain.model.ApkValidationResult
import zed.rainxch.details.domain.model.FingerprintCheckResult
import zed.rainxch.details.domain.model.SaveInstalledAppParams
import zed.rainxch.details.domain.model.UpdateInstalledAppParams

interface InstallationManager {
    suspend fun validateApk(
        filePath: String,
        isUpdate: Boolean,
        trackedPackageName: String?,
    ): ApkValidationResult

    suspend fun checkSigningFingerprint(apkInfo: ApkPackageInfo): FingerprintCheckResult

    suspend fun saveNewInstalledApp(params: SaveInstalledAppParams): InstalledApp?

    suspend fun updateInstalledAppVersion(params: UpdateInstalledAppParams)
}
