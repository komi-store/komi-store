package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import java.io.File
import java.io.IOException

internal class AppImageInstaller {
    fun install(file: File) {
        Logger.d { "Installing AppImage: ${file.absolutePath}" }
        val failure =
            try {
                installAndLaunch(file)
                null
            } catch (e: IOException) {
                e
            } catch (e: SecurityException) {
                e
            } catch (e: IllegalStateException) {
                e
            } catch (e: InterruptedException) {
                e
            }
        if (failure != null) throw installError(failure)
    }

    private fun installError(cause: Exception): IllegalStateException {
        Logger.e(cause) { "Failed to install AppImage" }
        val message =
            when (cause) {
                is IOException -> {
                    "Failed to install AppImage: ${cause.message}. " +
                        "Please ensure you have write permissions to ~/Applications folder."
                }

                is SecurityException -> {
                    "Security restrictions prevent installing AppImage."
                }

                else -> {
                    "Failed to install AppImage: ${cause.message}"
                }
            }
        return IllegalStateException(message, cause)
    }

    private fun installAndLaunch(file: File) {
        Logger.d { "Moving AppImage to ~/Applications..." }
        val installedFile = moveToApplicationsDirectory(file)
        Logger.d { "Moved to: ${installedFile.absolutePath}" }

        makeExecutable(installedFile)
        Logger.d { "AppImage is now executable" }

        Logger.d { "Launching AppImage..." }
        val process =
            ProcessBuilder(installedFile.absolutePath)
                .inheritIO()
                .start()

        Logger.d { "AppImage launched successfully (PID: ${process.pid()})" }

        showInstallationNotification(installedFile)

        Logger.d { "AppImage installation completed successfully" }
    }

    private fun makeExecutable(installedFile: File) {
        Logger.d { "Setting executable permissions..." }
        val executableSet = installedFile.setExecutable(true, false)
        Logger.d { "Set executable via Java: $executableSet" }

        if (!executableSet) {
            Logger.w { "Failed to set executable via Java, trying chmod..." }
            val chmodProcess = ProcessBuilder("chmod", "+x", installedFile.absolutePath).start()
            val chmodExitCode = chmodProcess.waitFor()
            Logger.d { "chmod exit code: $chmodExitCode" }
        }

        check(installedFile.canExecute()) { "Failed to make AppImage executable" }
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

        check(finalDestination.exists()) { "File was moved but doesn't exist at destination" }

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
        } catch (e: IOException) {
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
        } while (candidateFile.exists() && counter < MAX_NAME_ATTEMPTS)

        check(!candidateFile.exists()) { "Could not generate unique filename" }

        return candidateFile
    }

    private companion object {
        const val MAX_NAME_ATTEMPTS = 1000
    }
}
