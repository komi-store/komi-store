package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.model.system.Platform
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.IOException

internal class DesktopInstallNotifier(
    private val platform: Platform,
) {
    fun show(
        title: String,
        message: String,
    ) {
        if (platform == Platform.MACOS) {
            val script =
                """
                display notification "$message" with title "$title"
                """.trimIndent()
            start(listOf("osascript", "-e", script), failure = "Could not show macOS notification")
        } else {
            start(
                listOf("notify-send", title, message, "-u", "critical", "-t", "10000"),
                failure = "Could not show notification",
            )
        }
    }

    fun copyToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            Logger.d { "Command copied to clipboard" }
        } catch (e: IllegalStateException) {
            Logger.w(e) { "Could not copy to clipboard" }
        } catch (e: UnsupportedOperationException) {
            Logger.w(e) { "Could not copy to clipboard" }
        }
    }

    private fun start(
        command: List<String>,
        failure: String,
    ) {
        try {
            ProcessBuilder(command).start()
        } catch (e: IOException) {
            Logger.w(e) { failure }
        }
    }
}
