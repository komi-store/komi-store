package zed.rainxch.core.presentation.components

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import org.jetbrains.compose.resources.painterResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.app_icon

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

    when {
        iconBitmap != null ->
            Image(
                bitmap = iconBitmap,
                contentDescription = appName,
                modifier = modifier,
            )

        // Nothing on this device can prove what this app's icon is: it is not installed, and the
        // file we have is not a readable APK yet. The repository's owner avatar is then the only
        // image that actually belongs to this repository. Falling through to the bundled app_icon
        // below would stamp THIS application's logo onto someone else's download, which reads as
        // "this is Komi Store" — the one thing the icon must not say.
        avatarFallbackUrl != null ->
            GitHubStoreImage(
                imageModel = { avatarFallbackUrl },
                modifier = modifier,
            )

        else ->
            Image(
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = appName,
                modifier = modifier,
            )
    }
}

private fun resolveInstalledIcon(
    packageManager: PackageManager,
    packageName: String,
): ImageBitmap? =
    try {
        packageManager
            .getApplicationIcon(packageName)
            .toBitmap()
            .asImageBitmap()
    } catch (t: Throwable) {

        Log.w(TAG, "failed to load installed icon for $packageName", t)
        null
    }

private fun resolveApkIcon(
    packageManager: PackageManager,
    apkFilePath: String,
): ImageBitmap? =
    try {

        val info = getPackageArchiveInfoCompat(packageManager, apkFilePath)
        val appInfo = info?.applicationInfo ?: return null
        appInfo.sourceDir = apkFilePath
        appInfo.publicSourceDir = apkFilePath
        appInfo
            .loadIcon(packageManager)
            ?.toBitmap()
            ?.asImageBitmap()
    } catch (t: Throwable) {
        Log.w(TAG, "failed to load icon from APK at $apkFilePath", t)
        null
    }

private fun getPackageArchiveInfoCompat(
    packageManager: PackageManager,
    apkFilePath: String,
) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageArchiveInfo(
            apkFilePath,
            PackageManager.PackageInfoFlags.of(0L),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageArchiveInfo(apkFilePath, 0)
    }
