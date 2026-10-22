package zed.rainxch.apps.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionLineTest {
    @Test
    fun a_normal_update_points_at_the_stores_latest() {
        val line =
            resolveVersionLine(
                installedVersion = "v0.9.0",
                isPendingInstall = false,
                pendingInstallVersion = null,
                latestVersion = "v0.9.1",
            )

        assertEquals("v0.9.1", line.targetVersion)
        assertFalse(line.isDowngrade)
    }

    @Test
    fun a_pending_install_that_goes_backwards_is_flagged() {
        val line =
            resolveVersionLine(
                installedVersion = "v0.9.0",
                isPendingInstall = true,
                pendingInstallVersion = "v0.8.5",
                latestVersion = "v0.9.1",
            )

        assertEquals("v0.8.5", line.targetVersion)
        assertTrue(line.isDowngrade)
    }

    @Test
    fun a_pending_install_that_goes_forwards_is_not_flagged() {
        val line =
            resolveVersionLine(
                installedVersion = "v0.9.0",
                isPendingInstall = true,
                pendingInstallVersion = "v0.9.1",
                latestVersion = "v0.9.1",
            )

        assertEquals("v0.9.1", line.targetVersion)
        assertFalse(line.isDowngrade)
    }

    @Test
    fun a_row_with_no_target_shows_only_its_installed_version() {
        val line =
            resolveVersionLine(
                installedVersion = "v0.9.0",
                isPendingInstall = false,
                pendingInstallVersion = null,
                latestVersion = null,
            )

        assertNull(line.targetVersion)
        assertFalse(line.isDowngrade)
    }

    @Test
    fun a_pending_install_with_no_recorded_version_does_not_fall_back_to_latest() {
        val line =
            resolveVersionLine(
                installedVersion = "v0.9.0",
                isPendingInstall = true,
                pendingInstallVersion = null,
                latestVersion = "v0.9.1",
            )

        assertNull(line.targetVersion)
        assertFalse(line.isDowngrade)
    }

    @Test
    fun a_blank_target_is_treated_as_none() {
        val line =
            resolveVersionLine(
                installedVersion = "v0.9.0",
                isPendingInstall = true,
                pendingInstallVersion = "  ",
                latestVersion = null,
            )

        assertNull(line.targetVersion)
    }
}
