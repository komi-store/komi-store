package zed.rainxch.core.domain.network

interface DigestVerifier {

    suspend fun verify(
        filePath: String,
        expectedDigest: String,
    ): String?

    // The file's own sha256, spelled the way digests travel ("sha256:<hex>"); null when the
    // file cannot be read — "no evidence" is not the same answer as "does not match".
    suspend fun computeSha256(filePath: String): String?
}
