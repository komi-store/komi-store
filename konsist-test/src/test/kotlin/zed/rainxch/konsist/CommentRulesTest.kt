package zed.rainxch.konsist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentRulesTest {
    private val production = appProduction

    private val root: File by lazy {
        val file = production.files.first()
        File(file.path.replace('\\', '/').removeSuffix(file.relativePath))
    }

    private val sources: List<File> by lazy {
        val nested =
            SOURCE_ROOTS.map { File(root, it) }.flatMap { dir ->
                dir
                    .walkTopDown()
                    .onEnter { it.name != "build" && it.name != ".gradle" }
                    .filter { it.isFile && it.extension in KOTLIN_EXTENSIONS }
                    .toList()
            }
        nested + root.listFiles { file -> file.extension == "kts" }.orEmpty()
    }

    @Test
    fun comments_are_one_line_and_never_stacked() {
        val problems =
            sources.flatMap { file ->
                commentProblems(file.readText()).map { "${file.relativeTo(root).invariantSeparatorsPath}:$it" }
            }
        assertTrue(
            problems.isEmpty(),
            "A comment is one line, never stacked with another, and never KDoc (docs/code-style.md):\n" +
                problems.joinToString("\n"),
        )
    }

    @Test
    fun the_scanner_reads_strings_and_templates_as_code() {
        val text =
            listOf(
                "val url = \"https://example.com/\${path(\"a//b\")}\"",
                "val raw = \"\"\"/* not a comment */\"\"\"",
                "// one comment",
                "val c = '/'",
                "// another comment, two lines below the first",
            ).joinToString("\n")
        assertEquals(emptyList(), commentProblems(text))
    }

    @Test
    fun the_scanner_flags_each_broken_shape() {
        val text =
            listOf(
                "// first",
                "// second",
                "val a = 1",
                "/* one",
                "   two */",
                "/** docs */",
            ).joinToString("\n")
        assertEquals(listOf("1: 2 comment lines stacked", "4: block comment spans 2 lines", "6: KDoc"), commentProblems(text))
    }

    private companion object {
        val SOURCE_ROOTS = listOf("core", "feature", "composeApp", "androidApp", "build-logic", "konsist-test")
        val KOTLIN_EXTENSIONS = setOf("kt", "kts")
    }
}
