package zed.rainxch.core.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.network.DigestVerifier
import java.io.File
import java.security.MessageDigest

class AndroidDigestVerifier : DigestVerifier {
    override suspend fun verify(
        filePath: String,
        expectedDigest: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext "File not found: $filePath"
            val expected = expectedDigest.removePrefix("sha256:").lowercase()
            val actual =
                runCatching {
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { stream ->
                        val buf = ByteArray(8 * 1024)
                        while (true) {
                            val read = stream.read(buf)
                            if (read <= 0) break
                            digest.update(buf, 0, read)
                        }
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }.getOrElse { return@withContext "Digest computation failed: ${it.message}" }
            if (actual == expected) null else "Digest mismatch (expected $expected, got $actual)"
        }

    override suspend fun computeSha256(filePath: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                File(filePath).inputStream().use { stream ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val read = stream.read(buf)
                        if (read <= 0) break
                        digest.update(buf, 0, read)
                    }
                }
                "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }
}
