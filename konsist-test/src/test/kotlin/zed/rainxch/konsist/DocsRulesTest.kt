package zed.rainxch.konsist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DocsRulesTest {
    private val production = appProduction

    private val root: File by lazy {
        val file = production.files.first()
        File(file.path.replace('\\', '/').removeSuffix(file.relativePath))
    }

    private val docs: List<File> by lazy {
        buildList {
            listOf("AGENTS.md", "CLAUDE.md", "CONTRIBUTING.md").map { File(root, it) }.filter(File::isFile).forEach(::add)
            File(root, "docs/agents").listFiles { f -> f.extension == "md" }?.sortedBy { it.name }?.let(::addAll)
            File(root, "feature")
                .listFiles()
                ?.map { File(it, "CLAUDE.md") }
                ?.filter(File::isFile)
                ?.sortedBy { it.path }
                ?.let(::addAll)
        }
    }

    @Test
    fun docs_are_found() {
        assertTrue(docs.any { it.name == "AGENTS.md" }, "AGENTS.md not found under $root")
    }

    @Test
    fun routes_named_in_docs_exist_in_the_graph() {
        val routes =
            (production.classes(includeNested = true) + production.objects(includeNested = true))
                .filter { it.fullyQualifiedName.orEmpty().contains(".GithubStoreGraph.") }
                .map { it.name }
                .toSet()
        val missing =
            docs.flatMap { doc ->
                QUALIFIED_ROUTE
                    .findAll(
                        doc.readText(),
                    ).map { it.groupValues[1] }
                    .filterNot { it in routes }
                    .map { "${doc.relativeTo(root)}: $it" }
            }
        assertTrue(missing.isEmpty(), "Docs name routes that GithubStoreGraph doesn't have:\n${missing.joinToString("\n")}")
    }

    @Test
    fun screens_named_in_docs_exist_in_code() {
        val declared =
            buildSet {
                production.classes(includeNested = true).forEach { add(it.name) }
                production.objects(includeNested = true).forEach { add(it.name) }
                production.interfaces(includeNested = true).forEach { add(it.name) }
                production.functions(includeNested = true).forEach { add(it.name) }
            }
        val missing =
            docs.flatMap { doc ->
                SCREEN_NAME
                    .findAll(doc.readText())
                    .map { it.groupValues[1] }
                    .filterNot { it in declared || it in PLACEHOLDERS }
                    .map { "${doc.relativeTo(root)}: $it" }
            }
        assertTrue(missing.isEmpty(), "Docs name screens or graphs that no longer exist:\n${missing.joinToString("\n")}")
    }

    private companion object {
        val QUALIFIED_ROUTE = Regex("""`GithubStoreGraph\.([A-Z][A-Za-z0-9]*)""")
        val SCREEN_NAME = Regex("""`([A-Z][A-Za-z0-9]*(?:Screen|Graph))\b""")
        val PLACEHOLDERS = setOf("FooScreen")
    }
}
