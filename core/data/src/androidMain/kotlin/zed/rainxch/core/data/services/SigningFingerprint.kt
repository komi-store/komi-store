package zed.rainxch.core.data.services

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

internal object SigningFingerprint {
    // API 26-27 have no signer flag or field but the deprecated GET_SIGNATURES and PackageInfo.signatures.
    val SIGNER_FLAG: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    fun fromPackageInfo(info: PackageInfo): String? {
        val cert =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.let { sigInfo ->
                    if (sigInfo.hasMultipleSigners()) {
                        sigInfo.apkContentsSigners?.firstOrNull()
                    } else {
                        sigInfo.signingCertificateHistory?.lastOrNull()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()
            }
        return cert?.toByteArray()?.let(::sha256)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02X".format(it) }
}
