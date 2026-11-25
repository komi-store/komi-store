package zed.rainxch.details.presentation.utils

fun truncateMarkdownPreview(
    content: String,
    maxChars: Int,
): String {
    if (content.length <= maxChars) return content
    val window = content.substring(0, maxChars)
    val searchFrom = (maxChars * BREAK_SEARCH_FRACTION).toInt().coerceAtLeast(0)
    val breakAt =
        window.lastIndexOf("\n\n", maxChars).takeIf { it >= searchFrom && it > 0 }
            ?: window.lastIndexOf('\n', maxChars).takeIf { it >= searchFrom && it > 0 }
    return if (breakAt != null) window.substring(0, breakAt).trimEnd() + "\n" else window.trimEnd() + "…"
}

// A paragraph or line break only counts in the window's last quarter, so the preview isn't cut far short.
private const val BREAK_SEARCH_FRACTION = 0.75

fun splitMarkdownIntoChunks(
    content: String,
    targetChunkChars: Int,
): List<String> {
    if (content.length <= targetChunkChars) return listOf(content)
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    var inFence = false
    val lines = content.split('\n')

    fun flush() {
        if (current.isNotEmpty()) {
            chunks += current.toString()
            current.clear()
        }
    }
    for (line in lines) {
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
        }
        if (current.isNotEmpty()) current.append('\n')
        current.append(line)
        if (!inFence &&
            current.length >= targetChunkChars &&
            line.isBlank()
        ) {
            flush()
        }
    }
    flush()
    return chunks
}
