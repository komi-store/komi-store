package zed.rainxch.konsist

import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class FileSizeTest {
    private val production = appProduction

    @Test
    fun production_files_stay_under_the_line_limit() {
        production.files.assertTrue { it.text.lines().size <= MAX_LINES }
    }

    private companion object {
        const val MAX_LINES = 800
    }
}
