package zed.rainxch.core.data.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import zed.rainxch.core.data.services.installer.AndroidInstallerAppHandoff
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerHandoff
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import zed.rainxch.core.domain.utils.AssetArchitectureMatcher
import zed.rainxch.core.domain.utils.AssetSelector
import zed.rainxch.core.domain.utils.isAndroidApk
import java.io.File

class AndroidInstaller(
    private val context: Context,
    private val installerInfoExtractor: InstallerInfoExtractor,
) : Installer,
    InstallerHandoff by AndroidInstallerAppHandoff(context) {
    override fun getApkInfoExtractor(): InstallerInfoExtractor = installerInfoExtractor

    override fun detectSystemArchitecture(): SystemArchitecture {
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: return SystemArchitecture.UNKNOWN
        return when {
            arch.contains("arm64") || arch.contains("aarch64") -> SystemArchitecture.AARCH64
            arch.contains("armeabi") -> SystemArchitecture.ARM
            arch.contains("x86_64") -> SystemArchitecture.X86_64
            arch.contains("x86") -> SystemArchitecture.X86
            else -> SystemArchitecture.UNKNOWN
        }
    }

    override fun isAssetInstallable(assetName: String): Boolean {
        if (!isAndroidApk(assetName)) return false
        val systemArch = detectSystemArchitecture()
        return isArchitectureCompatible(assetName.lowercase(), systemArch)
    }

    private fun isArchitectureCompatible(
        assetName: String,
        systemArch: SystemArchitecture,
    ): Boolean = AssetArchitectureMatcher.isCompatible(assetName, systemArch)

    override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? {
        if (assets.isEmpty()) return null
        val systemArch = detectSystemArchitecture()
        val androidApks = assets.filter { asset -> isAndroidApk(asset.name) }
        if (androidApks.isEmpty()) return null
        val compatibleAssets =
            androidApks.filter { asset ->
                isArchitectureCompatible(asset.name.lowercase(), systemArch)
            }
        val assetsToConsider = compatibleAssets.ifEmpty { androidApks }
        return AssetSelector.choose(
            assets = assetsToConsider,
            deviceArch = systemArch,
            extensionPriority = listOf(".apk"),
        )
    }

    override suspend fun isSupported(extOrMime: String): Boolean {
        val ext = extOrMime.lowercase().removePrefix(".")
        return ext == "apk"
    }

    override suspend fun ensurePermissionsOrThrow(extOrMime: String) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            startActivityOrThrow(intent)
            error("Please enable 'Install unknown apps' for this app in Settings and try again.")
        }
    }

    override suspend fun install(
        filePath: String,
        extOrMime: String,
    ): InstallOutcome {
        val file = File(filePath)
        check(file.exists()) { "APK file not found: $filePath" }

        Logger.d { "Installing APK: $filePath" }

        val authority = "${context.packageName}.fileprovider"
        val fileUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        check(intent.resolveActivity(context.packageManager) != null) { "No installer available on this device" }
        intent.preferSystemInstaller()
        startActivityOrThrow(intent)
        Logger.d { "APK installation intent launched" }

        return InstallOutcome.DELEGATED_TO_SYSTEM
    }

    // A device without the screen (some TV builds) throws ActivityNotFoundException, which common code can't name.
    private fun startActivityOrThrow(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw SystemPackagesException(e)
        }
    }

    // Several installers and no Android default would open a chooser per install, so Default means the system installer.
    private fun Intent.preferSystemInstaller() {
        val packageManager = context.packageManager
        val resolved = resolveActivity(packageManager) ?: return
        val handlers = packageManager.defaultHandlers(this)
        if (handlers.any { it.activityInfo.packageName == resolved.packageName }) return
        handlers
            .firstOrNull { it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            ?.let { setPackage(it.activityInfo.packageName) }
    }

    private fun PackageManager.defaultHandlers(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

    override fun uninstall(packageName: String) {
        Logger.d { "Requesting uninstall for: $packageName" }
        val intent =
            Intent(Intent.ACTION_DELETE).apply {
                data = "package:$packageName".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.w { "Failed to start uninstall for $packageName: ${e.message}" }
        } catch (e: SecurityException) {
            Logger.w { "Failed to start uninstall for $packageName: ${e.message}" }
        }
    }

    override fun openApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } catch (e: ActivityNotFoundException) {
                Logger.w { "Failed to launch $packageName: ${e.message}" }
                false
            }
        } else {
            Logger.w { "No launch intent found for $packageName" }
            false
        }
    }
}
