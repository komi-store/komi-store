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

    fun isLegacyPartFile(fileName: String): Boolean =
        !isMetaFile(fileName) && LEGACY_PART.containsMatchIn(fileName)

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

    fun assetNameOfMeta(fileName: String): String? =
        if (isMetaFile(fileName)) fileName.removeSuffix(META_SUFFIX) else null
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
            var assetId: Long? = null
            var size: Long? = null
            var digest: String? = null

            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val separator = line.indexOf('=')
                if (separator <= 0) continue
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                when (key) {
                    "assetId" -> assetId = value.toLongOrNull()
                    "size" -> size = value.toLongOrNull()
                    "digest" -> digest = value.takeIf { it.isNotEmpty() }
                }
            }

            val parsedId = assetId ?: return null
            val parsedSize = size ?: return null
            return PartialIdentity(assetId = parsedId, digest = digest, size = parsedSize)
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

        val onDiskDigest = meta.digest
        val wantedDigest = incoming.digest
        if (onDiskDigest != null && wantedDigest != null) {
            return if (onDiskDigest == wantedDigest) PartialReuse.REUSE else PartialReuse.DISCARD
        }

        if (meta.assetId > 0L && incoming.assetId > 0L) {
            return if (meta.assetId == incoming.assetId) PartialReuse.REUSE else PartialReuse.DISCARD
        }

        return if (meta.size == incoming.size) PartialReuse.REUSE else PartialReuse.DISCARD
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
            incoming = PartialIdentity(
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

data class ContentRange(val start: Long?, val total: Long?) {
    companion object {
        fun parse(value: String?): ContentRange? {
            val body = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (!body.substringBefore(' ', "").trim().equals("bytes", ignoreCase = true)) return null
            val spec = body.substringAfter(' ', "").trim()
            if (spec.isEmpty()) return null
            val total = spec.substringAfterLast('/', "").trim().toLongOrNull()
            val range = spec.substringBefore('/').trim()
            val start =
                if (range.startsWith("*")) null
                else range.substringBefore('-').trim().toLongOrNull()
            if (start == null && total == null) return null
            return ContentRange(start = start, total = total)
        }
    }
}

object RangeRequest {

    fun headerValue(partLength: Long): String? =
        if (partLength > 0L) "bytes=$partLength-" else null

    fun decide(
        statusCode: Int,
        partLength: Long,
        contentRange: ContentRange?,
    ): RangeDecision =
        when (statusCode) {
            206 -> if (contentRange?.start == partLength) RangeDecision.APPEND else RangeDecision.RESTART
            416 -> RangeDecision.RESTART
            200 -> if (partLength > 0L) RangeDecision.TRUNCATE_REWRITE else RangeDecision.APPEND
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

        // A complete pair is the only shape that can be resumed, so it survives being
        // unclaimed. Only half-pairs are swept: they can never be attributed again.
        return OrphanVerdict.KEEP
    }
}

object PartialDisposal {

    private val TERMINAL_CLIENT_ERRORS = setOf(404, 410)

    fun shouldDiscardPartial(statusCode: Int?, explicitDiscard: Boolean): Boolean =
        explicitDiscard || (statusCode != null && statusCode in TERMINAL_CLIENT_ERRORS)
}

object PartialRetry {

    const val MAX_ATTEMPTS = 3

    private val BACKOFF_MILLIS = longArrayOf(1_000L, 4_000L, 16_000L)

    fun hasAttemptsLeft(attemptsMade: Int): Boolean = attemptsMade < MAX_ATTEMPTS

    fun backoffMillis(attemptsMade: Int): Long =
        BACKOFF_MILLIS[attemptsMade.coerceIn(0, BACKOFF_MILLIS.lastIndex)]
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
