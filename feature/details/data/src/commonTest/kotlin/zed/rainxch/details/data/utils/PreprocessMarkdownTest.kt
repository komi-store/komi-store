package zed.rainxch.details.data.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class PreprocessMarkdownTest {
    private val baseUrl = "https://raw.githubusercontent.com/komi-store/komi-store/main/"

    @Test
    fun an_html_table_becomes_plain_blocks_without_code_indentation() {
        val readme =
            """
            <table align="center">
              <tr>
                <td align="center">
                  <a href="https://github.com/rainxchzed"><b>rainxchzed</b></a>
                  <br />
                  <img src="https://img.shields.io/badge/Creator-3D4654?style=flat-square" alt="Creator" />
                </td>
              </tr>
            </table>
            """.trimIndent()

        assertEquals(
            "[**rainxchzed**](https://github.com/rainxchzed)\n\n**Creator**",
            preprocessMarkdown(readme, baseUrl),
        )
    }

    @Test
    fun indented_code_outside_a_table_keeps_its_indentation() {
        val readme = "Run it:\n\n    ./gradlew run"

        assertEquals(readme, preprocessMarkdown(readme, baseUrl))
    }
}
