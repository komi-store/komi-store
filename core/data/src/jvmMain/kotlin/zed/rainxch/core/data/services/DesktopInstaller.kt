package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.model.LinuxPackageType
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import zed.rainxch.core.domain.utils.AssetArchitectureMatcher
import zed.rainxch.core.domain.utils.AssetSelector
import java.awt.Desktop
import java.io.File
import java.io.IOException

class DesktopInstaller(
    private val platform: Platform,
    private val installerInfoExtractor: InstallerInfoExtractor,
) : UnsupportedInstallerActions() {
    private val host = DesktopHost(platform)
    private val notifier = DesktopInstallNotifier(platform)
    private val flatpakHandoff = FlatpakInstallHandoff()
    private val linuxInstaller = LinuxPackageInstaller(host, notifier)

    override fun getApkInfoExtractor(): InstallerInfoExtractor = installerInfoExtractor

    override fun detectSystemArchitecture(): SystemArchitecture = host.systemArchitecture

    override fun isAssetInstallable(assetName: String): Boolean {
        val name = assetName.lowercase()

        val hasValidExtension =
            when (platform) {
                Platform.ANDROID -> {
                    name.endsWith(".apk")
                }

                Platform.WINDOWS -> {
                    name.endsWith(".msi") || name.endsWith(".exe")
                }

                Platform.MACOS -> {
                    name.endsWith(".dmg") || name.endsWith(".pkg")
                }

                Platform.LINUX -> {
                    name.endsWith(".appimage") ||
                        name.endsWith(".deb") ||
                        name.endsWith(".rpm") ||
                        name.endsWith(".pkg.tar.zst")
                }
            }

        if (!hasValidExtension) return false

        return isArchitectureCompatible(name, host.systemArchitecture)
    }

    override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? {
        if (assets.isEmpty()) return null

        val priority = extensionPriority()

        val compatibleAssets =
            assets.filter { asset ->
                isArchitectureCompatible(asset.name.lowercase(), host.systemArchitecture)
            }

        val assetsToConsider = compatibleAssets.ifEmpty { assets }

        return AssetSelector.choose(
            assets = assetsToConsider,
            deviceArch = host.systemArchitecture,
            extensionPriority = priority,
        )
    }

    private fun extensionPriority(): List<String> =
        when (platform) {
            Platform.ANDROID -> listOf(".apk")
            Platform.WINDOWS -> listOf(".msi", ".exe")
            Platform.MACOS -> listOf(".dmg", ".pkg")
            Platform.LINUX -> linuxExtensionPriority()
        }

    private fun linuxExtensionPriority(): List<String> =
        if (host.isRunningInFlatpak) {
            when (host.linuxPackageType) {
                LinuxPackageType.DEB -> listOf(".deb", ".appimage", ".rpm", ".pkg.tar.zst")
                LinuxPackageType.RPM -> listOf(".rpm", ".appimage", ".deb", ".pkg.tar.zst")
                LinuxPackageType.ARCH -> listOf(".pkg.tar.zst", ".appimage", ".deb", ".rpm")
                LinuxPackageType.UNIVERSAL -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
            }
        } else {
            when (host.linuxPackageType) {
                LinuxPackageType.DEB -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
                LinuxPackageType.RPM -> listOf(".appimage", ".rpm", ".deb", ".pkg.tar.zst")
                LinuxPackageType.ARCH -> listOf(".appimage", ".pkg.tar.zst", ".deb", ".rpm")
                LinuxPackageType.UNIVERSAL -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
            }
        }

    private fun isArchitectureCompatible(
        assetName: String,
        systemArch: SystemArchitecture,
    ): Boolean {
        val name = assetName.lowercase()
        if (platform == Platform.MACOS && isArchitectureFreeMacInstaller(name)) return true
        return AssetArchitectureMatcher.isCompatible(name, systemArch)
    }

    private fun isArchitectureFreeMacInstaller(name: String): Boolean {
        if (name.contains("universal") || name.contains("darwin")) return true
        val isInstaller = name.endsWith(".dmg") || name.endsWith(".pkg")
        return isInstaller && MAC_ARCH_MARKERS.none { name.contains(it) }
    }

    override suspend fun isSupported(extOrMime: String): Boolean {
        val ext = extOrMime.lowercase().removePrefix(".")
        return when (platform) {
            Platform.WINDOWS -> ext in listOf("msi", "exe")
            Platform.MACOS -> ext in listOf("dmg", "pkg")
            Platform.LINUX -> ext in listOf("appimage", "deb", "rpm", "pkg.tar.zst", "zst")
            else -> false
        }
    }

    override suspend fun ensurePermissionsOrThrow(extOrMime: String) =
        withContext(Dispatchers.IO) {
            val ext = extOrMime.lowercase().removePrefix(".")

            if (host.isRunningInFlatpak) {
                Logger.d { "Running in Flatpak, skipping permission checks for .$ext" }
                return@withContext
            }

            if (platform == Platform.LINUX && ext == "appimage") {
                try {
                    val tempFile = File.createTempFile("appimage_perm_test", ".tmp")
                    try {
                        val canSetExecutable = tempFile.setExecutable(true)
                        if (!canSetExecutable) {
                            throw IllegalStateException(
                                "Unable to set executable permissions. AppImage installation requires " +
                                    "the ability to make files executable.",
                            )
                        }
                    } finally {
                        tempFile.delete()
                    }
                } catch (e: IOException) {
                    throw IllegalStateException(
                        "Failed to verify permission capabilities for AppImage installation: ${e.message}",
                        e,
                    )
                } catch (e: SecurityException) {
                    throw IllegalStateException(
                        "Security restrictions prevent setting executable permissions for AppImage files.",
                        e,
                    )
                }
            }
        }

    override suspend fun install(
        filePath: String,
        extOrMime: String,
    ): InstallOutcome =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            check(file.exists()) { "File not found: $filePath" }

            val ext = extOrMime.lowercase().removePrefix(".")

            if (host.isRunningInFlatpak) {
                flatpakHandoff.install(file, ext)
                return@withContext InstallOutcome.DELEGATED_TO_SYSTEM
            }

            when (platform) {
                Platform.WINDOWS -> installWindows(file, ext)
                Platform.MACOS -> installMacOS(file, ext)
                Platform.LINUX -> linuxInstaller.install(file, ext)
                else -> throw UnsupportedOperationException("Installation not supported on $platform")
            }

            InstallOutcome.DELEGATED_TO_SYSTEM
        }

    private fun installWindows(
        file: File,
        ext: String,
    ) {
        when (ext) {
            "msi" -> {
                val pb = ProcessBuilder("msiexec", "/i", file.absolutePath)
                pb.start()
            }

            "exe" -> {
                try {
                    ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
                } catch (e: IOException) {
                    Logger.w { "Failed to launch installer via cmd start: ${e.message}, falling back to Desktop.open" }
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file)
                    } else {
                        ProcessBuilder(file.absolutePath).start()
                    }
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported Windows installer: .$ext")
            }
        }
    }

    private fun installMacOS(
        file: File,
        ext: String,
    ) {
        when (ext) {
            "dmg" -> {
                val pb = ProcessBuilder("open", file.absolutePath)
                pb.start()

                notifier.show(
                    title = "Installation Started",
                    message = "Please drag the application to your Applications folder",
                )
            }

            "pkg" -> {
                installMacPackage(file)
            }

            else -> {
                throw IllegalArgumentException("Unsupported macOS installer: .$ext")
            }
        }
    }

    private fun installMacPackage(file: File) {
        val script =
            """
            do shell script "installer -pkg '${file.absolutePath}' -target /" with administrator privileges
            """.trimIndent()
        val exitCode = processExitCode(listOf("osascript", "-e", script))
        if (exitCode.getOrNull() == 0) {
            Logger.d { "PKG installed successfully" }
            return
        }
        val reason = exitCode.exceptionOrNull()?.message ?: "Installation cancelled or failed"
        Logger.w { "Automated install failed, opening installer GUI: $reason" }
        ProcessBuilder("open", file.absolutePath).start()
    }

    private companion object {
        val MAC_ARCH_MARKERS = listOf("x86_64", "amd64", "arm64", "aarch64")
    }
}
