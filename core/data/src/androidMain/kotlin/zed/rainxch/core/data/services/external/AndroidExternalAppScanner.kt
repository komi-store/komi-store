package zed.rainxch.core.data.services.external

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalAppScanner
import zed.rainxch.core.domain.system.InstallerKind
import zed.rainxch.core.domain.system.VisiblePackageEstimate

class AndroidExternalAppScanner(
    context: Context,
    private val manifestHintExtractor: ManifestHintExtractor,
    private val installerSourceClassifier: InstallerSourceClassifier,
) : ExternalAppScanner {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val selfPackageName: String = appContext.packageName

    override suspend fun isPermissionGranted(): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext true
            val pkgs = listInstalledPackages(includeMeta = false)

            pkgs.size >= GRANT_THRESHOLD
        }

    override suspend fun visiblePackageCountEstimate(): VisiblePackageEstimate =
        withContext(Dispatchers.IO) {
            val granted = isPermissionGranted()
            val visible = listInstalledPackages(includeMeta = false).size
            val invisible =
                if (granted) {
                    0
                } else {
                    INVISIBLE_GUESS
                }
            VisiblePackageEstimate(
                visibleCount = visible,
                invisibleEstimate = invisible,
                permissionGranted = granted,
            )
        }

    override suspend fun snapshot(): List<ExternalAppCandidate> =
        withContext(Dispatchers.IO) {
            val now = nowMillis()
            listInstalledPackages(includeMeta = true)
                .asSequence()
                .filter { it.packageName != selfPackageName }
                .mapNotNull { pkgInfo -> toCandidate(pkgInfo, now) }
                .filterNot { it.installerKind == InstallerKind.SYSTEM }
                .filterNot { it.installerKind == InstallerKind.STORE_PLAY }
                .filterNot { it.installerKind == InstallerKind.STORE_AURORA }
                .filterNot { it.installerKind == InstallerKind.STORE_GALAXY }
                .filterNot { it.installerKind == InstallerKind.STORE_OEM_OTHER }
                .toList()
        }

    override suspend fun snapshotSingle(packageName: String): ExternalAppCandidate? =
        withContext(Dispatchers.IO) {
            val info = loadSinglePackage(packageName) ?: return@withContext null
            toCandidate(info, nowMillis())
        }

    private fun listInstalledPackages(includeMeta: Boolean): List<PackageInfo> {
        val baseFlags =
            if (includeMeta) PackageManager.GET_META_DATA.toLong() else 0L

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(baseFlags))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(baseFlags.toInt())
            }
        }.getOrDefault(emptyList())
    }

    private fun loadSinglePackage(packageName: String): PackageInfo? =
        runCatching {
            val flags = PackageManager.GET_META_DATA.toLong()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, flags.toInt())
            }
        }.getOrNull()

    private fun toCandidate(
        pkgInfo: PackageInfo,
        firstSeenAt: Long,
    ): ExternalAppCandidate? {
        val appInfo = pkgInfo.applicationInfo ?: return null
        val packageName = pkgInfo.packageName
        val label =
            runCatching { appInfo.loadLabel(packageManager).toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: packageName

        val installerKind = installerSourceClassifier.classify(packageName, appInfo)
        val manifestHint = manifestHintExtractor.extract(appInfo.metaData)
        val versionCode = longVersionCode(pkgInfo)
        val fingerprint = SigningFingerprintComputer.compute(packageManager, packageName)

        return ExternalAppCandidate(
            packageName = packageName,
            appLabel = label,
            versionName = pkgInfo.versionName,
            versionCode = versionCode,
            signingFingerprint = fingerprint,
            installerKind = installerKind,
            manifestHint = manifestHint,
            firstSeenAt = firstSeenAt,
        )
    }

    private fun longVersionCode(pkgInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

    private fun nowMillis(): Long = System.currentTimeMillis()

    companion object {
        private const val GRANT_THRESHOLD = 30
        private const val INVISIBLE_GUESS = 100
    }
}
