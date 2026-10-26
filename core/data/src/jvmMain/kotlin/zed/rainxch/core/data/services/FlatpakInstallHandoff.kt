package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import java.io.File
import java.io.IOException

internal class FlatpakInstallHandoff {
    fun install(
        file: File,
        ext: String,
    ) {
        Logger.i { "Running in Flatpak sandbox, delegating installation to host system" }
        Logger.i { "File: ${file.absolutePath} (.$ext)" }

        when {
            file.name.lowercase().endsWith(".pkg.tar.zst") -> {
                openPackageOnHost(file, ARCH_PACKAGE)
            }

            ext == "deb" || ext == "rpm" -> {
                openPackageOnHost(file, nativePackage(ext))
            }

            ext == "appimage" -> {
                prepareAppImage(file)
            }

            else -> {
                notify(
                    title = "Download Complete",
                    message = "File saved to your Downloads folder.",
                )
                openInFileManager(file)
            }
        }
    }

    private fun nativePackage(ext: String) =
        HostPackage(
            label = ".$ext",
            readyMessage =
                "The ${ext.uppercase()} package has been opened in your system's " +
                    "software installer. Follow the prompts to complete installation.",
            unopenedMessage = "Please open this file with your software center to install.",
            manualMessage = "Please install manually from your file manager.",
        )

    private fun openPackageOnHost(
        file: File,
        pkg: HostPackage,
    ) {
        Logger.d { "Opening ${pkg.label} package via xdg-open portal for host installation" }
        val exitCode =
            processExitCode(listOf("xdg-open", file.absolutePath))
                .onFailure { Logger.w(it) { "Failed to open ${pkg.label} via xdg-open" } }
                .getOrNull()
        when (exitCode) {
            0 -> {
                Logger.i { "Package opened on host system for installation" }
                notify(title = "Package Ready to Install", message = pkg.readyMessage)
            }

            null -> {
                notify(title = "Download Complete", message = pkg.manualMessage)
                openInFileManager(file)
            }

            else -> {
                Logger.w { "xdg-open exited with code $exitCode" }
                notify(title = "Installation", message = pkg.unopenedMessage)
                openInFileManager(file)
            }
        }
    }

    private fun prepareAppImage(file: File) {
        Logger.d { "AppImage downloaded in Flatpak, preparing for host launch" }

        try {
            file.setExecutable(true, false)
            Logger.d { "Set executable permission on AppImage" }
        } catch (e: SecurityException) {
            Logger.w(e) { "Could not set executable permission" }
        }

        notify(
            title = "AppImage Downloaded",
            message = "Right-click → Properties → mark as executable, then double-click to run.",
        )

        openInFileManager(file)
    }

    private fun notify(
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
        } catch (e: IOException) {
            Logger.w(e) { "Could not show Flatpak notification" }
            Logger.i { "[$title] $message" }
        }
    }

    private fun openInFileManager(file: File) {
        if (showItemInFileManager(file)) return
        val parentDir = file.parentFile ?: return
        try {
            ProcessBuilder("xdg-open", parentDir.absolutePath).start()
            Logger.d { "Opened parent directory: ${parentDir.absolutePath}" }
        } catch (e: IOException) {
            Logger.w(e) { "Could not open file manager" }
        }
    }

    private fun showItemInFileManager(file: File): Boolean {
        val fileUri = "file://${file.absolutePath}"
        val exitCode =
            processExitCode(
                listOf(
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
                ),
            ).onFailure { Logger.w(it) { "D-Bus ShowItems not available" } }
                .getOrNull()
        when (exitCode) {
            0 -> Logger.d { "Opened file manager via D-Bus ShowItems: ${file.absolutePath}" }
            null -> Unit
            else -> Logger.w { "D-Bus ShowItems failed with exit code $exitCode" }
        }
        return exitCode == 0
    }

    private class HostPackage(
        val label: String,
        val readyMessage: String,
        val unopenedMessage: String,
        val manualMessage: String,
    )

    private companion object {
        val ARCH_PACKAGE =
            HostPackage(
                label = ".pkg.tar.zst",
                readyMessage =
                    "The Arch package has been opened in your system's " +
                        "package manager. Follow the prompts to complete installation.",
                unopenedMessage = "Please open this .pkg.tar.zst file with your package manager.",
                manualMessage = "Please install manually: sudo pacman -U <path>",
            )
    }
}
