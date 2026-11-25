package zed.rainxch.search.data.utils

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallerAssetsTest {
    @Test
    fun an_asset_matches_its_platform_whatever_the_case() {
        assertTrue(assetMatchesPlatform("App-1.2.APK", DiscoveryPlatform.Android))
        assertTrue(assetMatchesPlatform("setup.msi", DiscoveryPlatform.Windows))
        assertTrue(assetMatchesPlatform("app.pkg", DiscoveryPlatform.Macos))
        assertTrue(assetMatchesPlatform("app-x86_64.pkg.tar.zst", DiscoveryPlatform.Linux))
        assertTrue(assetMatchesPlatform("app.ipa", DiscoveryPlatform.Ios))
    }

    @Test
    fun an_asset_for_another_platform_does_not_match() {
        assertFalse(assetMatchesPlatform("app.apk", DiscoveryPlatform.Windows))
        assertFalse(assetMatchesPlatform("app-x86_64.pkg.tar.zst", DiscoveryPlatform.Macos))
    }

    @Test
    fun all_platforms_match_any_installer_but_not_an_archive() {
        assertTrue(assetMatchesPlatform("app.AppImage", DiscoveryPlatform.All))
        assertTrue(assetMatchesPlatform("app.dmg", DiscoveryPlatform.All))
        assertFalse(assetMatchesPlatform("source.zip", DiscoveryPlatform.All))
    }

    @Test
    fun available_platforms_follow_the_platform_order_and_leave_out_all() {
        val platforms = detectAvailablePlatforms(listOf("app.dmg", "app.apk", "notes.txt"))

        assertEquals(listOf(DiscoveryPlatform.Android, DiscoveryPlatform.Macos), platforms)
    }
}
