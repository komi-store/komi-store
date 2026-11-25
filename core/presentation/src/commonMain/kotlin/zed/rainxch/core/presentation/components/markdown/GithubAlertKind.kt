package zed.rainxch.core.presentation.components.markdown

enum class GithubAlertKind(
    val token: String,
) {
    NOTE("NOTE"),
    TIP("TIP"),
    IMPORTANT("IMPORTANT"),
    WARNING("WARNING"),
    CAUTION("CAUTION"),
    ;

    companion object {
        private val PATTERN = Regex("""^\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*$""")

        fun parse(blockquoteText: String): Match? {
            val stripped =
                blockquoteText
                    .lineSequence()
                    .map { line -> line.trimStart().removePrefix(">").trimStart() }
                    .toList()
            val token =
                stripped
                    .firstOrNull()
                    ?.let { PATTERN.matchEntire(it) }
                    ?.groupValues
                    ?.get(1)
            val kind = entries.firstOrNull { it.token == token } ?: return null
            return Match(kind, stripped.drop(1).joinToString("\n").trim())
        }
    }

    data class Match(
        val kind: GithubAlertKind,
        val body: String,
    )
}
