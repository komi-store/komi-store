package zed.rainxch.core.data.services

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.model.apk.ApkInspection
import zed.rainxch.core.domain.model.apk.ApkPermission
import zed.rainxch.core.domain.model.apk.ProtectionLevel
import zed.rainxch.core.domain.system.ApkInspector
import java.io.File

class AndroidApkInspector(
    private val context: Context,
) : ApkInspector {
    private val pm: PackageManager get() = context.packageManager

    override suspend fun inspectFile(filePath: String): ApkInspection? =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                Logger.w(TAG) { "inspectFile: file missing or unreadable: $filePath" }
                return@withContext null
            }
            val info = pm.getPackageArchiveInfoCompat(filePath, FULL_FLAGS)
            if (info == null) {
                Logger.w(TAG) { "inspectFile: PackageManager refused $filePath" }
                return@withContext null
            }

            info.applicationInfo?.apply {
                sourceDir = filePath
                publicSourceDir = filePath
            }

            try {
                buildInspection(
                    info = info,
                    source = ApkInspection.Source.FILE,
                    filePath = filePath,
                    fileSizeBytes = file.length(),
                    packageNameForGrantState = null,
                )
            } catch (t: Throwable) {
                Logger.w(TAG) { "inspectFile: failed to extract APK metadata for $filePath: $t" }
                null
            }
        }

    override suspend fun inspectInstalled(packageName: String): ApkInspection? =
        withContext(Dispatchers.IO) {
            val info =
                try {
                    pm.getPackageInfoCompat(packageName, FULL_FLAGS)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                } catch (t: Throwable) {
                    Logger.w(TAG) {
                        "inspectInstalled: PackageManager threw for $packageName: $t"
                    }
                    null
                }
            if (info == null) {
                Logger.w(TAG) { "inspectInstalled: package not found: $packageName" }
                return@withContext null
            }
            val sourceDir = info.applicationInfo?.sourceDir
            val sizeBytes = sourceDir?.let { runCatching { File(it).length() }.getOrNull() }
            try {
                buildInspection(
                    info = info,
                    source = ApkInspection.Source.INSTALLED,
                    filePath = sourceDir,
                    fileSizeBytes = sizeBytes,
                    packageNameForGrantState = packageName,
                )
            } catch (t: Throwable) {
                Logger.w(TAG) { "inspectInstalled: failed to extract metadata for $packageName: $t" }
                null
            }
        }

    private fun buildInspection(
        info: PackageInfo,
        source: ApkInspection.Source,
        filePath: String?,
        fileSizeBytes: Long?,
        packageNameForGrantState: String?,
    ): ApkInspection {
        val appInfo = info.applicationInfo
        val label = appInfo?.loadLabel(pm)?.toString().orEmpty()
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }

        val permissions = info.requestedPermissions?.toList().orEmpty()
        val grantFlags = info.requestedPermissionsFlags
        val isInstalledPackage = packageNameForGrantState != null
        val resolvedPermissions =
            permissions.mapIndexed { index, permName ->
                val granted =
                    if (isInstalledPackage && grantFlags != null && index < grantFlags.size) {
                        (grantFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    } else {
                        null
                    }
                resolvePermission(permName, granted, isInstalledPackage)
            }

        val mainActivity =
            runCatching {
                pm.getLaunchIntentForPackage(info.packageName)?.component?.className
            }.getOrNull()

        return ApkInspection(
            appLabel = label.ifBlank { info.packageName },
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCode,
            signingFingerprint = SigningFingerprint.fromPackageInfo(info),
            minSdk =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo?.minSdkVersion
                } else {
                    null
                },
            targetSdk = appInfo?.targetSdkVersion,
            permissions = resolvedPermissions,
            mainActivity = mainActivity,
            activityCount = info.activities?.size ?: 0,
            serviceCount = info.services?.size ?: 0,
            receiverCount = info.receivers?.size ?: 0,
            fileSizeBytes = fileSizeBytes,
            filePath = filePath,
            debuggable = appInfo?.let { (it.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 } ?: false,
            source = source,
        )
    }

    private fun resolvePermission(
        name: String,
        granted: Boolean?,
        isInstalledPackage: Boolean,
    ): ApkPermission {
        val info =
            runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()
        val protection = info?.let { resolveProtectionLevel(it) } ?: ProtectionLevel.UNKNOWN
        val display =
            info?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                ?: name
                    .substringAfterLast('.')
                    .replace('_', ' ')
                    .lowercase()
                    .replaceFirstChar { it.titlecase() }
        val description = info?.loadDescription(pm)?.toString()?.takeIf { it.isNotBlank() }

        val resolvedGranted =
            when {
                granted != null -> granted
                isInstalledPackage && protection == ProtectionLevel.NORMAL -> true
                else -> null
            }
        return ApkPermission(
            name = name,
            displayName = display,
            description = description,
            protectionLevel = protection,
            granted = resolvedGranted,
        )
    }

    private fun resolveProtectionLevel(info: PermissionInfo): ProtectionLevel {
        val base =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.protection
            } else {
                @Suppress("DEPRECATION")
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
            }
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.protectionFlags
            } else {
                @Suppress("DEPRECATION")
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_FLAGS
            }
        return when (base) {
            PermissionInfo.PROTECTION_DANGEROUS -> {
                ProtectionLevel.DANGEROUS
            }

            PermissionInfo.PROTECTION_SIGNATURE -> {
                if ((flags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0) {
                    ProtectionLevel.PRIVILEGED
                } else {
                    ProtectionLevel.SIGNATURE
                }
            }

            PermissionInfo.PROTECTION_NORMAL -> {
                ProtectionLevel.NORMAL
            }

            else -> {
                ProtectionLevel.UNKNOWN
            }
        }
    }

    private companion object {
        const val TAG = "AndroidApkInspector"

        @Suppress("DEPRECATION")
        val FULL_FLAGS: Int =
            (
                PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS
            ) or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
    }
}

private fun PackageManager.getPackageArchiveInfoCompat(
    filePath: String,
    flags: Int,
): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(filePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageArchiveInfo(filePath, flags)
    }

private fun PackageManager.getPackageInfoCompat(
    packageName: String,
    flags: Int,
): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, flags)
    }
