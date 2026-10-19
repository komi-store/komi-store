package zed.rainxch.konsist

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class LayerRulesTest {
    private val production = appProduction

    @Test
    fun domain_common_code_imports_no_frameworks() {
        production.files
            .filter { it.layer == Layer.Domain && "/src/commonMain/" in it.relativePath }
            .assertFalse { file ->
                file.imports.any { import -> FRAMEWORK_PREFIXES.any { import.name.startsWith(it) } }
            }
    }

    @Test
    fun presentation_modules_never_import_data_packages() {
        production.files
            .filter { it.layer == Layer.Presentation && it.relativePath !in DATA_IMPORT_EXCEPTIONS }
            .assertFalse { file -> file.imports.any { DATA_PACKAGE.matches(it.name) } }
    }

    @Test
    fun packages_follow_owner_and_layer() {
        production.files
            .filter { it.expectedPackagePrefix != null && it.relativePath !in PACKAGE_EXCEPTIONS }
            .assertTrue { file ->
                val name = file.packagee?.name ?: return@assertTrue false
                val prefix = file.expectedPackagePrefix!!
                name == prefix || name.startsWith("$prefix.")
            }
    }

    private companion object {
        val FRAMEWORK_PREFIXES =
            listOf(
                "io.ktor.",
                "androidx.room.",
                "androidx.compose.",
                "androidx.lifecycle.",
                "android.",
                "org.koin.",
            )

        val DATA_PACKAGE = Regex("""^zed\.rainxch\.[a-z]+\.data(\..+)?$""")

        val DATA_IMPORT_EXCEPTIONS =
            setOf(
                "feature/tweaks/presentation/src/commonMain/kotlin/zed/rainxch/tweaks/presentation/mirror/MirrorPickerViewModel.kt",
            )

        val PACKAGE_EXCEPTIONS =
            listOf(
                "model/ExploreResult.kt",
                "model/ProgrammingLanguage.kt",
                "model/SearchSource.kt",
                "model/SortBy.kt",
                "model/SortOrder.kt",
                "repository/SearchHistoryRepository.kt",
                "repository/SearchRepository.kt",
            ).map { "feature/search/domain/src/commonMain/kotlin/zed/rainxch/domain/$it" }
                .toSet()
    }
}
