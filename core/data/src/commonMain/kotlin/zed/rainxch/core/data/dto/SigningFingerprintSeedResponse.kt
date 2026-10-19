package zed.rainxch.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SigningFingerprintSeedResponse(
    val rows: List<Row>,
    val nextCursor: String? = null,
) {
    @Serializable
    data class Row(
        val fingerprint: String,
        val owner: String,
        val repo: String,
        val observedAt: Long,
    )
}
