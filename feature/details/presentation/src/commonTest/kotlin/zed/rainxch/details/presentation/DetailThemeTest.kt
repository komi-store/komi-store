package zed.rainxch.details.presentation

import zed.rainxch.core.presentation.locals.resolveDetailDarkTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailThemeTest {
    @Test
    fun the_app_theme_decides_while_the_setting_is_on() {
        assertTrue(
            resolveDetailDarkTheme(
                followAppTheme = true,
                appDarkTheme = true,
                systemDarkTheme = false,
            ),
        )
        assertFalse(
            resolveDetailDarkTheme(
                followAppTheme = true,
                appDarkTheme = false,
                systemDarkTheme = true,
            ),
        )
    }

    @Test
    fun the_system_dark_state_decides_while_the_setting_is_off() {
        assertTrue(
            resolveDetailDarkTheme(
                followAppTheme = false,
                appDarkTheme = false,
                systemDarkTheme = true,
            ),
        )
        assertFalse(
            resolveDetailDarkTheme(
                followAppTheme = false,
                appDarkTheme = true,
                systemDarkTheme = false,
            ),
        )
    }
}
