package zed.rainxch.core.domain.network

interface DigestVerifier {
    suspend fun verify(
        filePath: String,
        expectedDigest: String,
    ): String?
}
