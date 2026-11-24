package zed.rainxch.details.data.system

import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.details.domain.repository.DetailsRepository
import zed.rainxch.details.domain.system.AttestationVerifier
import zed.rainxch.details.domain.system.VerificationResult
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

class AttestationVerifierImpl(
    private val detailsRepository: DetailsRepository,
    private val logger: KomiStoreLogger,
) : AttestationVerifier {
    override suspend fun verify(
        owner: String,
        repoName: String,
        filePath: String,
    ): VerificationResult =
        try {
            val digest = computeSha256(filePath)
            val hasAttestation = detailsRepository.checkAttestations(owner, repoName, digest)
            if (hasAttestation) VerificationResult.Verified else VerificationResult.Unverified
        } catch (e: IOException) {
            logger.debug("Attestation check error: ${e.message}")
            VerificationResult.Error(e.message ?: "Unknown error")
        }

    private fun computeSha256(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(READ_BUFFER_BYTES)
        FileInputStream(File(filePath)).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val READ_BUFFER_BYTES = 8192
    }
}
