package zed.rainxch.core.domain.system

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.system.SystemArchitecture

interface Installer : InstallerHandoff {
    suspend fun isSupported(extOrMime: String): Boolean

    suspend fun ensurePermissionsOrThrow(extOrMime: String)

    suspend fun install(
        filePath: String,
        extOrMime: String,
    ): InstallOutcome

    fun uninstall(packageName: String)

    fun isAssetInstallable(assetName: String): Boolean

    fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset?

    fun detectSystemArchitecture(): SystemArchitecture

    fun getApkInfoExtractor(): InstallerInfoExtractor

    fun openApp(packageName: String): Boolean
}
