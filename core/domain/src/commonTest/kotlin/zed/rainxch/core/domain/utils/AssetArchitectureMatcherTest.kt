package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.utils.AssetArchitectureMatcher.Match
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetArchitectureMatcherTest {
    private fun match(name: String) = AssetArchitectureMatcher.matchArchitecture(name)

    @Test
    fun recognizes_widened_arm_vocabulary() {
        assertEquals(Match.Known(SystemArchitecture.ARM), match("git-delta_0.19.2_armhf.deb"))
        assertEquals(Match.Known(SystemArchitecture.ARM), match("gh_2.95.0_linux_armv6.deb"))
        assertEquals(Match.Known(SystemArchitecture.ARM), match("teams-for-linux-2.11.1-armv7l.AppImage"))
        assertEquals(Match.Known(SystemArchitecture.ARM), match("powershell-7.6.3-linux-arm32.tar.gz"))
    }

    @Test
    fun recognizes_widened_x86_vocabulary() {
        assertEquals(Match.Known(SystemArchitecture.X86), match("draw.io-ia32-30.2.4-windows-32bit-installer.exe"))
        assertEquals(Match.Known(SystemArchitecture.X86), match("OpenSSH-Win32-v10.0.0.0.msi"))
        assertEquals(Match.Known(SystemArchitecture.X86_64), match("otp_win64_29.0.2.exe"))
    }

    @Test
    fun recognizes_bare_go_386_token() {
        assertEquals(Match.Known(SystemArchitecture.X86), match("task_3.51.1_linux_386.apk"))
        assertTrue(AssetArchitectureMatcher.isCompatible("task_3.51.1_linux_386.apk", SystemArchitecture.X86_64))
        assertFalse(AssetArchitectureMatcher.isCompatible("task_3.51.1_linux_386.apk", SystemArchitecture.AARCH64))
    }

    @Test
    fun x64_token_wins_over_win32_platform_name() {
        assertEquals(Match.Known(SystemArchitecture.X86_64), match("xmcl-0.59.1-win32-x64.appx"))
    }

    @Test
    fun exotic_arches_are_foreign_not_universal() {
        assertEquals(Match.Foreign, match("flannel-v0.28.5-linux-riscv64.tar.gz"))
        assertEquals(Match.Foreign, match("ripgrep-15.1.0-s390x-unknown-linux-gnu.tar.gz"))
        assertEquals(Match.Foreign, match("rclone-v1.74.3-aix-ppc64.zip"))
        assertEquals(Match.Foreign, match("rclone-v1.74.3-linux-mips.deb"))
        assertEquals(Match.Foreign, match("Xray-linux-loong64.zip"))
    }

    @Test
    fun no_token_is_universal() {
        assertEquals(Match.Universal, match("teams-for-linux-2.11.1.AppImage"))
        assertEquals(Match.Universal, match("app-universal.dmg"))
        assertEquals(Match.Known(SystemArchitecture.AARCH64), match("app-arm64.apk"))
    }

    @Test
    fun foreign_is_incompatible_everywhere() {
        assertFalse(AssetArchitectureMatcher.isCompatible("app-riscv64.AppImage", SystemArchitecture.X86_64))
        assertFalse(AssetArchitectureMatcher.isCompatible("app-s390x.deb", SystemArchitecture.AARCH64))
    }

    @Test
    fun arm_is_incompatible_with_x86_64_but_runs_on_arm64() {
        assertFalse(AssetArchitectureMatcher.isCompatible("app-armv7l.AppImage", SystemArchitecture.X86_64))
        assertTrue(AssetArchitectureMatcher.isCompatible("app-armv7l.apk", SystemArchitecture.AARCH64))
    }

    @Test
    fun thirty_two_bit_x86_runs_on_x86_64() {
        assertTrue(AssetArchitectureMatcher.isCompatible("OpenSSH-Win32.msi", SystemArchitecture.X86_64))
    }

    @Test
    fun detect_architecture_maps_foreign_and_universal_to_null() {
        assertNull(AssetArchitectureMatcher.detectArchitecture("app-riscv64.AppImage"))
        assertNull(AssetArchitectureMatcher.detectArchitecture("plain.AppImage"))
        assertEquals(SystemArchitecture.ARM, AssetArchitectureMatcher.detectArchitecture("app-armhf.deb"))
    }
}
