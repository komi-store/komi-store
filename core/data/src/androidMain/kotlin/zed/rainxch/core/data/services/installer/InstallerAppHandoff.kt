package zed.rainxch.core.data.services.installer

// The Installer members that hand an APK or repo to another installer app, so AndroidInstaller can delegate them.
interface InstallerAppHandoff {
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
