package zed.rainxch.core.data.utils

import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.helpers.ClipboardHelper
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

class DesktopClipboardHelper : ClipboardHelper {
    override fun copy(
        label: String,
        text: String,
    ) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }

    override fun getText(): String? =
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (e: IllegalStateException) {
            unreadable(e)
        } catch (e: UnsupportedFlavorException) {
            unreadable(e)
        } catch (e: IOException) {
            unreadable(e)
        } catch (e: UnsupportedOperationException) {
            unreadable(e)
        }

    private fun unreadable(error: Exception): String? {
        Logger.w(error) { "Could not read the clipboard" }
        return null
    }
}
