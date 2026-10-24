package zed.rainxch.core.domain.diagnostics

object DiagnosticLogRing {
    private const val UTF8_CONTINUATION_MASK = 0xC0
    private const val UTF8_CONTINUATION_PREFIX = 0x80

    fun trimOldest(
        content: String,
        maxBytes: Int,
        targetBytes: Int = maxBytes,
    ): String {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val target = targetBytes.coerceIn(1, maxBytes)
        if (content.encodeToByteArray().size <= maxBytes) return content

        val lines = content.split('\n')
        val kept = ArrayDeque<String>()
        var usedBytes = 0
        for (index in lines.indices.reversed()) {
            val line = lines[index]
            val lineBytes = line.encodeToByteArray().size + 1
            if (usedBytes + lineBytes > target) break
            kept.addFirst(line)
            usedBytes += lineBytes
        }
        return if (kept.isEmpty()) {
            content.takeLastUtf8Bytes(target)
        } else {
            kept.joinToString("\n")
        }
    }

    private fun String.takeLastUtf8Bytes(maxBytes: Int): String {
        val bytes = encodeToByteArray()
        if (bytes.size <= maxBytes) return this
        var start = bytes.size - maxBytes
        while (start < bytes.size &&
            (bytes[start].toInt() and UTF8_CONTINUATION_MASK) == UTF8_CONTINUATION_PREFIX
        ) {
            start++
        }
        return bytes.copyOfRange(start, bytes.size).decodeToString()
    }
}
