package zed.rainxch.core.data.services

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import java.io.File
import java.security.MessageDigest

class AndroidInstallerInfoExtractor(
    private val context: Context,
) : InstallerInfoExtractor {
    override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? =
        withContext(Dispatchers.IO) {
            try {
                val packageManager = context.packageManager

                val packageInfo = parseApk(packageManager, filePath)

                if (packageInfo == null) {
                    Logger.e {
                        "Failed to parse APK at $filePath, file exists: ${
                            File(filePath).exists()
                        }, size: ${File(filePath).length()}"
                    }
                    return@withContext null
                }

                val appInfo = packageInfo.applicationInfo
                appInfo?.sourceDir = filePath
                appInfo?.publicSourceDir = filePath

                val appName = appInfo?.let { packageManager.getApplicationLabel(it) }.toString()
                val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

                val fingerprint = extractSigningFingerprint(packageManager, packageInfo, filePath)

                ApkPackageInfo(
                    appName = appName,
                    packageName = packageInfo.packageName,
                    versionName = packageInfo.versionName ?: SystemPackageInfo.UNKNOWN_VERSION_NAME,
                    versionCode = versionCode,
                    signingFingerprint = fingerprint,
                )
            } catch (e: SecurityException) {
                Logger.e { "Failed to extract APK info: ${e.message}, file: $filePath" }
                null
            }
        }

    private fun parseApk(
        packageManager: PackageManager,
        filePath: String,
    ): android.content.pm.PackageInfo? =
        getPackageArchiveInfoCompat(packageManager, filePath, fullFlags())
            ?: run {
                Logger.w {
                    "Full-flag parse failed for $filePath, retrying with minimal flags"
                }
                getPackageArchiveInfoCompat(packageManager, filePath, PackageManager.GET_META_DATA)
            }
            ?: run {
                Logger.w {
                    "Minimal-flag parse also failed for $filePath, retrying with zero flags"
                }
                getPackageArchiveInfoCompat(packageManager, filePath, 0)
            }

    private fun fullFlags(): Int {
        val signerFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        return PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES or signerFlag
    }

    private fun getPackageArchiveInfoCompat(
        packageManager: PackageManager,
        filePath: String,
        flags: Int,
    ): android.content.pm.PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                filePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            packageManager.getPackageArchiveInfo(filePath, flags)
        }

    private fun extractSigningFingerprint(
        packageManager: PackageManager,
        packageInfo: android.content.pm.PackageInfo,
        filePath: String,
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return getPackageArchiveInfoCompat(packageManager, filePath, SigningFingerprint.SIGNER_FLAG)
                ?.let(SigningFingerprint::fromPackageInfo)
        }
        return SigningFingerprint.fromPackageInfo(packageInfo)
            ?: reparsedSigningFingerprint(packageManager, filePath)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun reparsedSigningFingerprint(
        packageManager: PackageManager,
        filePath: String,
    ): String? {
        val sigInfo =
            getPackageArchiveInfoCompat(
                packageManager,
                filePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )?.signingInfo ?: return null
        val certs =
            if (sigInfo.hasMultipleSigners()) {
                sigInfo.apkContentsSigners
            } else {
                sigInfo.signingCertificateHistory
            }
        return certs?.firstOrNull()?.toByteArray()?.let(::sha256Fingerprint)
    }

    private fun sha256Fingerprint(certBytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(certBytes)
            .joinToString(":") { "%02X".format(it) }
}
