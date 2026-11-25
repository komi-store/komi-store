package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.system.Installer

// The Installer calls only Android can act on: a desktop has no other app to hand a package, a launch or an uninstall to.
abstract class UnsupportedInstallerActions : Installer {
    override fun isObtainiumInstalled(): Boolean = false

    override fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit,
    ) = Unit

    override fun isAppManagerInstalled(): Boolean = false

    override fun openInAppManager(
        filePath: String,
        onOpenInstaller: () -> Unit,
    ) = Unit

    override fun openWithExternalInstaller(filePath: String) = Unit

    override fun openApp(packageName: String): Boolean {
        Logger.d { "Open app not supported on desktop for: $packageName" }
        return false
    }

    override fun uninstall(packageName: String) {
        Logger.d { "Uninstall not supported on desktop for: $packageName" }
    }
}
