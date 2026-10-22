package zed.rainxch.auth.data.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object PkceGenerator {
    private val secureRandom = SecureRandom()
    private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun generate(): PkceTriplet {
        val stateBytes = ByteArray(STATE_BYTES).also { secureRandom.nextBytes(it) }
        val verifierBytes = ByteArray(VERIFIER_BYTES).also { secureRandom.nextBytes(it) }

        val state = b64.encode(stateBytes)
        val codeVerifier = b64.encode(verifierBytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.encodeToByteArray())
        val codeChallenge = b64.encode(digest)

        return PkceTriplet(
            state = state,
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge,
        )
    }

    private const val STATE_BYTES = 32
    private const val VERIFIER_BYTES = 48
}
