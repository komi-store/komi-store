package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeAwareImagesTest {
    @Test
    fun markdown_images_keep_only_the_current_theme() {
        val content = "![dark](https://x/d.png#gh-dark-mode-only) ![light](https://x/l.png#gh-light-mode-only?raw=1)"

        assertEquals("![dark](https://x/d.png) ", applyThemeAwareImages(content, isDark = true))
        assertEquals(" ![light](https://x/l.png?raw=1)", applyThemeAwareImages(content, isDark = false))
    }

    @Test
    fun html_images_keep_their_attributes_and_quotes() {
        val dark = """<img alt="x" src="https://x/d.png#gh-dark-mode-only" width="50">"""
        val light = "<img src='https://x/l.png#gh-light-mode-only'>"

        assertEquals("""<img alt="x" src="https://x/d.png" width="50">""", applyThemeAwareImages(dark, isDark = true))
        assertEquals("", applyThemeAwareImages(dark, isDark = false))
        assertEquals("<img src='https://x/l.png'>", applyThemeAwareImages(light, isDark = false))
        assertEquals("", applyThemeAwareImages(light, isDark = true))
    }

    @Test
    fun images_without_a_theme_marker_are_left_alone() {
        val content = """![plain](https://x/p.png) <img src="https://x/p.png">"""

        assertEquals(content, applyThemeAwareImages(content, isDark = true))
    }
}
