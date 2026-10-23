package zed.rainxch.core.data.services

import android.content.pm.PackageInfo
import android.os.Build
import java.security.MessageDigest

internal object SigningFingerprint {
    fun fromPackageInfo(info: PackageInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sigInfo = info.signingInfo ?: return null
            val cert =
                if (sigInfo.hasMultipleSigners()) {
                    sigInfo.apkContentsSigners?.firstOrNull()
                } else {
                    sigInfo.signingCertificateHistory?.lastOrNull()
                }
            return cert?.toByteArray()?.let(::sha256)
        }
        @Suppress("DEPRECATION")
        return info.signatures
            ?.firstOrNull()
            ?.toByteArray()
            ?.let(::sha256)
    }

    // Inverse of sha256 below; null when the text is not a SHA-256 fingerprint.
    fun toDigest(fingerprint: String): ByteArray? {
        val hex = fingerprint.trim().replace(":", "")
        val isSha256 = hex.length == SHA256_HEX_LENGTH && hex.all { it.digitToIntOrNull(HEX_RADIX) != null }
        return if (isSha256) hex.chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray() else null
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02X".format(it) }

    private const val SHA256_HEX_LENGTH = 64
    private const val HEX_RADIX = 16
}
