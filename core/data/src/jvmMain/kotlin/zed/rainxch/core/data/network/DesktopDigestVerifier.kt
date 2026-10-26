package zed.rainxch.core.data.network

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.network.DigestVerifier
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class DesktopDigestVerifier : DigestVerifier {
    override suspend fun verify(
        filePath: String,
        expectedDigest: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext "File not found: $filePath"
            val expected = expectedDigest.removePrefix("sha256:").lowercase()
            val actual =
                try {
                    sha256Hex(file)
                } catch (e: IOException) {
                    return@withContext "Digest computation failed: ${e.message}"
                }
            if (actual == expected) null else "Digest mismatch (expected $expected, got $actual)"
        }

    override suspend fun computeSha256(filePath: String): String? =
        withContext(Dispatchers.IO) {
            try {
                "sha256:" + sha256Hex(File(filePath))
            } catch (e: IOException) {
                Logger.w(e) { "Could not hash $filePath" }
                null
            }
        }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
