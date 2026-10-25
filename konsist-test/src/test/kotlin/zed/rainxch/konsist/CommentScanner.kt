package zed.rainxch.konsist

internal data class SourceComment(
    val startLine: Int,
    val endLine: Int,
    val isBlock: Boolean,
    val isKdoc: Boolean,
    val isCommentOnlyLine: Boolean,
)

internal fun commentProblems(text: String): List<String> {
    val comments = CommentScanner(text).scan()
    val problems = mutableListOf<Pair<Int, String>>()
    comments.filter { it.isKdoc }.forEach { problems += it.startLine to "KDoc" }
    comments
        .filter { it.isBlock && !it.isKdoc && it.endLine > it.startLine }
        .forEach { problems += it.startLine to "block comment spans ${it.endLine - it.startLine + 1} lines" }
    val commentOnlyLines = comments.filter { it.isCommentOnlyLine && it.startLine == it.endLine }.map { it.startLine }.sorted()
    stackedRuns(commentOnlyLines).forEach { (start, end) -> problems += start to "${end - start + 1} comment lines stacked" }
    return problems.sortedBy { it.first }.map { (line, what) -> "$line: $what" }
}

private fun stackedRuns(lines: List<Int>): List<Pair<Int, Int>> {
    val runs = mutableListOf<IntArray>()
    lines.forEach { line ->
        val last = runs.lastOrNull()
        if (last != null && line == last[1] + 1) last[1] = line else runs += intArrayOf(line, line)
    }
    return runs.filter { it[1] > it[0] }.map { it[0] to it[1] }
}

private class CommentScanner(
    private val text: String,
) {
    private var index = 0
    private var line = 1
    private val comments = mutableListOf<SourceComment>()

    fun scan(): List<SourceComment> {
        code(insideTemplate = false)
        return comments
    }

    private fun code(insideTemplate: Boolean) {
        var depth = 0
        while (index < text.length) {
            when {
                text.startsWith("//", index) -> lineComment()
                text.startsWith("/*", index) -> blockComment()
                text.startsWith("\"\"\"", index) -> rawString()
                text[index] == '"' -> string()
                text[index] == '\'' -> charLiteral()
                insideTemplate && text[index] == '{' -> {
                    depth++
                    advance()
                }

                insideTemplate && text[index] == '}' -> {
                    advance()
                    if (depth == 0) return
                    depth--
                }

                else -> {
                    advance()
                }
            }
        }
    }

    private fun advance() {
        if (text[index] == '\n') line++
        index++
    }

    private fun template() {
        advance()
        advance()
        code(insideTemplate = true)
    }

    private fun string() {
        advance()
        while (index < text.length && text[index] != '"' && text[index] != '\n') {
            when {
                text[index] == '\\' -> repeat(2) { if (index < text.length) advance() }
                text.startsWith("\${", index) -> template()
                else -> advance()
            }
        }
        if (index < text.length) advance()
    }

    private fun rawString() {
        repeat(3) { advance() }
        while (index < text.length && !text.startsWith("\"\"\"", index)) {
            if (text.startsWith("\${", index)) template() else advance()
        }
        while (index < text.length && text[index] == '"') advance()
    }

    private fun charLiteral() {
        advance()
        while (index < text.length && text[index] != '\'' && text[index] != '\n') {
            if (text[index] == '\\') advance()
            if (index < text.length) advance()
        }
        if (index < text.length) advance()
    }

    private fun lineComment() {
        val start = index
        while (index < text.length && text[index] != '\n') index++
        comments += SourceComment(line, line, isBlock = false, isKdoc = false, isCommentOnlyLine = blankBefore(start))
    }

    private fun blockComment() {
        val start = index
        val startLine = line
        val kdoc = text.startsWith("/**", index) && !text.startsWith("/**/", index)
        var depth = 0
        do {
            when {
                text.startsWith("/*", index) -> {
                    depth++
                    repeat(2) { advance() }
                }

                text.startsWith("*/", index) -> {
                    depth--
                    repeat(2) { advance() }
                }

                else -> {
                    advance()
                }
            }
        } while (depth > 0 && index < text.length)
        comments += SourceComment(startLine, line, isBlock = true, isKdoc = kdoc, isCommentOnlyLine = blankBefore(start))
    }

    private fun blankBefore(position: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', position - 1) + 1
        return text.substring(lineStart, position).isBlank()
    }
}
