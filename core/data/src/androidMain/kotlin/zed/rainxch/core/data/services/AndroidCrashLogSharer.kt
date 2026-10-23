package zed.rainxch.core.data.services

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.system.CrashLogSharer

class AndroidCrashLogSharer(
    private val context: Context,
) : CrashLogSharer {
    override suspend fun hasCrashLog(): Boolean = withContext(Dispatchers.IO) { CrashLogs.latest(context) != null }

    override fun shareLatest() {
        val intent = CrashLogs.shareIntent(context) ?: return
        context.startActivity(
            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
