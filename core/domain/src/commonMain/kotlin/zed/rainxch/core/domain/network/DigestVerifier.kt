package zed.rainxch.core.domain.network

interface DigestVerifier {
    suspend fun verify(
        filePath: String,
        expectedDigest: String,
    ): String?

    // "sha256:<hex>", or null when the file cannot be read: no evidence, not a mismatch.
    suspend fun computeSha256(filePath: String): String?
}
