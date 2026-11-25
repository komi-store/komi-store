package zed.rainxch.core.data.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import zed.rainxch.core.data.services.systemServiceCall
import zed.rainxch.core.domain.helpers.ClipboardHelper

class AndroidClipboardHelper(
    private val context: Context,
) : ClipboardHelper {
    override fun copy(
        label: String,
        text: String,
    ) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        systemServiceCall { cm.setPrimaryClip(ClipData.newPlainText(label, text)) }
    }

    override fun getText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = systemServiceCall { cm.primaryClip }?.takeIf { it.itemCount > 0 } ?: return null
        return clip.getItemAt(0).text?.toString()
    }
}
