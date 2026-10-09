package zed.rainxch.apps.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The version line's "to" side for the three paths it can take. The string assembly around it is
// trivial; which version it points at, and whether that is backwards, is the decision.
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

        // The parked file's version wins over the store's latest: that is the whole point.
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
        // The store's latest is not what the parked file carries; guessing would misreport the
        // direction, so there is simply no target.
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
