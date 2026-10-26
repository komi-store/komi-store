package zed.rainxch.core.data.download

import zed.rainxch.core.domain.network.AssetIdentity

object PartialNaming {
    const val PART_SUFFIX = ".part"
    const val META_SUFFIX = ".part.meta"

    private val LEGACY_PART = Regex("""\.part-[0-9A-Za-z-]+$""")

    fun part(safeName: String): String = "$safeName$PART_SUFFIX"

    fun meta(safeName: String): String = "$safeName$META_SUFFIX"

    fun isPartFile(fileName: String): Boolean =
        !isMetaFile(fileName) &&
            (fileName.endsWith(PART_SUFFIX) || LEGACY_PART.containsMatchIn(fileName))

    fun isMetaFile(fileName: String): Boolean = fileName.endsWith(META_SUFFIX)

    fun isLegacyPartFile(fileName: String): Boolean = !isMetaFile(fileName) && LEGACY_PART.containsMatchIn(fileName)

    fun assetNameOfPart(fileName: String): String? {
        if (isMetaFile(fileName)) return null
        val base =
            if (fileName.endsWith(PART_SUFFIX)) {
                fileName.removeSuffix(PART_SUFFIX)
            } else {
                fileName.replace(LEGACY_PART, "")
            }
        return base.takeIf { it.isNotEmpty() && it != fileName }
    }

    fun assetNameOfMeta(fileName: String): String? = if (isMetaFile(fileName)) fileName.removeSuffix(META_SUFFIX) else null
}

data class PartialIdentity(
    val assetId: Long,
    val digest: String?,
    val size: Long,
) {
    fun serialize(): String =
        buildString {
            append("assetId=").append(assetId).append('\n')
            append("digest=").append(digest.orEmpty()).append('\n')
            append("size=").append(size).append('\n')
        }

    companion object {
        fun parse(text: String): PartialIdentity? {
            val fields =
                text
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.indexOf('=') > 0 }
                    .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            val assetId = fields["assetId"]?.toLongOrNull()
            val size = fields["size"]?.toLongOrNull()
            return if (assetId == null || size == null) {
                null
            } else {
                PartialIdentity(assetId = assetId, digest = fields["digest"]?.takeIf { it.isNotEmpty() }, size = size)
            }
        }
    }
}

enum class PartialReuse {
    REUSE,

    DISCARD,
}

object PartialOwnership {
    fun decideReuse(
        partExists: Boolean,
        partLength: Long,
        meta: PartialIdentity?,
        incoming: PartialIdentity,
    ): PartialReuse {
        if (!partExists || partLength <= 0L) return PartialReuse.DISCARD
        if (meta == null) return PartialReuse.DISCARD

        val sameAsset =
            when {
                meta.digest != null && incoming.digest != null -> meta.digest == incoming.digest
                meta.assetId > 0L && incoming.assetId > 0L -> meta.assetId == incoming.assetId
                else -> meta.size == incoming.size
            }
        return if (sameAsset) PartialReuse.REUSE else PartialReuse.DISCARD
    }

    fun decideReuse(
        partExists: Boolean,
        partLength: Long,
        meta: PartialIdentity?,
        incoming: AssetIdentity?,
    ): PartialReuse {
        if (incoming == null) return PartialReuse.DISCARD
        return decideReuse(
            partExists = partExists,
            partLength = partLength,
            meta = meta,
            incoming =
                PartialIdentity(
                    assetId = incoming.assetId,
                    digest = incoming.digest,
                    size = incoming.size,
                ),
        )
    }
}

enum class RangeDecision {
    APPEND,

    TRUNCATE_REWRITE,

    RESTART,

    UNEXPECTED,
}

data class ContentRange(
    val start: Long?,
    val total: Long?,
) {
    companion object {
        fun parse(value: String?): ContentRange? {
            val body = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (!body.substringBefore(' ', "").trim().equals("bytes", ignoreCase = true)) return null
            val spec = body.substringAfter(' ', "").trim().takeIf { it.isNotEmpty() } ?: return null
            val total = spec.substringAfterLast('/', "").trim().toLongOrNull()
            val range = spec.substringBefore('/').trim()
            val start =
                if (range.startsWith("*")) {
                    null
                } else {
                    range.substringBefore('-').trim().toLongOrNull()
                }
            return if (start == null && total == null) null else ContentRange(start = start, total = total)
        }
    }
}

object RangeRequest {
    private const val HTTP_OK = 200
    private const val HTTP_PARTIAL_CONTENT = 206
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416

    fun headerValue(partLength: Long): String? = if (partLength > 0L) "bytes=$partLength-" else null

    fun decide(
        statusCode: Int,
        partLength: Long,
        contentRange: ContentRange?,
    ): RangeDecision =
        when (statusCode) {
            HTTP_PARTIAL_CONTENT -> if (contentRange?.start == partLength) RangeDecision.APPEND else RangeDecision.RESTART
            HTTP_RANGE_NOT_SATISFIABLE -> RangeDecision.RESTART
            HTTP_OK -> if (partLength > 0L) RangeDecision.TRUNCATE_REWRITE else RangeDecision.APPEND
            else -> RangeDecision.UNEXPECTED
        }
}

enum class OrphanVerdict {
    KEEP,

    DROP_PART_ONLY,

    DROP_META_ONLY,
}

object OrphanPolicy {
    fun classify(
        partExists: Boolean,
        metaExists: Boolean,
        isClaimed: Boolean,
    ): OrphanVerdict {
        if (!partExists && !metaExists) return OrphanVerdict.KEEP
        if (isClaimed) return OrphanVerdict.KEEP

        if (partExists && !metaExists) return OrphanVerdict.DROP_PART_ONLY
        if (!partExists) return OrphanVerdict.DROP_META_ONLY

        return OrphanVerdict.KEEP
    }
}

object PartialDisposal {
    private val TERMINAL_CLIENT_ERRORS = setOf(404, 410)

    fun shouldDiscardPartial(
        statusCode: Int?,
        explicitDiscard: Boolean,
    ): Boolean = explicitDiscard || (statusCode != null && statusCode in TERMINAL_CLIENT_ERRORS)
}

object PartialRetry {
    const val MAX_ATTEMPTS = 3

    private val BACKOFF_MILLIS = longArrayOf(1_000L, 4_000L, 16_000L)

    fun hasAttemptsLeft(attemptsMade: Int): Boolean = attemptsMade < MAX_ATTEMPTS

    fun backoffMillis(attemptsMade: Int): Long = BACKOFF_MILLIS[attemptsMade.coerceIn(0, BACKOFF_MILLIS.lastIndex)]
}

object FinishedFileReuse {
    fun digestToVerify(
        destinationExists: Boolean,
        destinationLength: Long,
        expectedDigest: String?,
        expectedSize: Long,
    ): String? {
        if (!destinationExists) return null
        if (expectedDigest.isNullOrBlank()) return null
        if (expectedSize > 0L && destinationLength != expectedSize) return null
        return expectedDigest
    }
}
