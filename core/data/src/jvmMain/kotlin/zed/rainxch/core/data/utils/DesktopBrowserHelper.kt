package zed.rainxch.core.data.utils

import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.system.DesktopOs
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

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
        } catch (e: IOException) {
            onFailure(openFailure(e, url))
        } catch (e: URISyntaxException) {
            onFailure(openFailure(e, url))
        } catch (e: IllegalArgumentException) {
            onFailure(openFailure(e, url))
        } catch (e: UnsupportedOperationException) {
            onFailure(openFailure(e, url))
        }
    }

    private fun openFailure(
        error: Exception,
        url: String,
    ): String = "Failed to open browser: ${error.message}. Please visit: $url"
}
