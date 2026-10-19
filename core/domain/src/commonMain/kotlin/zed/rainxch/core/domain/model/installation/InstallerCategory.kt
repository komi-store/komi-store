package zed.rainxch.core.domain.model.installation

enum class InstallerCategory(
    val sortPriority: Int,
) {
    SIDE_STORE(0),
    SIDELOADED(1),
    PLAY_STORE(2),
    VENDOR_STORE(3),
    SYSTEM_UPDATE(4),
    ;

    companion object {
        private val SIDE_STORE_INSTALLERS =
            setOf(
                "org.fdroid.fdroid",
                "org.fdroid.basic",
                "dev.imranr.obtainium",
                "dev.imranr.obtainium.fdroid",
                "com.aurora.store",
                "com.looker.droidify",
                "com.machiav3lli.fdroid",
                "nya.kitsunyan.foxydroid",
                "zed.rainxch.githubstore",
            )

        private val VENDOR_STORE_INSTALLERS =
            setOf(
                "com.sec.android.app.samsungapps",
                "com.huawei.appmarket",
                "com.xiaomi.market",
                "com.heytap.market",
                "com.oppo.market",
                "com.bbk.appstore",
                "com.hihonor.appmarket",
                "com.amazon.venezia",
            )

        private val VENDOR_PACKAGE_PREFIXES =
            listOf(
                "com.samsung.",
                "com.sec.",
                "com.sec.android.",
                "com.huawei.",
                "com.hihonor.",
                "com.honor.",
                "com.xiaomi.",
                "com.miui.",
                "com.mi.",
                "com.heytap.",
                "com.oppo.",
                "com.coloros.",
                "com.oneplus.",
                "com.bbk.",
                "com.vivo.",
                "com.iqoo.",
                "com.amazon.",
                "com.transsion.",
                "com.tecno.",
                "com.infinix.",
                "com.itel.",
                "com.motorola.",
                "com.lge.",
                "com.sonyericsson.",
                "com.sonymobile.",
            )

        private val SYSTEM_PACKAGE_PREFIXES =
            listOf(
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.android.",
                "android.",
            )

        private const val PLAY_STORE_INSTALLER = "com.android.vending"

        fun classify(
            installerPackageName: String?,
            isUpdatedSystemApp: Boolean,
            packageName: String? = null,
        ): InstallerCategory {
            if (isUpdatedSystemApp) return SYSTEM_UPDATE
            when (installerPackageName) {
                null -> Unit
                PLAY_STORE_INSTALLER -> return PLAY_STORE
                in SIDE_STORE_INSTALLERS -> return SIDE_STORE
                in VENDOR_STORE_INSTALLERS -> return VENDOR_STORE
                else -> Unit
            }
            if (packageName != null) {
                if (SYSTEM_PACKAGE_PREFIXES.any { packageName.startsWith(it) }) return SYSTEM_UPDATE
                if (VENDOR_PACKAGE_PREFIXES.any { packageName.startsWith(it) }) return VENDOR_STORE
            }
            return SIDELOADED
        }
    }
}
