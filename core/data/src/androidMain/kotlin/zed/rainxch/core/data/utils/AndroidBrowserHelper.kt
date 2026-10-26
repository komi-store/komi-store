package zed.rainxch.core.data.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import zed.rainxch.core.domain.helpers.BrowserHelper

class AndroidBrowserHelper(
    private val context: Context,
) : BrowserHelper {
    override fun openUrl(
        url: String,
        onFailure: (error: String) -> Unit,
    ) {
        val intent =
            Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            onFailure(e.message ?: FALLBACK_ERROR)
        } catch (e: SecurityException) {
            onFailure(e.message ?: FALLBACK_ERROR)
        }
    }

    private companion object {
        const val FALLBACK_ERROR = "Unable to open the requested URL."
    }
}
