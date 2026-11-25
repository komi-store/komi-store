package zed.rainxch.core.data.utils

import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.helpers.ClipboardHelper
import zed.rainxch.core.domain.model.error.SystemPackagesException
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
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        } catch (e: IllegalStateException) {
            // setContents throws it while another application holds the clipboard.
            throw SystemPackagesException(e)
        } catch (e: UnsupportedOperationException) {
            throw SystemPackagesException(e)
        }
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
