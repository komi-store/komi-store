package zed.rainxch.core.presentation.components

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import zed.rainxch.core.domain.model.error.SystemPackagesException

private const val TAG = "InstalledAppIcon"

@Composable
actual fun InstalledAppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier,
    apkFilePath: String?,
    avatarFallbackUrl: String?,
) {
    val packageManager = LocalContext.current.packageManager
    val iconBitmap =
        remember(packageName, apkFilePath, packageManager) {
            resolveInstalledIcon(packageManager, packageName)
                ?: apkFilePath?.let { resolveApkIcon(packageManager, it) }
        }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = appName,
            modifier = modifier,
        )
    } else {
        FallbackAppIcon(appName = appName, avatarFallbackUrl = avatarFallbackUrl, modifier = modifier)
    }
}

internal fun resolveInstalledIcon(
    packageManager: PackageManager,
    packageName: String,
): ImageBitmap? =
    try {
        systemServiceCall { packageManager.getApplicationIcon(packageName) }
            .toBitmap()
            .asImageBitmap()
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "failed to load installed icon for $packageName", e)
        null
    } catch (e: SystemPackagesException) {
        Log.w(TAG, "failed to load installed icon for $packageName", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "failed to load installed icon for $packageName", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "installed icon for $packageName is too big to draw", e)
        null
    }

private fun resolveApkIcon(
    packageManager: PackageManager,
    apkFilePath: String,
): ImageBitmap? =
    try {
        val appInfo = packageManager.getPackageArchiveInfo(apkFilePath, 0)?.applicationInfo ?: return null
        appInfo.sourceDir = apkFilePath
        appInfo.publicSourceDir = apkFilePath
        systemServiceCall { appInfo.loadIcon(packageManager) }
            ?.toBitmap()
            ?.asImageBitmap()
    } catch (e: SystemPackagesException) {
        Log.w(TAG, "failed to load icon from APK at $apkFilePath", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "failed to load icon from APK at $apkFilePath", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "icon from APK at $apkFilePath is too big to draw", e)
        null
    }
