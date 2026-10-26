package zed.rainxch.core.data.services.external

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import zed.rainxch.core.data.services.installingPackageNameOf
import zed.rainxch.core.domain.system.InstallerKind

class InstallerSourceClassifier(
    private val packageManager: PackageManager,
    private val selfPackageName: String,
) {
    fun classify(
        packageName: String,
        applicationInfo: ApplicationInfo?,
    ): InstallerKind {
        val flags = applicationInfo?.flags ?: 0
        val isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0
        return when {
            packageName == selfPackageName -> InstallerKind.GITHUB_STORE_SELF
            isSystem -> InstallerKind.SYSTEM
            OEM_PACKAGE_PREFIXES.any { packageName.startsWith(it) } -> InstallerKind.STORE_OEM_OTHER
            else -> mapInstaller(installerPackageNameFor(packageName), isSystem = false)
        }
    }

    fun classifyByInstaller(installerPackageName: String?): InstallerKind = mapInstaller(installerPackageName, isSystem = false)

    private fun installerPackageNameFor(packageName: String): String? =
        runCatching { packageManager.installingPackageNameOf(packageName) }.getOrNull()

    private fun mapInstaller(
        installer: String?,
        isSystem: Boolean,
    ): InstallerKind {
        if (installer == null) {
            return if (isSystem) InstallerKind.SYSTEM else InstallerKind.SIDELOAD
        }
        return when (installer) {
            in OBTAINIUM_PACKAGES -> InstallerKind.STORE_OBTAINIUM
            FDROID -> InstallerKind.STORE_FDROID
            PLAY -> InstallerKind.STORE_PLAY
            AURORA -> InstallerKind.STORE_AURORA
            GALAXY -> InstallerKind.STORE_GALAXY
            in OEM_STORES -> InstallerKind.STORE_OEM_OTHER
            in BROWSERS -> InstallerKind.BROWSER
            in SIDELOAD_PACKAGES -> InstallerKind.SIDELOAD
            else -> InstallerKind.UNKNOWN
        }
    }

    companion object {
        private val OBTAINIUM_PACKAGES =
            setOf(
                "dev.imranr.obtainium",
                "dev.imranr.obtainium.app",
                "dev.imranr.obtainium.fdroid",
            )
        private const val FDROID = "org.fdroid.fdroid"
        private const val PLAY = "com.android.vending"
        private const val AURORA = "com.aurora.store"
        private const val GALAXY = "com.sec.android.app.samsungapps"

        private val OEM_STORES =
            setOf(
                "com.huawei.appmarket",
                "com.xiaomi.market",
                "com.heytap.market",
                "com.oppo.market",
                "com.vivo.appstore",
                "com.miui.packageinstaller",
            )

        private val BROWSERS =
            setOf(
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "com.chrome.canary",
                "com.brave.browser",
                "org.mozilla.firefox",
                "org.mozilla.firefox_beta",
                "org.mozilla.fenix",
                "com.microsoft.emmx",
                "com.vivaldi.browser",
                "com.sec.android.app.sbrowser",
                "com.duckduckgo.mobile.android",
                "com.opera.browser",
                "com.opera.mini.native",
            )

        private val SIDELOAD_PACKAGES =
            setOf(
                "com.android.shell",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
            )

        private val OEM_PACKAGE_PREFIXES =
            listOf(
                "com.samsung.",
                "com.sec.",
                "com.lge.",
                "com.huawei.",
                "com.honor.",
                "com.miui.",
                "com.xiaomi.",
                "com.mi.",
                "com.oneplus.",
                "com.oppo.",
                "com.heytap.",
                "com.coloros.",
                "com.realme.",
                "com.vivo.",
                "com.iqoo.",
                "com.motorola.",
                "com.lenovo.",
                "com.asus.",
                "com.sony.",
                "com.nokia.",
                "com.htc.",
                "com.amazon.",
                "com.google.android.",
                "com.android.",
            )
    }
}
