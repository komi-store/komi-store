package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionMathAssetVersionTest {
    @Test
    fun a_version_before_the_architecture_tokens_parses() {
        assertEquals("1.0.6", VersionMath.assetVersionInName("runtime-nodejs-1.0.6-arm64-v8a.apk"))
    }

    @Test
    fun a_name_without_a_dotted_version_parses_to_null() {
        assertNull(VersionMath.assetVersionInName("app-arm64-v8a.apk"))
    }

    @Test
    fun an_underscored_abi_token_is_not_a_version() {
        assertNull(VersionMath.assetVersionInName("app-x86_64.apk"))
        assertNull(VersionMath.assetVersionInName("app-armeabi-v7a.apk"))
    }

    @Test
    fun a_prefixed_version_parses() {
        assertEquals("4.7.2", VersionMath.assetVersionInName("Godot_v4.7.2-stable_android_editor.apk"))
        assertEquals("2.1.0", VersionMath.assetVersionInName("v2.1.0.zap"))
    }

    @Test
    fun a_dotted_calver_parses() {
        assertEquals("2026.11.02", VersionMath.assetVersionInName("app-2026.11.02.apk"))
    }

    @Test
    fun a_version_without_an_extension_parses() {
        assertEquals("1.0.6", VersionMath.assetVersionInName("runtime-nodejs-1.0.6"))
    }

    @Test
    fun a_name_without_a_version_parses_to_null() {
        assertNull(VersionMath.assetVersionInName(null))
        assertNull(VersionMath.assetVersionInName("app.apk"))
        assertNull(VersionMath.assetVersionInName(""))
    }

    @Test
    fun versions_compare_by_number_not_by_text() {
        assertEquals(1, VersionMath.compareAssetVersions("1.10.0", "1.9.9"))
        assertEquals(-1, VersionMath.compareAssetVersions("1.0.5", "1.0.6"))
        assertEquals(0, VersionMath.compareAssetVersions("1.0.6", "1.0.6"))
    }

    @Test
    fun missing_trailing_components_count_as_zero() {
        assertEquals(0, VersionMath.compareAssetVersions("1.0", "1.0.0"))
        assertEquals(1, VersionMath.compareAssetVersions("1.0.1", "1.0"))
    }
}
