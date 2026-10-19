package zed.rainxch.core.data.utils

import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.system.DesktopOs
import java.awt.Desktop
import java.net.URI

class DesktopBrowserHelper : BrowserHelper {
    override fun openUrl(
        url: String,
        onFailure: (error: String) -> Unit,
    ) {
        try {
            when {
                DesktopOs.isLinux -> {
                    val processBuilder = ProcessBuilder("xdg-open", url)
                    processBuilder.redirectErrorStream(true)
                    processBuilder.start()
                }

                Desktop.isDesktopSupported() &&
                    Desktop
                        .getDesktop()
                        .isSupported(Desktop.Action.BROWSE)
                -> {
                    Desktop.getDesktop().browse(URI(url))
                }

                else -> {
                    onFailure("Cannot open browser automatically. Please visit: $url")
                }
            }
        } catch (e: Exception) {
            onFailure("Failed to open browser: ${e.message}. Please visit: $url")
        }
    }
}
