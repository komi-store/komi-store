package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.model.LinuxPackageType
import java.io.File
import java.io.IOException

internal class LinuxPackageInstaller(
    private val host: DesktopHost,
    private val notifier: DesktopInstallNotifier,
) {
    private val terminals = LinuxTerminals(host)
    private val appImageInstaller = AppImageInstaller()

    fun install(
        file: File,
        ext: String,
    ) {
        if (file.name.lowercase().endsWith(".pkg.tar.zst")) {
            installPacmanPackage(file)
            return
        }

        when (ext) {
            "appimage" -> appImageInstaller.install(file)
            "deb" -> installDebPackage(file)
            "rpm" -> installRpmPackage(file)
            else -> throw IllegalArgumentException("Unsupported Linux installer: .$ext")
        }
    }

    private fun installDebPackage(file: File) {
        Logger.d { "Installing DEB package: ${file.absolutePath}" }

        if (host.linuxPackageType == LinuxPackageType.RPM) {
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
            )
        if (!installedByAny(installMethods, packageLabel = "DEB")) {
            val filePath = file.absolutePath
            runInTerminal(LinuxTerminalScripts.debInstall(filePath), noTerminalError = "No terminal emulator found.") {
                notifier.show("Installation Required", "Please install manually using your file manager")
                notifier.copyToClipboard("sudo dpkg -i '$filePath' && sudo apt-get install -f -y")
            }
        }
    }

    private fun installRpmPackage(file: File) {
        Logger.d { "Installing RPM package: ${file.absolutePath}" }

        val installMethods =
            listOf(
                listOf("pkexec", "dnf", "install", "-y", "--nogpgcheck", file.absolutePath),
                listOf("pkexec", "yum", "install", "-y", "--nogpgcheck", file.absolutePath),
                listOf("pkexec", "zypper", "install", "-y", "--no-gpg-checks", file.absolutePath),
                listOf("pkexec", "rpm", "-ivh", "--nosignature", file.absolutePath),
            )
        if (!installedByAny(installMethods, packageLabel = "RPM")) {
            val filePath = file.absolutePath
            runInTerminal(LinuxTerminalScripts.rpmInstall(filePath), noTerminalError = "No terminal emulator found.") {
                notifier.show("Installation Required", "Please install manually using your file manager")
                notifier.copyToClipboard("sudo dnf install -y --nogpgcheck '$filePath'")
            }
        }
    }

    private fun installPacmanPackage(file: File) {
        Logger.d { "Installing pacman package: ${file.absolutePath}" }

        if (host.linuxPackageType != LinuxPackageType.ARCH) {
            Logger.w { "Pacman package (.pkg.tar.zst) on non-Arch system (type=${host.linuxPackageType})." }
            openTerminalForPacmanIncompatible(file.absolutePath)
            return
        }

        val installMethods = listOf(listOf("pkexec", "pacman", "-U", "--noconfirm", file.absolutePath))
        if (!installedByAny(installMethods, packageLabel = "Pacman")) {
            Logger.d { "Opening terminal for pacman -U install" }
            val quoted = LinuxTerminalScripts.shellQuote(file.absolutePath)
            runInTerminal(
                LinuxTerminalScripts.pacmanInstall(quoted),
                noTerminalError = "No terminal emulator found to run pacman install.",
            ) {
                notifier.show("Install via Terminal", "Run: sudo pacman -U $quoted")
            }
        }
    }

    private fun installedByAny(
        installMethods: List<List<String>>,
        packageLabel: String,
    ): Boolean {
        val installed = installMethods.any { runInstallMethod(it) }
        if (installed) {
            Logger.d { "$packageLabel package installed successfully" }
            notifier.show("Installation Complete", "Package installed successfully")
        }
        return installed
    }

    private fun runInstallMethod(method: List<String>): Boolean {
        Logger.d { "Trying installation method: ${method.joinToString(" ")}" }
        val exitCode =
            processExitCode(method)
                .onFailure { Logger.w { "Installation method not available: ${it.message}" } }
                .getOrNull()
        if (exitCode != null && exitCode != 0) {
            Logger.w { "Installation method failed with exit code: $exitCode" }
        }
        return exitCode == 0
    }

    private fun openTerminalForAlienConversion(filePath: String) {
        Logger.d { "Opening terminal for Alien conversion and installation" }
        runInTerminal(
            LinuxTerminalScripts.alienConversion(filePath),
            noTerminalError = "No terminal found to run Alien conversion.",
        ) {
            Logger.e { "No terminal emulator found for conversion" }
            notifier.show(
                "Conversion Required",
                "Please install 'alien', convert '$filePath' to RPM, and install manually.",
            )
        }
    }

    private fun openTerminalForPacmanIncompatible(filePath: String) {
        Logger.d { "Opening terminal to inform user .pkg.tar.zst is not installable on their distro" }

        val availableTerminals = terminals.available()
        if (availableTerminals.isEmpty()) {
            notifier.show(
                "Wrong Package Type",
                "This is an Arch Linux package (.pkg.tar.zst), not supported on your distribution. " +
                    "Look for a .deb, .rpm, or .AppImage in the release.",
            )
        } else {
            val quoted = LinuxTerminalScripts.shellQuote(filePath)
            terminals.run(LinuxTerminalScripts.pacmanIncompatible(quoted), availableTerminals)
        }
        throw IOException(
            "Arch packages (.pkg.tar.zst) can only be installed on Arch-based distributions.",
        )
    }

    private inline fun runInTerminal(
        script: String,
        noTerminalError: String,
        onNoTerminal: () -> Unit,
    ) {
        val availableTerminals = terminals.available()
        if (availableTerminals.isEmpty()) {
            onNoTerminal()
            throw IOException(noTerminalError)
        }
        terminals.run(script, availableTerminals)
    }
}
