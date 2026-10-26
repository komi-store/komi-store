package zed.rainxch.core.data.services

import android.content.pm.PackageManager
import android.os.Build

// Before API 30 the installer is readable only through the deprecated getInstallerPackageName.
internal fun PackageManager.installingPackageNameOf(packageName: String): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getInstallSourceInfo(packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        getInstallerPackageName(packageName)
    }
