package zed.rainxch.core.data.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.helpers.AppLauncher
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.system.Platform
import java.io.File
import java.io.IOException

class DesktopAppLauncher(
    private val logger: KomiStoreLogger,
    private val platform: Platform,
) : AppLauncher {
    override suspend fun launchApp(installedApp: InstalledApp): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                when (platform) {
                    Platform.WINDOWS -> launchWindowsApp(installedApp)
                    Platform.MACOS -> launchMacOSApp(installedApp)
                    Platform.LINUX -> launchLinuxApp(installedApp)
                    else -> throw UnsupportedOperationException("Unsupported platform: $platform")
                }
                Result.success(Unit)
            } catch (e: IOException) {
                launchFailure(installedApp, e)
            } catch (e: IllegalStateException) {
                launchFailure(installedApp, e)
            } catch (e: UnsupportedOperationException) {
                launchFailure(installedApp, e)
            }
        }

    private fun launchFailure(
        installedApp: InstalledApp,
        error: Exception,
    ): Result<Unit> {
        logger.error("Failed to launch app ${installedApp.appName}", error)
        return Result.failure(error)
    }

    override suspend fun canLaunchApp(installedApp: InstalledApp): Boolean =
        withContext(Dispatchers.IO) {
            when (platform) {
                Platform.WINDOWS -> canLaunchWindowsApp(installedApp)
                Platform.MACOS -> canLaunchMacOSApp(installedApp)
                Platform.LINUX -> canLaunchLinuxApp(installedApp)
                else -> false
            }
        }

    private fun launchWindowsApp(installedApp: InstalledApp) {
        val launched =
            launchFromWindowsInstallDirs(installedApp) ||
                launchThroughWindowsShell(installedApp) ||
                launchFromWindowsRegistry(installedApp)
        check(launched) { "Could not find executable for ${installedApp.appName}" }
    }

    private fun launchFromWindowsInstallDirs(installedApp: InstalledApp): Boolean {
        val mainExe =
            listOfNotNull(
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
                System.getenv("LOCALAPPDATA"),
            ).asSequence()
                .flatMap { basePath ->
                    sequenceOf(
                        File(basePath, installedApp.appName),
                        File(basePath, installedApp.repoName),
                        File(basePath, installedApp.packageName.substringAfterLast(".")),
                    )
                }.filter { it.exists() && it.isDirectory }
                .firstNotNullOfOrNull { mainExecutableIn(it, installedApp) }
                ?: return false
        ProcessBuilder("cmd", "/c", "start", "", mainExe.absolutePath)
            .start()
        logger.debug("Launched Windows app from: ${mainExe.absolutePath}")
        return true
    }

    private fun mainExecutableIn(
        appDir: File,
        installedApp: InstalledApp,
    ): File? {
        val exeFiles =
            appDir
                .walkTopDown()
                .maxDepth(EXE_SEARCH_DEPTH)
                .filter { it.extension.equals("exe", ignoreCase = true) }
                .filter { !it.name.contains("uninstall", ignoreCase = true) }
                .toList()
        return exeFiles.find {
            it.nameWithoutExtension.equals(installedApp.appName, ignoreCase = true) ||
                it.nameWithoutExtension.equals(
                    installedApp.repoName,
                    ignoreCase = true,
                )
        } ?: exeFiles.firstOrNull()
    }

    private fun launchThroughWindowsShell(installedApp: InstalledApp): Boolean {
        val name =
            listOf(
                installedApp.appName,
                installedApp.repoName,
                installedApp.appName.replace(" ", ""),
            ).firstOrNull { tryStart("cmd", "/c", "start", "", it) }
                ?: return false
        logger.debug("Launched Windows app using shell: $name")
        return true
    }

    private fun launchFromWindowsRegistry(installedApp: InstalledApp): Boolean {
        val installLocation =
            findWindowsDisplayName(installedApp)?.let { getWindowsInstallLocation(it) }
                ?: return false
        val mainExe =
            File(installLocation)
                .listFiles { file ->
                    file.extension.equals("exe", ignoreCase = true) &&
                        !file.name.contains("uninstall", ignoreCase = true)
                }?.firstOrNull()
                ?: return false
        ProcessBuilder("cmd", "/c", "start", "", mainExe.absolutePath)
            .start()
        logger.debug("Launched Windows app from registry location: ${mainExe.absolutePath}")
        return true
    }

    private fun launchMacOSApp(installedApp: InstalledApp) {
        val launched =
            openAppBundle(applicationsBundle(installedApp), "Launched macOS app") ||
                openAppBundle(findAppBundle(File("/Applications"), installedApp), "Launched macOS app") ||
                openByBundleId(installedApp) ||
                openAppBundle(
                    findAppBundle(File(System.getProperty("user.home"), "Applications"), installedApp),
                    "Launched macOS app from user folder",
                )
        check(launched) { "Could not find app for ${installedApp.appName}" }
    }

    private fun applicationsBundle(installedApp: InstalledApp): File? {
        val appName =
            if (installedApp.appName.endsWith(".app")) {
                installedApp.appName
            } else {
                "${installedApp.appName}.app"
            }
        return File("/Applications", appName).takeIf { it.exists() }
    }

    private fun findAppBundle(
        dir: File,
        installedApp: InstalledApp,
    ): File? =
        dir.listFiles()?.find { file ->
            file.isDirectory &&
                file.name.endsWith(".app") &&
                (
                    file.name.contains(installedApp.appName, ignoreCase = true) ||
                        file.name.contains(installedApp.repoName, ignoreCase = true)
                )
        }

    private fun openAppBundle(
        bundle: File?,
        launchedMessage: String,
    ): Boolean {
        if (bundle == null) return false
        ProcessBuilder("open", "-a", bundle.absolutePath).start()
        logger.debug("$launchedMessage: ${bundle.absolutePath}")
        return true
    }

    private fun openByBundleId(installedApp: InstalledApp): Boolean {
        if (!tryStart("open", "-b", installedApp.packageName)) return false
        logger.debug("Launched macOS app by bundle ID: ${installedApp.packageName}")
        return true
    }

    private fun launchLinuxApp(installedApp: InstalledApp) {
        val launched =
            launchLinuxCommand(installedApp) ||
                launchFromDesktopEntry(installedApp) ||
                launchFlatpak(installedApp) ||
                launchAppImage(installedApp)
        check(launched) { "Could not launch ${installedApp.appName}" }
    }

    private fun launchLinuxCommand(installedApp: InstalledApp): Boolean {
        val command =
            listOf(
                installedApp.appName.lowercase().replace(" ", "-"),
                installedApp.appName.lowercase().replace(" ", ""),
                installedApp.repoName.lowercase(),
                installedApp.packageName.substringAfterLast("."),
            ).firstOrNull { tryStart(it) }
                ?: return false
        logger.debug("Launched Linux app with command: $command")
        return true
    }

    private fun launchFromDesktopEntry(installedApp: InstalledApp): Boolean {
        val command =
            desktopEntryDirs()
                .asSequence()
                .map(::File)
                .filter { it.exists() }
                .mapNotNull { dir -> desktopEntryIn(dir, installedApp) }
                .mapNotNull { execCommandOf(it) }
                .firstOrNull()
                ?: return false
        ProcessBuilder("sh", "-c", command).start()
        logger.debug("Launched Linux app from .desktop file: $command")
        return true
    }

    private fun desktopEntryIn(
        dir: File,
        installedApp: InstalledApp,
    ): File? =
        dir.listFiles()?.find { file ->
            file.extension.equals("desktop", ignoreCase = true) &&
                (
                    file.nameWithoutExtension.contains(
                        installedApp.appName,
                        ignoreCase = true,
                    ) ||
                        file.nameWithoutExtension.contains(
                            installedApp.repoName,
                            ignoreCase = true,
                        )
                )
        }

    private fun execCommandOf(desktopFile: File): String? =
        desktopFile
            .readLines()
            .find {
                it.trim().startsWith("Exec=")
            }?.substringAfter("Exec=")
            ?.replace(Regex("%[fFuU]"), "")
            ?.trim()

    private fun launchFlatpak(installedApp: InstalledApp): Boolean {
        val appId =
            executeCommand("flatpak", "list", "--app")
                .lines()
                .find {
                    it.contains(installedApp.appName, ignoreCase = true) ||
                        it.contains(installedApp.repoName, ignoreCase = true)
                }?.split(Regex("\\s+"))
                ?.firstOrNull()
                ?: return false
        if (!tryStart("flatpak", "run", appId)) return false
        logger.debug("Launched Linux app via flatpak: $appId")
        return true
    }

    private fun launchAppImage(installedApp: InstalledApp): Boolean {
        if (!installedApp.fileExtension.equals("appimage", ignoreCase = true)) return false
        val home = System.getProperty("user.home")
        val appImage =
            listOf("$home/Applications", "$home/.local/bin", "/opt")
                .asSequence()
                .mapNotNull { dir ->
                    File(dir).listFiles()?.find { file ->
                        file.extension.equals("appimage", ignoreCase = true) &&
                            file.nameWithoutExtension.contains(
                                installedApp.appName,
                                ignoreCase = true,
                            )
                    }
                }.firstOrNull { it.canExecute() }
                ?: return false
        ProcessBuilder(appImage.absolutePath).start()
        logger.debug("Launched AppImage: ${appImage.absolutePath}")
        return true
    }

    // Starts the command, or returns false when this system has nothing to run under that name.
    private fun tryStart(vararg command: String): Boolean =
        try {
            ProcessBuilder(*command).start()
            true
        } catch (_: IOException) {
            false
        }

    private fun canLaunchWindowsApp(installedApp: InstalledApp): Boolean {
        val programFiles =
            listOfNotNull(
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)"),
            )

        for (basePath in programFiles) {
            val appDir = File(basePath, installedApp.appName)
            if (appDir.exists() && appDir.isDirectory) {
                val hasExe =
                    appDir
                        .walkTopDown()
                        .maxDepth(EXE_SEARCH_DEPTH)
                        .any { it.extension.equals("exe", ignoreCase = true) }
                if (hasExe) return true
            }
        }

        return false
    }

    private fun canLaunchMacOSApp(installedApp: InstalledApp): Boolean {
        val appPath = File("/Applications", "${installedApp.appName}.app")
        if (appPath.exists()) return true

        val appsDir = File("/Applications")
        return appsDir.listFiles()?.any { file ->
            file.name.endsWith(".app") &&
                file.name.contains(installedApp.appName, ignoreCase = true)
        } ?: false
    }

    private fun canLaunchLinuxApp(installedApp: InstalledApp): Boolean {
        val command = installedApp.appName.lowercase().replace(" ", "-")
        val which = executeCommand("which", command)
        if (which.isNotBlank()) return true

        return desktopEntryDirs().any { dir ->
            File(dir).listFiles()?.any { file ->
                file.extension.equals("desktop", ignoreCase = true) &&
                    file.nameWithoutExtension.contains(installedApp.appName, ignoreCase = true)
            } ?: false
        }
    }

    private fun desktopEntryDirs(): List<String> =
        listOf(
            "/usr/share/applications",
            "/usr/local/share/applications",
            "${System.getProperty("user.home")}/.local/share/applications",
        )

    private fun findWindowsDisplayName(installedApp: InstalledApp): String? {
        val searchName = installedApp.appName
        val result =
            executeCommand(
                "powershell",
                "-Command",
                "$UNINSTALL_ENTRIES | Where-Object { \$_.DisplayName -like '*$searchName*' } | " +
                    "Select-Object -First 1 -ExpandProperty DisplayName",
            )
        return result.trim().takeIf { it.isNotBlank() }
    }

    private fun getWindowsInstallLocation(displayName: String): String? {
        val result =
            executeCommand(
                "powershell",
                "-Command",
                "$UNINSTALL_ENTRIES | Where-Object { \$_.DisplayName -eq '$displayName' } | " +
                    "Select-Object -First 1 -ExpandProperty InstallLocation",
            )
        return result.trim().takeIf { it.isNotBlank() }
    }

    private fun executeCommand(vararg command: String): String =
        try {
            val process =
                ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (_: IOException) {
            ""
        } catch (_: InterruptedException) {
            ""
        }

    private companion object {
        const val EXE_SEARCH_DEPTH = 3
        const val UNINSTALL_ENTRIES =
            "Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*"
    }
}
