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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02X".format(it) }
}
