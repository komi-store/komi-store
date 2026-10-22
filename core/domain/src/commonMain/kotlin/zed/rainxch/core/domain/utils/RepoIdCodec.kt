package zed.rainxch.core.domain.utils

object RepoIdCodec {
    private const val HOST_FINGERPRINT_MASK = 0x7FFFFFL
    private const val RAW_ID_MASK = 0xFFFFFFFFFFL
    private const val HOST_FINGERPRINT_SHIFT = 40

    fun encode(
        host: String?,
        rawId: Long,
    ): Long {
        if (host.isNullOrBlank()) return rawId
        val hostBits =
            (host.lowercase().hashCode().toLong() and HOST_FINGERPRINT_MASK) shl
                HOST_FINGERPRINT_SHIFT
        val idBits = rawId and RAW_ID_MASK
        return -(hostBits or idBits)
    }

    fun isForeignSource(repoId: Long): Boolean = repoId < 0L

    fun sourceHostOf(
        repoId: Long,
        repoUrl: String,
    ): String? {
        if (!isForeignSource(repoId)) return null
        return repoUrl
            .substringAfter("://")
            .substringBefore('/')
            .lowercase()
            .ifBlank { null }
    }
}
