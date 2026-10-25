package zed.rainxch.konsist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class StabilityRulesTest {
    private val production = appProduction

    private val root: File by lazy {
        val file = production.files.first()
        File(file.path.replace('\\', '/').removeSuffix(file.relativePath))
    }

    private val dumps: List<File> by lazy {
        MODULE_ROOTS
            .map { File(root, it) }
            .flatMap { dir ->
                dir
                    .walkTopDown()
                    .onEnter { it.name != "build" && it.name != "src" && it.name != ".gradle" }
                    .filter { it.isFile && it.extension == "stability" && it.parentFile.name == "stability" }
                    .toList()
            }
    }

    @Test
    fun dumps_are_found() {
        assertTrue(dumps.isNotEmpty(), "No stability dumps found under $root")
    }

    @Test
    fun composables_take_only_stable_parameters_and_skip() {
        val problems =
            dumps.flatMap { dump ->
                val path = dump.relativeTo(root).invariantSeparatorsPath
                composables(dump.readText()).filter { it.restartable }.flatMap { composable ->
                    buildList {
                        composable.unstableParams.forEach { add("$path: ${composable.name} takes unstable $it") }
                        if (!composable.skippable) add("$path: ${composable.name} restarts but can't skip")
                    }
                }
            }
        assertTrue(
            problems.isEmpty(),
            "Map domain and data types to stable UI models in the presentation module instead (docs/agents/checks.md):\n" +
                problems.joinToString("\n"),
        )
    }

    private fun composables(text: String): List<DumpedComposable> =
        text
            .lines()
            .map { it.trim() }
            .fold(mutableListOf<MutableList<String>>()) { entries, line ->
                if (line == "@Composable") entries += mutableListOf<String>() else entries.lastOrNull()?.add(line)
                entries
            }.mapNotNull { lines ->
                val signature = lines.firstOrNull { it.contains(" fun ") } ?: return@mapNotNull null
                DumpedComposable(
                    name = signature.substringAfter(" fun ").substringBefore('('),
                    restartable = "restartable: true" in lines,
                    skippable = "skippable: true" in lines,
                    unstableParams =
                        lines
                            .filter { it.startsWith("- ") && ": UNSTABLE" in it }
                            .map { it.removePrefix("- ").substringBefore(':') }
                            .filterNot { isViewModel(signature, it) },
                )
            }

    // A Root holds its ViewModel by reference, and strong skipping compares it by identity.
    private fun isViewModel(
        signature: String,
        param: String,
    ): Boolean =
        signature
            .substringAfter("($param: ", missingDelimiterValue = signature.substringAfter(", $param: ", ""))
            .substringBefore(',')
            .substringBefore(')')
            .endsWith("ViewModel")

    private data class DumpedComposable(
        val name: String,
        val restartable: Boolean,
        val skippable: Boolean,
        val unstableParams: List<String>,
    )

    private companion object {
        val MODULE_ROOTS = listOf("core", "feature", "composeApp", "androidApp")
    }
}
