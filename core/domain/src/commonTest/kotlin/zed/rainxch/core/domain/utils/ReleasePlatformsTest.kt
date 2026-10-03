package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

class ReleasePlatformsTest {
    private fun release(tag: String, vararg assets: String) =
        GithubRelease(
            id = tag.hashCode().toLong(),
            tagName = tag,
            name = tag,
            publishedAt = "2026-01-01T00:00:00Z",
            description = null,
            assets = assets.map {
                GithubAsset(id = it.hashCode().toLong(), name = it, contentType = "", size = 1L, downloadUrl = "")
            },
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
        )

    private val notesnook = listOf(
        release("3.4.13-android", "notesnook-arm64-v8a.apk", "notesnook-x86_64.apk"),
        release(
            "v3.4.8",
            "latest.yml",
            "notesnook_linux_x86_64.AppImage",
            "notesnook_mac_arm64.dmg",
            "notesnook_win_x64.exe",
        ),
        release("3.4.12-android", "notesnook-arm64-v8a.apk"),
        release("v3.4.7", "notesnook_linux_x86_64.AppImage", "notesnook_win_x64.exe"),
    )

    @Test
    fun releasePlatformsIgnoreNonInstallerFiles() {
        assertEquals(
            setOf(DiscoveryPlatform.Linux, DiscoveryPlatform.Macos, DiscoveryPlatform.Windows),
            notesnook[1].platforms(),
        )
    }

    @Test
    fun eachPlatformGetsItsNewestReleaseWithTheDeviceFirst() {
        val onAndroid = newestReleasePerPlatform(notesnook, devicePlatform = DiscoveryPlatform.Android)
        assertEquals(
            listOf(
                DiscoveryPlatform.Android to "3.4.13-android",
                DiscoveryPlatform.Macos to "v3.4.8",
                DiscoveryPlatform.Windows to "v3.4.8",
                DiscoveryPlatform.Linux to "v3.4.8",
            ),
            onAndroid.map { it.platform to it.release.tagName },
        )
        val onWindows = newestReleasePerPlatform(notesnook, devicePlatform = DiscoveryPlatform.Windows)
        assertEquals(DiscoveryPlatform.Windows, onWindows.first().platform)
    }

    @Test
    fun noReleasesMeansNoPlatforms() {
        assertEquals(emptyList(), newestReleasePerPlatform(emptyList(), devicePlatform = DiscoveryPlatform.Android))
    }
}
