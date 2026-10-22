package zed.rainxch.core.data.services.installer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class ExternalInstallerLauncher(
    private val context: Context,
) {
    fun launch(
        filePath: String,
        packageName: String,
    ): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val candidates =
            listOf(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME),
                Intent(ACTION_INSTALL_PACKAGE).setDataAndType(uri, APK_MIME),
            )
        return candidates.any { base -> startTargeted(base, packageName) }
    }

    private fun startTargeted(
        base: Intent,
        packageName: String,
    ): Boolean {
        val intent =
            Intent(base).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(packageName)
            }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val ACTION_INSTALL_PACKAGE = "android.intent.action.INSTALL_PACKAGE"
    }
}
