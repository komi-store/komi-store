package zed.rainxch.core.data.services.installer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.model.installation.ExternalInstallerApp
import zed.rainxch.core.domain.repository.ExternalInstallerRepository

// queryIntentActivities only matches declared filters, so a synthetic APK content URI enumerates installers.
class ExternalInstallerDiscovery(
    private val context: Context,
) : ExternalInstallerRepository {
    override suspend fun listInstallers(): List<ExternalInstallerApp> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val probe = "content://${context.packageName}.fileprovider/probe.apk".toUri()
            val probes =
                listOf(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(probe, APK_MIME)
                        .addCategory(Intent.CATEGORY_DEFAULT),
                    Intent(Intent.ACTION_VIEW).setDataAndType(probe, APK_MIME),
                    Intent(ACTION_INSTALL_PACKAGE)
                        .setDataAndType(probe, APK_MIME)
                        .addCategory(Intent.CATEGORY_DEFAULT),
                )
            probes
                .flatMap { intent -> packageManager.resolveAll(intent) }
                .mapNotNull { info ->
                    val packageName =
                        info.activityInfo?.packageName?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                    if (packageName == context.packageName) return@mapNotNull null
                    // The system package installer is what Default already uses.
                    val flags = info.activityInfo?.applicationInfo?.flags ?: 0
                    if (flags and ApplicationInfo.FLAG_SYSTEM != 0) return@mapNotNull null
                    val label =
                        runCatching {
                            info.activityInfo.applicationInfo
                                .loadLabel(packageManager)
                                .toString()
                        }.getOrDefault(packageName)
                            .ifBlank { packageName }
                    ExternalInstallerApp(packageName = packageName, label = label)
                }.distinctBy { it.packageName }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }

    private fun PackageManager.resolveAll(intent: Intent): List<android.content.pm.ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val ACTION_INSTALL_PACKAGE = "android.intent.action.INSTALL_PACKAGE"
    }
}
