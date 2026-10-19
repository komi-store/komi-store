package zed.rainxch.details.presentation.utils

fun truncateMarkdownPreview(
    content: String,
    maxChars: Int,
): String {
    if (content.length <= maxChars) return content
    val window = content.substring(0, maxChars)

    val searchFrom = (maxChars * 0.75).toInt().coerceAtLeast(0)
    val paragraphBreak = window.lastIndexOf("\n\n", maxChars).takeIf { it >= searchFrom }
    if (paragraphBreak != null && paragraphBreak > 0) {
        return window.substring(0, paragraphBreak).trimEnd() + "\n"
    }
    val newline = window.lastIndexOf('\n', maxChars).takeIf { it >= searchFrom }
    if (newline != null && newline > 0) {
        return window.substring(0, newline).trimEnd() + "\n"
    }
    return window.trimEnd() + "…"
}

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
