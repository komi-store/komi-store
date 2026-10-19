package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetPlatformTest {
    @Test
    fun alpine_nfpm_apks_are_not_android() {
        assertFalse(isAndroidApk("task_3.51.1_linux_arm64.apk"))
        assertFalse(isAndroidApk("task_3.51.1_linux_386.apk"))
        assertFalse(isAndroidApk("task_3.51.1_linux_amd64.apk"))
        assertFalse(isAndroidApk("k8sgpt_amd64.apk"))
        assertFalse(isAndroidApk("spicedb_1.54.0_linux_arm64.apk"))
    }

    @Test
    fun genuine_android_apks_are_android() {
        assertTrue(isAndroidApk("app-arm64-v8a-release.apk"))
        assertTrue(isAndroidApk("app-armeabi-v7a-release.apk"))
        assertTrue(isAndroidApk("v2rayNG_2.2.5_universal.apk"))
        assertTrue(isAndroidApk("v2rayNG_2.2.5_x86_64.apk"))
        assertTrue(isAndroidApk("v2rayNG_2.2.5_x86.apk"))
        assertTrue(isAndroidApk("Magisk-v30.7.apk"))
        assertTrue(isAndroidApk("app-release.apk"))
    }

    @Test
    fun linux_only_arch_apks_are_not_android() {
        assertFalse(isAndroidApk("tool_armhf.apk"))
        assertFalse(isAndroidApk("tool_armv7l.apk"))
        assertFalse(isAndroidApk("tool_arm32.apk"))
        assertFalse(isAndroidApk("tool_riscv64.apk"))
        assertFalse(isAndroidApk("tool_s390x.apk"))
        assertFalse(isAndroidApk("tool_ppc64le.apk"))
    }

    @Test
    fun non_apk_is_not_android_apk() {
        assertFalse(isAndroidApk("tool_linux_amd64.deb"))
        assertFalse(isAndroidApk("tool.AppImage"))
    }

    @Test
    fun alpine_apk_groups_under_linux_not_android() {
        assertEquals(DiscoveryPlatform.Linux, assetPlatformOf("task_3.51.1_linux_arm64.apk"))
        assertEquals(DiscoveryPlatform.Android, assetPlatformOf("app-arm64-v8a-release.apk"))
        assertEquals(DiscoveryPlatform.Android, assetPlatformOf("v2rayNG_2.2.5_universal.apk"))
    }
}
