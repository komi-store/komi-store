package zed.rainxch.konsist

import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class FileSizeTest {
    private val production = appProduction

    @Test
    fun production_files_stay_under_the_line_limit() {
        production.files
            .filter { it.relativePath !in OVERSIZED }
            .assertTrue { it.text.lines().size <= MAX_LINES }
    }

    @Test
    fun oversized_allowlist_only_shrinks() {
        val stillOversized =
            production.files
                .filter { it.text.lines().size > MAX_LINES }
                .map { it.relativePath }
                .toSet()
        val stale = OVERSIZED - stillOversized
        assertTrue(stale.isEmpty(), "Now within $MAX_LINES lines or gone, remove from OVERSIZED: $stale")
    }

    private companion object {
        const val MAX_LINES = 800

        val OVERSIZED =
            setOf(
                "feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/DetailsViewModel.kt",
                "feature/apps/presentation/src/commonMain/kotlin/zed/rainxch/apps/presentation/AppsViewModel.kt",
                "feature/tweaks/presentation/src/commonMain/kotlin/zed/rainxch/tweaks/presentation/TweaksViewModel.kt",
                "core/data/src/jvmMain/kotlin/zed/rainxch/core/data/services/DesktopInstaller.kt",
                "feature/search/presentation/src/commonMain/kotlin/zed/rainxch/search/presentation/SearchRoot.kt",
                "composeApp/src/commonMain/kotlin/zed/rainxch/githubstore/app/navigation/AppNavigation.kt",
                "feature/search/presentation/src/commonMain/kotlin/zed/rainxch/search/presentation/SearchViewModel.kt",
                "feature/apps/presentation/src/commonMain/kotlin/zed/rainxch/apps/presentation/components/LinkAppBottomSheet.kt",
                "feature/details/data/src/commonMain/kotlin/zed/rainxch/details/data/repository/DetailsRepositoryImpl.kt",
                "feature/apps/presentation/src/commonMain/kotlin/zed/rainxch/apps/presentation/import/ExternalImportViewModel.kt",
                "feature/auth/presentation/src/commonMain/kotlin/zed/rainxch/auth/presentation/AuthenticationViewModel.kt",
                "feature/auth/presentation/src/commonMain/kotlin/zed/rainxch/auth/presentation/AuthenticationRoot.kt",
                "feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/DetailsRoot.kt",
                "core/data/src/commonMain/kotlin/zed/rainxch/core/data/services/DefaultDownloadOrchestrator.kt",
            )
    }
}
