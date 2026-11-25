package zed.rainxch.githubstore

import zed.rainxch.core.domain.system.DesktopOs
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

object DesktopDeepLink {
    private const val SINGLE_INSTANCE_PORT = 47632
    private const val SINGLE_INSTANCE_BACKLOG = 50
    private const val SCHEME = "githubstore"
    private const val DESKTOP_FILE_NAME = "github-store-deeplink"

    fun registerUriSchemeIfNeeded() {
        when {
            isWindows() -> registerWindows()
            isLinux() -> registerLinux()
        }
    }

    private fun registerWindows() {
        val exePath = resolveExePath()
        if (exePath == null) {
            println("DeepLink: skipped Windows scheme registration (exe path unresolved)")
            return
        }
        val commandValue = "\"$exePath\" \"%1\""
        if (!windowsRegistrationIsValid(commandValue)) {
            importRegistryEntries(windowsRegistryEntries(iconValue = "\"$exePath\",1", commandValue = commandValue))
        }
    }

    private fun windowsRegistryEntries(
        iconValue: String,
        commandValue: String,
    ): String =
        buildString {
            append("Windows Registry Editor Version 5.00\r\n\r\n")
            append("[HKEY_CURRENT_USER\\SOFTWARE\\Classes\\$SCHEME]\r\n")
            append("@=\"URL:Komi Store Protocol\"\r\n")
            append("\"URL Protocol\"=\"\"\r\n\r\n")
            append("[HKEY_CURRENT_USER\\SOFTWARE\\Classes\\$SCHEME\\DefaultIcon]\r\n")
            append("@=\"${regEscape(iconValue)}\"\r\n\r\n")
            append("[HKEY_CURRENT_USER\\SOFTWARE\\Classes\\$SCHEME\\shell\\open\\command]\r\n")
            append("@=\"${regEscape(commandValue)}\"\r\n")
        }

    private fun importRegistryEntries(regContent: String) {
        val regFile =
            try {
                File.createTempFile("komi-scheme", ".reg")
            } catch (e: IOException) {
                println("DeepLink: Windows scheme registration failed (temp file): ${e.message}")
                null
            } ?: return

        try {
            regFile.writeBytes(("$regContent").toByteArray(Charsets.UTF_16LE))
            val result = runCommandResult("reg", "import", regFile.absolutePath)
            if (result == null || result.exitCode != 0) {
                println("DeepLink: Windows scheme registration failed (reg import): ${result?.output?.trim().orEmpty()}")
            }
        } catch (e: IOException) {
            println("DeepLink: Windows scheme registration failed: ${e.message}")
        } finally {
            regFile.delete()
        }
    }

    private fun windowsRegistrationIsValid(expectedCommandValue: String): Boolean {
        val protocol = runCommand("reg", "query", "HKCU\\SOFTWARE\\Classes\\$SCHEME", "/v", "URL Protocol")
        val command =
            protocol
                ?.takeIf { it.contains("URL Protocol") }
                ?.let { runCommand("reg", "query", "HKCU\\SOFTWARE\\Classes\\$SCHEME\\shell\\open\\command", "/ve") }
        val actualCommandValue =
            command
                ?.lineSequence()
                ?.firstOrNull { it.contains("REG_SZ") }
                ?.substringAfter("REG_SZ")
                ?.trim()
        return command != null && actualCommandValue.equals(expectedCommandValue, ignoreCase = true)
    }

    private fun regEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun registerLinux() {
        val appsDir = File(System.getProperty("user.home"), ".local/share/applications")
        val desktopFile = File(appsDir, "$DESKTOP_FILE_NAME.desktop")

        if (desktopFile.exists()) return

        val exePath = resolveExePath() ?: return

        appsDir.mkdirs()

        desktopFile.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=Komi Store
            Exec="$exePath" %u
            Terminal=false
            MimeType=x-scheme-handler/$SCHEME;
            NoDisplay=true
            """.trimIndent(),
        )

        runCommand("xdg-mime", "default", "$DESKTOP_FILE_NAME.desktop", "x-scheme-handler/$SCHEME")
    }

    fun tryForwardToRunningInstance(uri: String): Boolean =
        try {
            Socket("127.0.0.1", SINGLE_INSTANCE_PORT).use { socket ->
                PrintWriter(socket.getOutputStream(), true).println(uri)
            }
            true
        } catch (_: IOException) {
            false
        }

    fun startInstanceListener(onUri: (String) -> Unit) {
        val thread = Thread({ listenForForwardedUris(onUri) }, "DeepLinkListener")
        thread.isDaemon = true
        thread.start()
    }

    // Binding fails when another instance already listens, and a failed accept ends the listener; both end quietly.
    private fun listenForForwardedUris(onUri: (String) -> Unit) {
        try {
            val server = ServerSocket(SINGLE_INSTANCE_PORT, SINGLE_INSTANCE_BACKLOG, InetAddress.getLoopbackAddress())
            while (true) {
                server.accept().use { client -> readForwardedUri(client)?.let(onUri) }
            }
        } catch (_: IOException) {
        }
    }

    private fun readForwardedUri(client: Socket): String? =
        try {
            BufferedReader(InputStreamReader(client.getInputStream())).readLine()?.takeIf { it.isNotBlank() }?.trim()
        } catch (_: IOException) {
            null
        }

    private fun isWindows(): Boolean = DesktopOs.isWindows

    private fun isLinux(): Boolean = DesktopOs.isLinux

    private fun resolveExePath(): String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() } ?: currentProcessCommand()

    private fun currentProcessCommand(): String? =
        try {
            ProcessHandle
                .current()
                .info()
                .command()
                .orElse(null)
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.endsWith("java.exe", ignoreCase = true) &&
                        !it.endsWith("javaw.exe", ignoreCase = true)
                }
        } catch (_: SecurityException) {
            null
        }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
    )

    private fun runCommandResult(vararg cmd: String): CommandResult? =
        try {
            val process =
                ProcessBuilder(*cmd)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            CommandResult(process.waitFor(), output)
        } catch (_: IOException) {
            null
        } catch (_: InterruptedException) {
            null
        }

    private fun runCommand(vararg cmd: String): String? = runCommandResult(*cmd)?.takeIf { it.exitCode == 0 }?.output
}
