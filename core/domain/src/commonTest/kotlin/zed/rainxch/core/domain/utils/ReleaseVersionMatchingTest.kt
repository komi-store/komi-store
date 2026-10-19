package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersionMatchingTest {
    private fun asset(name: String) =
        GithubAsset(
            id = name.hashCode().toLong(),
            name = name,
            contentType = "application/vnd.android.package-archive",
            size = 1L,
            downloadUrl = "https://dl/$name",
        )

    @Test
    fun tagVersionDropsTheAppPrefix() {
        assertEquals("4.4.25", AssetVariant.tagVersion("auth-v4.4.25"))
        assertEquals("1.13.1", AssetVariant.tagVersion("v1.13.1"))
        assertEquals("26.10.b4a8da2", AssetVariant.tagVersion("26.10.b4a8da2"))
        assertEquals("4.7.2-stable", AssetVariant.tagVersion("4.7.2-stable"))
        assertEquals("24_0b2", AssetVariant.tagVersion("THUNDERBIRD_24_0b2"))
        assertNull(AssetVariant.tagVersion("nightly"))
        assertNull(AssetVariant.tagVersion("v2"))
    }

    @Test
    fun aHashAfterTheVersionDotIsPartOfTheVersion() {
        assertEquals(
            "installerx-revived-online-*.apk",
            AssetVariant.tagGlob("InstallerX-Revived-online-26.10.b4a8da2.apk", "26.10.b4a8da2"),
        )
        assertEquals(
            "installerx-revived-online-*.apk",
            AssetVariant.tagGlob("InstallerX-Revived-online-26.09.apk", "26.09"),
        )
    }

    @Test
    fun buildIdsNextToTheVersionGoWithIt() {
        val market = "libchecker-*-market-release.apk"
        assertEquals(market, AssetVariant.tagGlob("LibChecker-2.5.4.5696014-2671-market-release.apk", "2.5.4"))
        assertEquals(market, AssetVariant.tagGlob("LibChecker-2.5.3.3f5abd2-2515-market-release.apk", "2.5.3"))
        assertEquals("app-*.apk", AssetVariant.tagGlob("app-b4a8da2-1.2.3.apk", "1.2.3"))
        assertEquals("readyou-*-signed.apk", AssetVariant.tagGlob("ReadYou-0.16.2-091c9207-signed.apk", "0.16.2"))
    }

    @Test
    fun variantWordsAreNeverBuildIds() {
        assertEquals(
            "seal-*-arm64-v8a-release.apk",
            AssetVariant.tagGlob("Seal-1.13.1-arm64-v8a-release.apk", "v1.13.1"),
        )
        assertNotEquals(
            AssetVariant.tagGlob("LibChecker-2.5.4.5696014-2671-foss-release.apk", "2.5.4"),
            AssetVariant.tagGlob("LibChecker-2.5.4.5696014-2671-market-release.apk", "2.5.4"),
        )
    }

    @Test
    fun separatorsMayDifferBetweenTagAndName() {
        assertEquals("thunderbird-*.apk", AssetVariant.tagGlob("thunderbird-24.0b2.apk", "THUNDERBIRD_24_0b2"))
        assertEquals("keepassdx-*-free.apk", AssetVariant.tagGlob("KeePassDX-4.5.0_beta05-free.apk", "4.5.0beta05"))
    }

    @Test
    fun aVersionInsideAnotherNumberIsNotTheReleaseVersion() {
        assertNull(AssetVariant.tagGlob("app-11.3.0.apk", "1.3"))
        assertNull(AssetVariant.tagGlob("app-1.3.0.apk", "1.3"))
        assertNull(AssetVariant.tagGlob("app-1.2.3.apk", "nightly"))
    }

    @Test
    fun monorepoAppsStayApart() {
        val auth = AssetVariant.tagGlob("ente-auth-v4.4.25.apk", "auth-v4.4.25")
        assertEquals("ente-auth-*.apk", auth)
        assertNotEquals(auth, AssetVariant.tagGlob("ente-photos-v1.3.64.apk", "photos-v1.3.64"))
        assertFalse(AssetOwnership.isSameApp("ente-auth-v4.4.25.apk", "ente-photos-v1.3.64.apk", "auth-v4.4.25", "photos-v1.3.64"))
        assertTrue(AssetOwnership.isSameApp("ente-auth-v4.4.25.apk", "ente-auth-v4.4.24.apk", "auth-v4.4.25", "auth-v4.4.24"))
    }

    @Test
    fun aVersionPrefixChangeKeepsTheFamily() {
        assertEquals(
            AssetVariant.tagGlob("InstallerX-Revived-offline-v2.3.2.apk", "v2.3.2"),
            AssetVariant.tagGlob("InstallerX-Revived-offline-26.05.01.apk", "26.05.01"),
        )
    }

    @Test
    fun aPinnedVariantSurvivesABuildIdRelease() {
        val pin = AssetVariant.fingerprintFromPickedAsset("InstallerX-Revived-online-26.09.apk", 2, "26.09")
        val release =
            listOf(
                asset("InstallerX-Revived-offline-26.10.b4a8da2.apk"),
                asset("InstallerX-Revived-online-26.10.b4a8da2.apk"),
            )
        val picked =
            AssetVariant.resolvePreferredAsset(
                assets = release,
                pinnedVariant = pin?.variant,
                pinnedTokens = pin?.tokens?.takeIf { it.isNotEmpty() },
                pinnedGlob = pin?.glob,
                releaseTag = "26.10.b4a8da2",
            )
        assertEquals("InstallerX-Revived-online-26.10.b4a8da2.apk", picked?.name)
    }

    @Test
    fun aPinStoredBeforeReleaseTagsStillMatches() {
        val legacyPin = AssetVariant.deriveGlob("Godot_v4.7.2-stable_android_editor.apk")
        val release =
            listOf(
                asset("Godot_v4.8-stable_android_editor_horizonos.apk"),
                asset("Godot_v4.8-stable_android_editor.apk"),
            )
        val picked =
            AssetVariant.resolvePreferredAsset(
                assets = release,
                pinnedVariant = null,
                pinnedGlob = legacyPin,
                releaseTag = "4.8-stable",
            )
        assertEquals("Godot_v4.8-stable_android_editor.apk", picked?.name)
    }

    @Test
    fun aVersionGluedToAWordIsNotTheReleaseVersion() {
        assertEquals("android10-app-*.apk", AssetVariant.tagGlob("android10-app-v10.apk", "v10"))
        assertNull(AssetVariant.tagGlob("android10-app.apk", "v10"))
    }

    @Test
    fun aPinnedGlobSurvivesTheNextReleaseWhenTheVersionAlsoAppearsInAWord() {
        val pin = AssetVariant.fingerprintFromPickedAsset("android10-app-v10-arm64.apk", 2, "v10")
        assertEquals(AssetVariant.tagGlob("android10-app-v11-arm64.apk", "v11"), pin?.glob)
    }

    @Test
    fun aNameThatLengthensWhenLowercasedStillYieldsItsVariant() {
        assertEquals("", AssetVariant.extract("İnstaller-1.2.3.apk", "1.2.3"))
        assertEquals(
            "arm64",
            AssetVariant.fingerprintFromPickedAsset("İnstaller-1.2.3-arm64.apk", 2, "1.2.3")?.variant,
        )
    }

    @Test
    fun aVariantPickedOutsideDetailsUsesItsRelease() {
        assertEquals(
            "market-release",
            AssetVariant.deriveFromPickedAsset("LibChecker-2.5.4.5696014-2671-market-release.apk", 2, "2.5.4"),
        )
    }
}
