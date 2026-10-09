package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.system.SystemArchitecture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssetSelectorTest {

    private val apk = listOf(".apk")

    private fun asset(
        name: String,
        size: Long = 1_000L,
    ): GithubAsset =
        GithubAsset(
            id = name.hashCode().toLong(),
            name = name,
            contentType = "",
            size = size,
            downloadUrl = "https://example/$name",
        )

    private fun pick(
        assets: List<GithubAsset>,
        arch: SystemArchitecture = SystemArchitecture.AARCH64,
        ext: List<String> = apk,
    ): String? = AssetSelector.choose(assets, arch, ext)?.name

    @Test
    fun exact_abi_beats_larger_universal_issue_808() {
        val assets =
            listOf(
                asset("rustdesk-1.4.0-aarch64.apk", size = 30_000_000L),
                asset("rustdesk-1.4.0-universal.apk", size = 90_000_000L),
                asset("rustdesk-1.4.0-armv7.apk", size = 28_000_000L),
                asset("rustdesk-1.4.0-x86_64.apk", size = 32_000_000L),
            )
        assertEquals("rustdesk-1.4.0-aarch64.apk", pick(assets))
    }

    @Test
    fun universal_beats_32bit_fallback_on_64bit_device() {
        val assets =
            listOf(
                asset("app-armeabi-v7a.apk", size = 10_000_000L),
                asset("app-universal.apk", size = 5_000_000L),
            )
        assertEquals("app-universal.apk", pick(assets))
    }

    @Test
    fun falls_back_to_32bit_when_only_compatible_option() {
        val assets =
            listOf(
                asset("app-armeabi-v7a.apk"),
                asset("app-x86_64.apk"),
            )
        assertEquals("app-armeabi-v7a.apk", pick(assets))
    }

    @Test
    fun picks_exact_for_x86_64_family() {
        val assets =
            listOf(
                asset("app-x86.apk"),
                asset("app-x86_64.apk"),
                asset("app-universal.apk"),
            )
        assertEquals("app-x86_64.apk", pick(assets, arch = SystemArchitecture.X86_64))
    }

    @Test
    fun stable_universal_beats_exact_abi_debug() {
        val assets =
            listOf(
                asset("app-aarch64-debug.apk", size = 50_000_000L),
                asset("app-universal.apk", size = 40_000_000L),
            )
        assertEquals("app-universal.apk", pick(assets))
    }

    @Test
    fun stable_beats_nightly_at_same_abi() {
        val assets =
            listOf(
                asset("app-aarch64-nightly.apk", size = 60_000_000L),
                asset("app-aarch64.apk", size = 30_000_000L),
            )
        assertEquals("app-aarch64.apk", pick(assets))
    }

    @Test
    fun extension_priority_dominates_size() {
        val assets =
            listOf(
                asset("app-x64.exe", size = 80_000_000L),
                asset("app-x64.msi", size = 20_000_000L),
            )
        val picked =
            pick(assets, arch = SystemArchitecture.X86_64, ext = listOf(".msi", ".exe"))
        assertEquals("app-x64.msi", picked)
    }

    @Test
    fun larger_size_wins_within_identical_tier() {
        val assets =
            listOf(
                asset("app-universal-a.apk", size = 10L),
                asset("app-universal-b.apk", size = 20L),
            )
        assertEquals("app-universal-b.apk", pick(assets))
    }

    @Test
    fun incompatible_arch_loses_even_without_prefilter() {
        val assets =
            listOf(
                asset("app-x86_64.apk", size = 99_000_000L),
                asset("app-aarch64.apk", size = 1_000L),
            )
        assertEquals("app-aarch64.apk", pick(assets))
    }

    @Test
    fun mobile_build_beats_leanback_build_with_same_abi() {
        val assets =
            listOf(
                asset("webhtv-1.0-leanback-arm64-v8a.apk", size = 30_000_000L),
                asset("webhtv-1.0-mobile-arm64-v8a.apk", size = 20_000_000L),
            )
        assertEquals("webhtv-1.0-mobile-arm64-v8a.apk", pick(assets))
    }

    @Test
    fun unmarked_apk_remains_neutral_between_mobile_and_leanback() {
        assertEquals(2, AssetSelector.deviceVariantRank("app-mobile-arm64-v8a.apk"))
        assertEquals(1, AssetSelector.deviceVariantRank("app-arm64-v8a.apk"))
        assertEquals(0, AssetSelector.deviceVariantRank("app-leanback-arm64-v8a.apk"))
        assertEquals(1, AssetSelector.deviceVariantRank("app-x86_64.AppImage"))
    }

    @Test
    fun mobile_keyword_requires_a_token_boundary() {
        assertEquals(1, AssetSelector.deviceVariantRank("automobile-arm64-v8a.apk"))
        assertEquals(2, AssetSelector.deviceVariantRank("app_mobile_arm64-v8a.apk"))
    }

    @Test
    fun webhtv_release_picks_the_mobile_arm64_build_issue_907() {
        val assets =
            listOf(
                asset("leanback-arm64_v8a.apk", size = 139_334_457L),
                asset("leanback-armeabi_v7a.apk", size = 115_232_614L),
                asset("mobile-arm64_v8a.apk", size = 139_191_611L),
                asset("mobile-armeabi_v7a.apk", size = 115_089_768L),
            )
        assertEquals("mobile-arm64_v8a.apk", pick(assets))
    }

    @Test
    fun native_abi_outranks_the_mobile_preference() {
        val assets =
            listOf(
                asset("app-mobile-armeabi-v7a.apk", size = 20_000_000L),
                asset("app-leanback-arm64-v8a.apk", size = 20_000_000L),
            )
        assertEquals("app-leanback-arm64-v8a.apk", pick(assets))
    }

    @Test
    fun empty_returns_null() {
        assertNull(pick(emptyList()))
    }

    @Test
    fun flavor_rank_classifies_prerelease() {
        assertEquals(3, AssetSelector.flavorRank("rustdesk-1.4.0-aarch64.apk"))
        assertEquals(2, AssetSelector.flavorRank("app-1.0-rc1.apk"))
        assertEquals(2, AssetSelector.flavorRank("app-1.0-beta.apk"))
        assertEquals(1, AssetSelector.flavorRank("app-nightly.apk"))
        assertEquals(0, AssetSelector.flavorRank("app-debug.apk"))
    }

    @Test
    fun arch_rank_tiers() {
        assertEquals(3, AssetSelector.archRank("app-aarch64.apk", SystemArchitecture.AARCH64))
        assertEquals(2, AssetSelector.archRank("app-universal.apk", SystemArchitecture.AARCH64))
        assertEquals(1, AssetSelector.archRank("app-armeabi-v7a.apk", SystemArchitecture.AARCH64))
        assertEquals(0, AssetSelector.archRank("app-x86_64.apk", SystemArchitecture.AARCH64))
    }

    @Test
    fun foreign_arch_ranks_incompatible() {
        assertEquals(0, AssetSelector.archRank("app-riscv64.AppImage", SystemArchitecture.X86_64))
        assertEquals(0, AssetSelector.archRank("app-armv7l.AppImage", SystemArchitecture.X86_64))
    }

    private val linux =
        listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")

    @Test
    fun real_teams_for_linux_does_not_pick_larger_armv7l_appimage_on_x64() {
        val assets =
            listOf(
                asset("teams-for-linux-2.11.1-armv7l.AppImage", size = 119_000_000L),
                asset("teams-for-linux-2.11.1.AppImage", size = 100_000_000L),
                asset("teams-for-linux_2.11.1_amd64.deb", size = 103_000_000L),
            )
        assertEquals(
            "teams-for-linux-2.11.1.AppImage",
            pick(assets, arch = SystemArchitecture.X86_64, ext = linux),
        )
    }

    @Test
    fun real_go_task_apk_set_picks_arm64_on_android_arm64() {
        val assets =
            listOf(
                asset("task_3.51.1_linux_386.apk", size = 14_400_000L),
                asset("task_3.51.1_linux_amd64.apk", size = 15_500_000L),
                asset("task_3.51.1_linux_arm.apk", size = 14_500_000L),
                asset("task_3.51.1_linux_arm64.apk", size = 13_900_000L),
                asset("task_3.51.1_linux_riscv64.apk", size = 14_700_000L),
            )
        assertEquals("task_3.51.1_linux_arm64.apk", pick(assets, arch = SystemArchitecture.AARCH64))
    }

    @Test
    fun exotic_arch_never_beats_host_build_even_when_larger() {
        val assets =
            listOf(
                asset("tool-linux-riscv64.AppImage", size = 99_000_000L),
                asset("tool-linux-x86_64.AppImage", size = 40_000_000L),
            )
        assertEquals(
            "tool-linux-x86_64.AppImage",
            pick(assets, arch = SystemArchitecture.X86_64, ext = linux),
        )
    }
}
