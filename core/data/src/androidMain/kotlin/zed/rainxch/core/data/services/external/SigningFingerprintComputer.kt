package zed.rainxch.core.data.services.external

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import zed.rainxch.core.data.services.SigningFingerprint
import java.security.MessageDigest

object SigningFingerprintComputer {
    fun compute(
        packageManager: PackageManager,
        packageName: String,
    ): String? =
        runCatching {
            val info = loadPackageInfo(packageManager, packageName) ?: return null
            fingerprintOf(info)
        }.getOrNull()

    fun computeFrom(info: PackageInfo): String? =
        runCatching {
            fingerprintOf(info)
        }.getOrNull()

    private fun loadPackageInfo(
        pm: PackageManager,
        packageName: String,
    ): PackageInfo? {
        val flags = SigningFingerprint.SIGNER_FLAG.toLong()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
            } else {
                pm.getPackageInfo(packageName, flags.toInt())
            }
        }.getOrNull()
    }

    private fun fingerprintOf(info: PackageInfo): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return SigningFingerprint.fromPackageInfo(info)
        val sigInfo = info.signingInfo
        val current = sigInfo?.apkContentsSigners?.firstOrNull()
        val fallback = sigInfo?.signingCertificateHistory?.lastOrNull()
        return (current ?: fallback)?.toByteArray()?.let(::sha256Hex)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02X".format(it) }
}
