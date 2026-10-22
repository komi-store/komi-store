package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.model.LinuxPackageType
import zed.rainxch.core.data.model.LinuxTerminal
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import zed.rainxch.core.domain.utils.AssetArchitectureMatcher
import zed.rainxch.core.domain.utils.AssetSelector
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import kotlin.collections.iterator
import kotlin.getValue

class DesktopInstaller(
    private val platform: Platform,
    private val installerInfoExtractor: InstallerInfoExtractor,
) : Installer {
    private val linuxPackageType: LinuxPackageType by lazy {
        determineLinuxPackageType()
    }

    private val systemArchitecture: SystemArchitecture by lazy {
        determineSystemArchitecture()
    }

    private val isRunningInFlatpak: Boolean by lazy {
        try {
            File("/.flatpak-info").exists() ||
                System.getenv("FLATPAK_ID") != null
        } catch (_: Exception) {
            false
        }
    }

    override fun getApkInfoExtractor(): InstallerInfoExtractor = installerInfoExtractor

    override fun detectSystemArchitecture(): SystemArchitecture = systemArchitecture

    override fun isObtainiumInstalled(): Boolean = false

    override fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit,
    ) {
    }

    override fun isAppManagerInstalled(): Boolean = false

    override fun openInAppManager(
        filePath: String,
        onOpenInstaller: () -> Unit,
    ) {
    }

    override fun openApp(packageName: String): Boolean {
        Logger.d { "Open app not supported on desktop for: $packageName" }
        return false
    }

    override fun openWithExternalInstaller(filePath: String) {
    }

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

        return isArchitectureCompatible(name, systemArchitecture)
    }

    override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? {
        if (assets.isEmpty()) return null

        val priority =
            when (platform) {
                Platform.ANDROID -> {
                    listOf(".apk")
                }

                Platform.WINDOWS -> {
                    listOf(".msi", ".exe")
                }

                Platform.MACOS -> {
                    listOf(".dmg", ".pkg")
                }

                Platform.LINUX -> {
                    if (isRunningInFlatpak) {
                        when (linuxPackageType) {
                            LinuxPackageType.DEB -> listOf(".deb", ".appimage", ".rpm", ".pkg.tar.zst")
                            LinuxPackageType.RPM -> listOf(".rpm", ".appimage", ".deb", ".pkg.tar.zst")
                            LinuxPackageType.ARCH -> listOf(".pkg.tar.zst", ".appimage", ".deb", ".rpm")
                            LinuxPackageType.UNIVERSAL -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
                        }
                    } else {
                        when (linuxPackageType) {
                            LinuxPackageType.DEB -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
                            LinuxPackageType.RPM -> listOf(".appimage", ".rpm", ".deb", ".pkg.tar.zst")
                            LinuxPackageType.ARCH -> listOf(".appimage", ".pkg.tar.zst", ".deb", ".rpm")
                            LinuxPackageType.UNIVERSAL -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
                        }
                    }
                }
            }

        val compatibleAssets =
            assets.filter { asset ->
                isArchitectureCompatible(asset.name.lowercase(), systemArchitecture)
            }

        val assetsToConsider = compatibleAssets.ifEmpty { assets }

        return AssetSelector.choose(
            assets = assetsToConsider,
            deviceArch = systemArchitecture,
            extensionPriority = priority,
        )
    }

    private fun determineSystemArchitecture(): SystemArchitecture {
        if (platform == Platform.MACOS) {
            try {
                val process = ProcessBuilder("uname", "-m").start()
                val output =
                    process.inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                process.waitFor()

                return when (output) {
                    "arm64" -> SystemArchitecture.AARCH64
                    "x86_64" -> SystemArchitecture.X86_64
                    else -> SystemArchitecture.fromString(System.getProperty("os.arch"))
                }
            } catch (_: Exception) {
            }
        }

        val osArch = System.getProperty("os.arch") ?: return SystemArchitecture.UNKNOWN
        return SystemArchitecture.fromString(osArch)
    }

    private fun determineLinuxPackageType(): LinuxPackageType {
        if (platform != Platform.LINUX) return LinuxPackageType.UNIVERSAL

        if (isRunningInFlatpak) {
            return try {
                detectHostLinuxPackageType()
            } catch (e: Exception) {
                Logger.w { "Failed to detect host Linux package type from Flatpak: ${e.message}" }
                LinuxPackageType.UNIVERSAL
            }
        }

        return try {
            val osRelease = tryReadOsRelease()
            if (osRelease != null) {
                val idLike = osRelease["ID_LIKE"]?.lowercase() ?: ""
                val id = osRelease["ID"]?.lowercase() ?: ""

                if (id in listOf("debian", "ubuntu", "linuxmint", "pop", "elementary") ||
                    idLike.contains("debian") || idLike.contains("ubuntu")
                ) {
                    Logger.d { "Detected Debian-based distribution: $id" }
                    return LinuxPackageType.DEB
                }

                if (id in
                    listOf(
                        "fedora",
                        "rhel",
                        "centos",
                        "rocky",
                        "almalinux",
                        "opensuse",
                        "suse",
                    ) ||
                    idLike.contains("fedora") || idLike.contains("rhel") ||
                    idLike.contains("suse") || idLike.contains("centos")
                ) {
                    Logger.d { "Detected RPM-based distribution: $id" }
                    return LinuxPackageType.RPM
                }

                if (id in
                    listOf(
                        "arch",
                        "manjaro",
                        "endeavouros",
                        "artix",
                        "cachyos",
                        "garuda",
                        "arcolinux",
                        "parabola",
                    ) ||
                    idLike.contains("arch")
                ) {
                    Logger.d { "Detected Arch-based distribution: $id" }
                    return LinuxPackageType.ARCH
                }
            }

            if (commandExists("apt") || commandExists("apt-get")) {
                Logger.d { "Detected package manager: apt" }
                return LinuxPackageType.DEB
            }

            if (commandExists("dnf")) {
                Logger.d { "Detected package manager: dnf" }
                return LinuxPackageType.RPM
            }

            if (commandExists("yum")) {
                Logger.d { "Detected package manager: yum" }
                return LinuxPackageType.RPM
            }

            if (commandExists("zypper")) {
                Logger.d { "Detected package manager: zypper" }
                return LinuxPackageType.RPM
            }

            if (commandExists("pacman")) {
                Logger.d { "Detected package manager: pacman" }
                return LinuxPackageType.ARCH
            }

            Logger.d { "Could not determine package type, defaulting to UNIVERSAL" }
            LinuxPackageType.UNIVERSAL
        } catch (e: Exception) {
            Logger.w { "Failed to detect Linux package type: ${e.message}" }
            LinuxPackageType.UNIVERSAL
        }
    }

    private fun detectHostLinuxPackageType(): LinuxPackageType {
        val hostOsRelease = File("/run/host/os-release")
        if (!hostOsRelease.exists()) {
            Logger.w { "Host os-release not available at /run/host/os-release" }
            return LinuxPackageType.UNIVERSAL
        }

        val osRelease = parseOsRelease(hostOsRelease.readText())
        val id = osRelease["ID"]?.lowercase() ?: ""
        val idLike = osRelease["ID_LIKE"]?.lowercase() ?: ""

        Logger.d { "Host distro detected from Flatpak: ID=$id, ID_LIKE=$idLike" }

        if (id in listOf("debian", "ubuntu", "linuxmint", "pop", "elementary") ||
            idLike.contains("debian") || idLike.contains("ubuntu")
        ) {
            Logger.d { "Host is Debian-based: $id" }
            return LinuxPackageType.DEB
        }

        if (id in listOf("fedora", "rhel", "centos", "rocky", "almalinux", "opensuse", "suse") ||
            idLike.contains("fedora") || idLike.contains("rhel") ||
            idLike.contains("suse") || idLike.contains("centos")
        ) {
            Logger.d { "Host is RPM-based: $id" }
            return LinuxPackageType.RPM
        }

        if (id in
            listOf(
                "arch",
                "manjaro",
                "endeavouros",
                "artix",
                "cachyos",
                "garuda",
                "arcolinux",
                "parabola",
            ) ||
            idLike.contains("arch")
        ) {
            Logger.d { "Host is Arch-based: $id" }
            return LinuxPackageType.ARCH
        }

        Logger.d { "Could not classify host distro, defaulting to UNIVERSAL" }
        return LinuxPackageType.UNIVERSAL
    }

    private fun tryReadOsRelease(): Map<String, String>? {
        val osReleaseFiles =
            listOf(
                "/etc/os-release",
                "/usr/lib/os-release",
            )

        for (filePath in osReleaseFiles) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val content = file.readText()
                    return parseOsRelease(content)
                }
            } catch (e: Exception) {
                Logger.w { "Could not read $filePath: ${e.message}" }
            }
        }
        return null
    }

    private fun parseOsRelease(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"")
                    result[key] = value
                }
            }
        }
        return result
    }

    private fun commandExists(command: String): Boolean =
        try {
            val process = ProcessBuilder("which", command).start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }

    private fun isArchitectureCompatible(
        assetName: String,
        systemArch: SystemArchitecture,
    ): Boolean {
        val name = assetName.lowercase()

        if (platform == Platform.MACOS) {
            if (name.contains("universal") || name.contains("darwin")) {
                return true
            }

            if ((name.endsWith(".dmg") || name.endsWith(".pkg")) &&
                !listOf("x86_64", "amd64", "arm64", "aarch64").any { name.contains(it) }
            ) {
                return true
            }
        }

        return AssetArchitectureMatcher.isCompatible(name, systemArch)
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

            if (isRunningInFlatpak) {
                Logger.d { "Running in Flatpak — skipping permission checks for .$ext" }
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

    override fun uninstall(packageName: String) {
        Logger.d { "Uninstall not supported on desktop for: $packageName" }
    }

    override suspend fun install(
        filePath: String,
        extOrMime: String,
    ): InstallOutcome =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalStateException("File not found: $filePath")
            }

            val ext = extOrMime.lowercase().removePrefix(".")

            if (isRunningInFlatpak) {
                installFromFlatpak(file, ext)
                return@withContext InstallOutcome.DELEGATED_TO_SYSTEM
            }

            when (platform) {
                Platform.WINDOWS -> installWindows(file, ext)
                Platform.MACOS -> installMacOS(file, ext)
                Platform.LINUX -> installLinux(file, ext)
                else -> throw UnsupportedOperationException("Installation not supported on $platform")
            }

            InstallOutcome.DELEGATED_TO_SYSTEM
        }

    private fun installFromFlatpak(
        file: File,
        ext: String,
    ) {
        Logger.i { "Running in Flatpak sandbox — delegating installation to host system" }
        Logger.i { "File: ${file.absolutePath} (.$ext)" }

        val nameLower = file.name.lowercase()
        if (nameLower.endsWith(".pkg.tar.zst")) {
            Logger.d { "Opening .pkg.tar.zst package via xdg-open portal for host installation" }
            try {
                val process = ProcessBuilder("xdg-open", file.absolutePath).start()
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    Logger.i { "Arch package opened on host system for installation" }
                    showFlatpakNotification(
                        title = "Package Ready to Install",
                        message =
                            "The Arch package has been opened in your system's " +
                                "package manager. Follow the prompts to complete installation.",
                    )
                } else {
                    Logger.w { "xdg-open exited with code $exitCode" }
                    showFlatpakNotification(
                        title = "Installation",
                        message = "Please open this .pkg.tar.zst file with your package manager.",
                    )
                    openInFileManager(file)
                }
            } catch (e: Exception) {
                Logger.w { "Failed to open .pkg.tar.zst via xdg-open: ${e.message}" }
                showFlatpakNotification(
                    title = "Download Complete",
                    message = "Please install manually: sudo pacman -U <path>",
                )
                openInFileManager(file)
            }
            return
        }

        when (ext) {
            "deb", "rpm" -> {
                Logger.d { "Opening .$ext package via xdg-open portal for host installation" }
                try {
                    val process = ProcessBuilder("xdg-open", file.absolutePath).start()
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        Logger.i { "Package opened on host system for installation" }
                        showFlatpakNotification(
                            title = "Package Ready to Install",
                            message =
                                "The ${ext.uppercase()} package has been opened in your system's " +
                                    "software installer. Follow the prompts to complete installation.",
                        )
                    } else {
                        Logger.w { "xdg-open exited with code $exitCode" }
                        showFlatpakNotification(
                            title = "Installation",
                            message = "Please open this file with your software center to install.",
                        )
                        openInFileManager(file)
                    }
                } catch (e: Exception) {
                    Logger.w { "Failed to open file via xdg-open: ${e.message}" }
                    showFlatpakNotification(
                        title = "Download Complete",
                        message = "Please install manually from your file manager.",
                    )
                    openInFileManager(file)
                }
            }

            "appimage" -> {
                Logger.d { "AppImage downloaded in Flatpak — preparing for host launch" }

                try {
                    file.setExecutable(true, false)
                    Logger.d { "Set executable permission on AppImage" }
                } catch (e: Exception) {
                    Logger.w { "Could not set executable permission: ${e.message}" }
                }

                showFlatpakNotification(
                    title = "AppImage Downloaded",
                    message = "Right-click → Properties → mark as executable, then double-click to run.",
                )

                openInFileManager(file)
            }

            else -> {
                showFlatpakNotification(
                    title = "Download Complete",
                    message = "File saved to your Downloads folder.",
                )
                openInFileManager(file)
            }
        }
    }

    private fun showFlatpakNotification(
        title: String,
        message: String,
    ) {
        try {
            ProcessBuilder(
                "notify-send",
                "--app-name=Komi Store",
                title,
                message,
                "-u",
                "normal",
                "-t",
                "15000",
            ).start()
        } catch (e: Exception) {
            Logger.w { "Could not show Flatpak notification: ${e.message}" }
            Logger.i { "[$title] $message" }
        }
    }

    private fun openInFileManager(file: File) {
        try {
            val fileUri = "file://${file.absolutePath}"
            val process =
                ProcessBuilder(
                    "gdbus",
                    "call",
                    "--session",
                    "--dest",
                    "org.freedesktop.FileManager1",
                    "--object-path",
                    "/org/freedesktop/FileManager1",
                    "--method",
                    "org.freedesktop.FileManager1.ShowItems",
                    "['$fileUri']",
                    "",
                ).start()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Logger.d { "Opened file manager via D-Bus ShowItems: ${file.absolutePath}" }
                return
            }
            Logger.w { "D-Bus ShowItems failed with exit code $exitCode" }
        } catch (e: Exception) {
            Logger.w { "D-Bus ShowItems not available: ${e.message}" }
        }

        try {
            val parentDir = file.parentFile ?: return
            ProcessBuilder("xdg-open", parentDir.absolutePath).start()
            Logger.d { "Opened parent directory: ${parentDir.absolutePath}" }
        } catch (e: Exception) {
            Logger.w { "Could not open file manager: ${e.message}" }
        }
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

                tryShowNotification(
                    title = "Installation Started",
                    message = "Please drag the application to your Applications folder",
                )
            }

            "pkg" -> {
                try {
                    val script =
                        """
                        do shell script "installer -pkg '${file.absolutePath}' -target /" with administrator privileges
                        """.trimIndent()

                    val pb = ProcessBuilder("osascript", "-e", script)
                    val process = pb.start()
                    val exitCode = process.waitFor()

                    if (exitCode != 0) {
                        throw IOException("Installation cancelled or failed")
                    }

                    Logger.d { "PKG installed successfully" }
                } catch (e: Exception) {
                    Logger.w { "Automated install failed, opening installer GUI: ${e.message}" }
                    ProcessBuilder("open", file.absolutePath).start()
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported macOS installer: .$ext")
            }
        }
    }

    private fun tryShowNotification(
        title: String,
        message: String,
    ) {
        if (platform == Platform.MACOS) {
            try {
                val script =
                    """
                    display notification "$message" with title "$title"
                    """.trimIndent()
                ProcessBuilder("osascript", "-e", script).start()
            } catch (e: Exception) {
                Logger.w { "Could not show macOS notification: ${e.message}" }
            }
        } else {
            try {
                ProcessBuilder(
                    "notify-send",
                    title,
                    message,
                    "-u",
                    "critical",
                    "-t",
                    "10000",
                ).start()
            } catch (e: Exception) {
                Logger.w { "Could not show notification: ${e.message}" }
            }
        }
    }

    private fun installLinux(
        file: File,
        ext: String,
    ) {
        val nameLower = file.name.lowercase()
        if (nameLower.endsWith(".pkg.tar.zst")) {
            installPacmanPackage(file)
            return
        }

        when (ext) {
            "appimage" -> {
                installAppImage(file)
            }

            "deb" -> {
                installDebPackage(file)
            }

            "rpm" -> {
                installRpmPackage(file)
            }

            else -> {
                throw IllegalArgumentException("Unsupported Linux installer: .$ext")
            }
        }
    }

    private fun installDebPackage(file: File) {
        Logger.d { "Installing DEB package: ${file.absolutePath}" }

        if (linuxPackageType == LinuxPackageType.RPM) {
            Logger.i { "Detected DEB package on RPM system. Initiating conversion flow." }
            openTerminalForAlienConversion(file.absolutePath)
            return
        }

        val installMethods =
            listOf(
                listOf("pkexec", "apt", "install", "-y", file.absolutePath),
                listOf(
                    "pkexec",
                    "sh",
                    "-c",
                    "dpkg -i '${file.absolutePath}' || apt-get install -f -y",
                ),
                listOf("gdebi-gtk", file.absolutePath),
                null,
            )

        for (method in installMethods) {
            if (method == null) {
                openTerminalForDebInstall(file.absolutePath)
                return
            }

            try {
                Logger.d { "Trying installation method: ${method.joinToString(" ")}" }
                val process = ProcessBuilder(method).start()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    Logger.d { "DEB package installed successfully" }
                    tryShowNotification("Installation Complete", "Package installed successfully")
                    return
                } else {
                    Logger.w { "Installation method failed with exit code: $exitCode" }
                }
            } catch (e: IOException) {
                Logger.w { "Installation method not available: ${e.message}" }
            }
        }

        throw IOException("Could not install DEB package. Please install it manually.")
    }

    private fun openTerminalForAlienConversion(filePath: String) {
        Logger.d { "Opening terminal for Alien conversion and installation" }

        val availableTerminals = detectAvailableTerminals()

        if (availableTerminals.isEmpty()) {
            Logger.e { "No terminal emulator found for conversion" }
            tryShowNotification(
                "Conversion Required",
                "Please install 'alien', convert '$filePath' to RPM, and install manually.",
            )
            throw IOException("No terminal found to run Alien conversion.")
        }

        val command =
            buildString {
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'DEB Package on RPM System Detected'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo ''; ")
                append("echo 'This package will be converted to RPM format.'; ")
                append("echo 'This requires the \"alien\" tool.'; ")
                append("echo ''; ")

                append("if ! command -v alien &> /dev/null; then ")
                append("echo 'Installing alien and rpm-build...'; ")
                append("sudo dnf install -y alien rpm-build 2>/dev/null || ")
                append("sudo yum install -y alien rpm-build 2>/dev/null || ")
                append("sudo zypper install -y alien rpm-build 2>/dev/null; ")
                append("fi; ")

                append("if ! command -v alien &> /dev/null; then ")
                append("echo ''; ")
                append("echo 'ERROR: Failed to install alien.'; ")
                append("echo 'Please install it manually: sudo dnf install alien rpm-build'; ")
                append("echo ''; ")
                append("echo 'Press Enter to close...'; read; exit 1; ")
                append("fi; ")

                append("echo ''; ")
                append("echo 'Converting to RPM (this may take a minute)...'; ")
                append("TMPDIR=/tmp/alien_install_$; ")
                append($$"mkdir -p \"$TMPDIR\" && cd \"$TMPDIR\" || exit 1; ")
                append("cp '$filePath' ./package.deb; ")
                append("sudo alien -r -c package.deb; ")

                append("if [ ! -f *.rpm ]; then ")
                append("echo ''; ")
                append("echo 'ERROR: Conversion failed.'; ")
                append($$"cd .. && rm -rf \"$TMPDIR\"; ")
                append("echo 'Press Enter to close...'; read; exit 1; ")
                append("fi; ")

                append("echo ''; ")
                append("echo 'Installing converted RPM...'; ")
                append("INSTALL_SUCCESS=0; ")

                append("if sudo dnf install -y ./*.rpm 2>&1; then INSTALL_SUCCESS=1; ")
                append("elif sudo yum install -y ./*.rpm 2>&1; then INSTALL_SUCCESS=1; ")
                append("elif sudo zypper install -y --allow-unsigned-rpm ./*.rpm 2>&1; then INSTALL_SUCCESS=1; ")
                append("elif sudo rpm -ivh --nodeps --force ./*.rpm 2>&1; then INSTALL_SUCCESS=1; ")
                append("fi; ")

                append("echo ''; ")
                append($$"if [ $INSTALL_SUCCESS -eq 1 ]; then ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installation Complete!'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("else ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installation Failed!'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo ''; ")
                append("echo 'The RPM was created but installation failed.'; ")
                append("echo 'This usually happens due to file conflicts.'; ")
                append("echo ''; ")
                append("echo 'The converted RPM is located at:'; ")
                append($$"echo \"$TMPDIR/\"*.rpm; ")
                append("echo ''; ")
                append("echo 'You can try installing it manually with:'; ")
                append($$"echo \"sudo rpm -ivh --force $TMPDIR/\"*.rpm; ")
                append("echo ''; ")
                append("echo 'Or open the file with your software manager.'; ")
                append("fi; ")

                append($$"cd .. && rm -rf \"$TMPDIR\"; ")
                append("echo ''; ")
                append("echo 'Press Enter to close...'; read")
            }

        runCommandInTerminal(command, availableTerminals)
    }

    private fun installRpmPackage(file: File) {
        Logger.d { "Installing RPM package: ${file.absolutePath}" }

        val installMethods =
            listOf(
                listOf("pkexec", "dnf", "install", "-y", "--nogpgcheck", file.absolutePath),
                listOf("pkexec", "yum", "install", "-y", "--nogpgcheck", file.absolutePath),
                listOf("pkexec", "zypper", "install", "-y", "--no-gpg-checks", file.absolutePath),
                listOf("pkexec", "rpm", "-ivh", "--nosignature", file.absolutePath),
                null,
            )

        for (method in installMethods) {
            if (method == null) {
                openTerminalForRpmInstall(file.absolutePath)
                return
            }

            try {
                Logger.d { "Trying installation method: ${method.joinToString(" ")}" }
                val process = ProcessBuilder(method).start()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    Logger.d { "RPM package installed successfully" }
                    tryShowNotification("Installation Complete", "Package installed successfully")
                    return
                } else {
                    Logger.w { "Installation method failed with exit code: $exitCode" }
                }
            } catch (e: IOException) {
                Logger.w { "Installation method not available: ${e.message}" }
            }
        }

        throw IOException("Could not install RPM package. Please install it manually.")
    }

    private fun shellQuoteSingleQuotes(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun installPacmanPackage(file: File) {
        Logger.d { "Installing pacman package: ${file.absolutePath}" }

        if (linuxPackageType != LinuxPackageType.ARCH) {
            Logger.w { "Pacman package (.pkg.tar.zst) on non-Arch system (type=$linuxPackageType)." }
            openTerminalForPacmanIncompatible(file.absolutePath)
            return
        }

        val installMethods =
            listOf(
                listOf("pkexec", "pacman", "-U", "--noconfirm", file.absolutePath),
                null,
            )

        for (method in installMethods) {
            if (method == null) {
                openTerminalForPacmanInstall(file.absolutePath)
                return
            }

            try {
                Logger.d { "Trying installation method: ${method.joinToString(" ")}" }
                val process = ProcessBuilder(method).start()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    Logger.d { "Pacman package installed successfully" }
                    tryShowNotification("Installation Complete", "Package installed successfully")
                    return
                } else {
                    Logger.w { "Installation method failed with exit code: $exitCode" }
                }
            } catch (e: IOException) {
                Logger.w { "Installation method not available: ${e.message}" }
            }
        }

        throw IOException("Could not install pacman package. Please install it manually.")
    }

    private fun openTerminalForPacmanInstall(filePath: String) {
        Logger.d { "Opening terminal for pacman -U install" }

        val availableTerminals = detectAvailableTerminals()
        val quoted = shellQuoteSingleQuotes(filePath)

        if (availableTerminals.isEmpty()) {
            tryShowNotification(
                "Install via Terminal",
                "Run: sudo pacman -U $quoted",
            )
            throw IOException("No terminal emulator found to run pacman install.")
        }

        val command =
            buildString {
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installing Arch Package'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo ''; ")
                append("echo 'You will be prompted for your sudo password.'; ")
                append("echo ''; ")
                append("sudo pacman -U $quoted; ")
                append("EXIT=\$?; ")
                append("echo ''; ")
                append("if [ \$EXIT -eq 0 ]; then ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installation Complete!'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("else ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installation Failed (exit \$EXIT)'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("fi; ")
                append("echo ''; ")
                append("echo 'Press Enter to close...'; read")
            }

        runCommandInTerminal(command, availableTerminals)
    }

    private fun openTerminalForPacmanIncompatible(filePath: String) {
        Logger.d { "Opening terminal to inform user .pkg.tar.zst is not installable on their distro" }

        val availableTerminals = detectAvailableTerminals()

        if (availableTerminals.isEmpty()) {
            tryShowNotification(
                "Wrong Package Type",
                "This is an Arch Linux package (.pkg.tar.zst) — not supported on your distribution. Look for a .deb, .rpm, or .AppImage in the release.",
            )
            throw IOException(
                "Arch packages (.pkg.tar.zst) can only be installed on Arch-based distributions.",
            )
        }

        val quoted = shellQuoteSingleQuotes(filePath)
        val command =
            buildString {
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Wrong Package Format'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo ''; ")
                append("echo 'This file is an Arch Linux package (.pkg.tar.zst):'; ")

                append("printf '  %s\\n' $quoted; ")
                append("echo ''; ")
                append("echo 'Your system uses a different package format.'; ")
                append("echo 'Please download the .deb, .rpm, or .AppImage variant'; ")
                append("echo 'instead from the release page.'; ")
                append("echo ''; ")
                append("echo 'Press Enter to close...'; read")
            }

        runCommandInTerminal(command, availableTerminals)
        throw IOException(
            "Arch packages (.pkg.tar.zst) can only be installed on Arch-based distributions.",
        )
    }

    private fun openTerminalForDebInstall(filePath: String) {
        val command =
            buildString {
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installing DEB Package'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo ''; ")
                append("sudo dpkg -i '$filePath' && sudo apt-get install -f -y; ")
                append("echo ''; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installation Complete!'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo ''; ")
                append("echo 'Press Enter to close...'; read")
            }

        val availableTerminals = detectAvailableTerminals()
        if (availableTerminals.isEmpty()) {
            tryShowNotification(
                "Installation Required",
                "Please install manually using your file manager",
            )
            tryCopyToClipboard("sudo dpkg -i '$filePath' && sudo apt-get install -f -y")
            throw IOException("No terminal emulator found.")
        }

        runCommandInTerminal(command, availableTerminals)
    }

    private fun openTerminalForRpmInstall(filePath: String) {
        val command =
            buildString {
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installing RPM Package'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo ''; ")
                append("sudo dnf install -y --nogpgcheck '$filePath' 2>/dev/null || ")
                append("sudo yum install -y --nogpgcheck '$filePath' 2>/dev/null || ")
                append("sudo zypper install -y --no-gpg-checks '$filePath' 2>/dev/null || ")
                append("sudo rpm -ivh --nosignature '$filePath'; ")
                append("echo ''; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo 'Installation Complete!'; ")
                append("echo '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'; ")
                append("echo ''; ")
                append("echo 'Press Enter to close...'; read")
            }

        val availableTerminals = detectAvailableTerminals()
        if (availableTerminals.isEmpty()) {
            tryShowNotification(
                "Installation Required",
                "Please install manually using your file manager",
            )
            tryCopyToClipboard("sudo dnf install -y --nogpgcheck '$filePath'")
            throw IOException("No terminal emulator found.")
        }

        runCommandInTerminal(command, availableTerminals)
    }

    private fun runCommandInTerminal(
        command: String,
        terminals: List<LinuxTerminal>,
    ) {
        for (terminal in terminals) {
            try {
                Logger.d { "Trying terminal: ${terminal.name}" }
                val processBuilder =
                    when (terminal) {
                        LinuxTerminal.GNOME_TERMINAL -> {
                            ProcessBuilder(
                                "gnome-terminal",
                                "--",
                                "bash",
                                "-c",
                                command,
                            )
                        }

                        LinuxTerminal.KONSOLE -> {
                            ProcessBuilder(
                                "konsole",
                                "-e",
                                "bash",
                                "-c",
                                command,
                            )
                        }

                        LinuxTerminal.XTERM -> {
                            ProcessBuilder(
                                "xterm",
                                "-e",
                                "bash",
                                "-c",
                                command,
                            )
                        }

                        LinuxTerminal.XFCE4_TERMINAL -> {
                            ProcessBuilder(
                                "xfce4-terminal",
                                "-e",
                                "bash -c \"$command\"",
                            )
                        }

                        LinuxTerminal.ALACRITTY -> {
                            ProcessBuilder(
                                "alacritty",
                                "-e",
                                "bash",
                                "-c",
                                command,
                            )
                        }

                        LinuxTerminal.KITTY -> {
                            ProcessBuilder(
                                "kitty",
                                "bash",
                                "-c",
                                command,
                            )
                        }

                        LinuxTerminal.TILIX -> {
                            ProcessBuilder(
                                "tilix",
                                "-e",
                                "bash -c \"$command\"",
                            )
                        }

                        LinuxTerminal.MATE_TERMINAL -> {
                            ProcessBuilder(
                                "mate-terminal",
                                "-e",
                                "bash -c \"$command\"",
                            )
                        }
                    }

                processBuilder.start()
                Logger.d { "Terminal opened successfully: ${terminal.name}" }
                return
            } catch (e: IOException) {
                Logger.w { "Failed to open ${terminal.name}: ${e.message}" }
            }
        }
        throw IOException("Could not open any terminal emulator")
    }

    private fun detectAvailableTerminals(): List<LinuxTerminal> {
        val availableTerminals = mutableListOf<LinuxTerminal>()

        val terminalCommands =
            mapOf(
                LinuxTerminal.GNOME_TERMINAL to "gnome-terminal",
                LinuxTerminal.KONSOLE to "konsole",
                LinuxTerminal.XFCE4_TERMINAL to "xfce4-terminal",
                LinuxTerminal.ALACRITTY to "alacritty",
                LinuxTerminal.KITTY to "kitty",
                LinuxTerminal.TILIX to "tilix",
                LinuxTerminal.MATE_TERMINAL to "mate-terminal",
                LinuxTerminal.XTERM to "xterm",
            )

        for ((terminal, command) in terminalCommands) {
            if (commandExists(command)) {
                availableTerminals.add(terminal)
                Logger.d { "Found terminal: $command" }
            }
        }

        return availableTerminals
    }

    private fun tryCopyToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            Logger.d { "Command copied to clipboard" }
        } catch (e: Exception) {
            Logger.w { "Could not copy to clipboard: ${e.message}" }
        }
    }

    private fun installAppImage(file: File) {
        Logger.d { "Installing AppImage: ${file.absolutePath}" }

        try {
            Logger.d { "Moving AppImage to ~/Applications..." }
            val installedFile = moveToApplicationsDirectory(file)
            Logger.d { "Moved to: ${installedFile.absolutePath}" }

            Logger.d { "Setting executable permissions..." }
            val executableSet = installedFile.setExecutable(true, false)
            Logger.d { "Set executable via Java: $executableSet" }

            if (!executableSet) {
                Logger.w { "Failed to set executable via Java, trying chmod..." }
                val chmodProcess = ProcessBuilder("chmod", "+x", installedFile.absolutePath).start()
                val chmodExitCode = chmodProcess.waitFor()
                Logger.d { "chmod exit code: $chmodExitCode" }
            }

            if (!installedFile.canExecute()) {
                throw IllegalStateException("Failed to make AppImage executable")
            }

            Logger.d { "AppImage is now executable" }

            Logger.d { "Launching AppImage..." }
            val process =
                ProcessBuilder(installedFile.absolutePath)
                    .inheritIO()
                    .start()

            Logger.d { "AppImage launched successfully (PID: ${process.pid()})" }

            showInstallationNotification(installedFile)

            Logger.d { "AppImage installation completed successfully" }
        } catch (e: IOException) {
            Logger.e { "Failed to install AppImage: ${e.message}" }
            e.printStackTrace()
            throw IllegalStateException(
                "Failed to install AppImage: ${e.message}. " +
                    "Please ensure you have write permissions to ~/Applications folder.",
                e,
            )
        } catch (e: SecurityException) {
            Logger.e { "Security exception: ${e.message}" }
            e.printStackTrace()
            throw IllegalStateException(
                "Security restrictions prevent installing AppImage.",
                e,
            )
        } catch (e: Exception) {
            Logger.e { "Unexpected error: ${e.message}" }
            e.printStackTrace()
            throw IllegalStateException("Failed to install AppImage: ${e.message}", e)
        }
    }

    private fun moveToApplicationsDirectory(file: File): File {
        val homeDir = System.getProperty("user.home")
        val applicationsDir = File(homeDir, "Applications")

        if (!applicationsDir.exists()) {
            Logger.d { "Creating ~/Applications directory..." }
            val created = applicationsDir.mkdirs()
            Logger.d { "Directory created: $created" }
        }

        if (file.parent == applicationsDir.absolutePath) {
            Logger.d { "AppImage already in ~/Applications, no move needed" }
            return file
        }

        val destinationFile = File(applicationsDir, file.name)
        val finalDestination =
            if (destinationFile.exists()) {
                Logger.d { "File already exists in ~/Applications, generating unique name" }
                generateUniqueFileName(applicationsDir, file.name)
            } else {
                destinationFile
            }

        Logger.d { "Moving from: ${file.absolutePath}" }
        Logger.d { "Moving to: ${finalDestination.absolutePath}" }

        file.copyTo(finalDestination, overwrite = false)
        Logger.d { "Copy successful, file size: ${finalDestination.length()} bytes" }

        val deleted = file.delete()
        Logger.d { "Original file deleted: $deleted" }

        if (!finalDestination.exists()) {
            throw IllegalStateException("File was moved but doesn't exist at destination")
        }

        return finalDestination
    }

    private fun showInstallationNotification(file: File) {
        try {
            val message = "AppImage installed and launched from ~/Applications"

            Logger.i { message }
            Logger.i { "Location: ${file.absolutePath}" }

            ProcessBuilder(
                "notify-send",
                "-i",
                "application-x-executable",
                "AppImage Installed",
                "Installed to ~/Applications\n\nYou can find it at:\n${file.name}",
            ).start()
        } catch (e: Exception) {
            Logger.d { "Could not show notification: ${e.message}" }
        }
    }

    private fun generateUniqueFileName(
        directory: File,
        originalName: String,
    ): File {
        val nameWithoutExtension = originalName.substringBeforeLast(".")
        val extension = originalName.substringAfterLast(".", "")

        var counter = 1
        var candidateFile: File

        do {
            val newName =
                if (extension.isNotEmpty()) {
                    "${nameWithoutExtension}_$counter.$extension"
                } else {
                    "${nameWithoutExtension}_$counter"
                }
            candidateFile = File(directory, newName)
            counter++
        } while (candidateFile.exists() && counter < 1000)

        if (candidateFile.exists()) {
            throw IllegalStateException("Could not generate unique filename")
        }

        return candidateFile
    }
}
