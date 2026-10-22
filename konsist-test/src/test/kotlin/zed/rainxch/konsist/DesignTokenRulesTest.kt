package zed.rainxch.konsist

import kotlin.test.Test
import kotlin.test.assertTrue

class DesignTokenRulesTest {
    @Test
    fun no_raw_font_sizes() = Rule.RawSp.check()

    @Test
    fun no_raw_font_weights() = Rule.FontWeight.check()

    @Test
    fun no_hand_built_text_styles() = Rule.TextStyle.check()

    @Test
    fun no_material_text_outside_core_presentation() = Rule.MaterialText.check()

    @Test
    fun no_material_typography_outside_core_presentation() = Rule.MaterialTypography.check()

    @Test
    fun no_literal_tween_durations() = Rule.Tween.check()

    @Test
    fun no_literal_alpha_tints() = Rule.Alpha.check()

    @Test
    fun no_color_literals() = Rule.ColorLiteral.check()

    @Test
    fun no_raw_popups_outside_core_presentation() = Rule.Popup.check()

    private enum class Rule(
        val file: String,
        val pattern: Regex,
        val consumersOnly: Boolean,
        val instead: String,
    ) {
        RawSp("raw-sp", Regex("""\b\d+(\.\d+)?\.sp\b"""), false, "a KomiTextRole"),
        FontWeight("font-weight", Regex("""\bFontWeight\."""), false, "a KomiTextRole"),
        TextStyle("text-style", Regex("""\bTextStyle\("""), false, "a KomiTextRole"),
        MaterialText(
            "material-text",
            Regex("""\bimport androidx\.compose\.material3\.Text\b"""),
            true,
            "KomiText",
        ),
        MaterialTypography("material-typography", Regex("""\bMaterialTheme\.typography\b"""), true, "KomiText"),
        Tween("tween", Regex("""\btween\(\s*(durationMillis\s*=\s*)?\d"""), false, "a personality motion token"),
        Alpha("alpha", Regex("""\.copy\(\s*alpha\s*=\s*\d"""), false, "a personality color token"),
        ColorLiteral("color-literal", Regex("""\bColor\(0x"""), false, "a personality color token"),
        Popup("popup", Regex("""\b(Popup|TooltipBox)\("""), true, "a core/presentation component"),
        ;

        fun check() {
            val counts =
                appProduction.files
                    .map { it.relativePath to it.text }
                    .filter { (path, _) -> path.endsWith(".kt") && !path.startsWith(PERSONALITY) }
                    .filter { (path, _) -> !consumersOnly || !path.startsWith(CORE_PRESENTATION) }
                    .associate { (path, text) -> path to pattern.findAll(text).count() }
                    .filterValues { it > 0 }
            val allowed = allowlist()
            val grown = counts.filter { (path, count) -> count > (allowed[path] ?: 0) }
            val shrunk = allowed.filter { (path, count) -> (counts[path] ?: 0) < count }
            assertTrue(
                grown.isEmpty(),
                "Use $instead instead of `${pattern.pattern}`. Grew past $RESOURCES/$file.txt: " +
                    grown.entries.joinToString { (path, count) -> "$path ($count, allowed ${allowed[path] ?: 0})" },
            )
            assertTrue(
                shrunk.isEmpty(),
                "Fewer matches now; lower or remove these lines in $RESOURCES/$file.txt: " +
                    shrunk.entries.joinToString { (path, count) -> "$path ($count -> ${counts[path] ?: 0})" },
            )
        }

        private fun allowlist(): Map<String, Int> =
            DesignTokenRulesTest::class.java
                .getResource("/design-tokens/$file.txt")
                ?.readText()
                ?.lines()
                ?.filter { it.isNotBlank() }
                ?.associate { line ->
                    val (count, path) = line.split(" ", limit = 2)
                    path to count.toInt()
                }.orEmpty()
    }
}

private const val PERSONALITY = "core/presentation/src/commonMain/kotlin/zed/rainxch/core/presentation/personality/"
private const val CORE_PRESENTATION = "core/presentation/"
private const val RESOURCES = "konsist-test/src/test/resources/design-tokens"
