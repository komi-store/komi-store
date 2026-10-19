package zed.rainxch.details.domain.system

interface AttestationVerifier {
    suspend fun verify(
        owner: String,
        repoName: String,
        filePath: String,
    ): VerificationResult
}
