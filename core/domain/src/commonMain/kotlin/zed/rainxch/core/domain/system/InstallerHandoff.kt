package zed.rainxch.core.domain.system

interface InstallerHandoff {
    fun isObtainiumInstalled(): Boolean

    fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit,
    )

    fun isAppManagerInstalled(): Boolean

    fun openInAppManager(
        filePath: String,
        onOpenInstaller: () -> Unit,
    )

    fun openWithExternalInstaller(filePath: String)
}
