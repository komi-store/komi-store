package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstalledVariantTest {
    private fun app(
        packageName: String,
        installedAssetName: String?,
        pending: Boolean = false,
        glob: String? = null,
        installedVersion: String = "1.0.0",
    ) = InstalledApp(
        packageName = packageName,
        repoId = 1L,
        repoName = "repo",
        repoOwner = "owner",
        repoOwnerAvatarUrl = "",
        repoDescription = null,
        primaryLanguage = null,
        repoUrl = "",
        installedVersion = installedVersion,
        installedAssetName = installedAssetName,
        installedAssetUrl = null,
        latestVersion = null,
        latestAssetName = null,
        latestAssetUrl = null,
        latestAssetSize = null,
        appName = packageName,
        installSource = InstallSource.THIS_APP,
        installedAt = 0L,
        lastCheckedAt = 0L,
        lastUpdatedAt = 0L,
        isUpdateAvailable = false,
        signingFingerprint = null,
        systemArchitecture = "arm64-v8a",
        fileExtension = "apk",
        isPendingInstall = pending,
        assetGlobPattern = glob,
    )

    private fun marked(
        assets: List<String>,
        apps: List<InstalledApp>,
    ): List<String> = assets.filter { AssetOwnership.variantStatus(it, "2.0.0", apps) != null }

    private val abiRelease =
        listOf(
            "app-arm64-v8a-1.3.0.apk",
            "app-armeabi-v7a-1.3.0.apk",
            "app-universal-1.3.0.apk",
        )

    private val morphe =
        listOf(
            "google-photos-arm64-v8a-morphe-patches-v7.95.0.989626323.apk",
            "instagram-arm64-v8a-piko-patches-v440.0.0.1.1.apk",
            "instagram-armeabi-v7a-piko-patches-v440.0.0.1.1.apk",
            "youtube-universal-morphe-patches-v20.1.1.apk",
        )

    @Test
    fun only_the_installed_abi_is_marked_in_a_single_app_release() {
        val apps = listOf(app("com.app", "app-arm64-v8a-1.2.0.apk"))
        assertEquals(listOf("app-arm64-v8a-1.3.0.apk"), marked(abiRelease, apps))
    }

    @Test
    fun only_the_installed_app_and_abi_are_marked_in_a_multi_app_release() {
        val apps = listOf(app("com.instagram", "instagram-arm64-v8a-piko-patches-v436.0.0.1.1.apk"))
        assertEquals(listOf("instagram-arm64-v8a-piko-patches-v440.0.0.1.1.apk"), marked(morphe, apps))
    }

    @Test
    fun every_installed_app_is_marked_in_a_multi_app_release() {
        val apps =
            listOf(
                app("com.instagram", "instagram-arm64-v8a-piko-patches-v436.0.0.1.1.apk"),
                app("com.google.photos", "google-photos-arm64-v8a-morphe-patches-v7.90.0.1.apk"),
            )
        assertEquals(
            listOf(
                "google-photos-arm64-v8a-morphe-patches-v7.95.0.989626323.apk",
                "instagram-arm64-v8a-piko-patches-v440.0.0.1.1.apk",
            ),
            marked(morphe, apps),
        )
    }

    @Test
    fun a_pending_install_is_not_marked() {
        val apps = listOf(app("com.app", "app-arm64-v8a-1.2.0.apk", pending = true))
        assertEquals(emptyList(), marked(abiRelease, apps))
    }

    @Test
    fun names_without_a_version_mark_only_the_same_file() {
        val apps = listOf(app("com.app", "app-arm64-v8a.apk"))
        assertEquals(listOf("app-arm64-v8a.apk"), marked(listOf("app-arm64-v8a.apk", "app-armeabi-v7a.apk"), apps))
    }

    @Test
    fun a_linked_app_is_marked_by_its_pinned_glob() {
        val apps = listOf(app("com.app", null, glob = "app-arm64-v8a-*.apk"))
        assertEquals(listOf("app-arm64-v8a-1.3.0.apk"), marked(abiRelease, apps))
    }

    @Test
    fun the_installed_release_is_installed() {
        val apps =
            listOf(
                app(
                    "org.godotengine.editor.v4",
                    "Godot_v4.7.2-stable_android_editor.apk",
                    installedVersion = "4.7.2-stable",
                ),
            )
        assertEquals(
            AssetOwnership.VariantStatus.INSTALLED,
            AssetOwnership.variantStatus("Godot_v4.7.2-stable_android_editor.apk", "4.7.2-stable", apps),
        )
    }

    @Test
    fun a_newer_release_of_the_installed_variant_is_an_update() {
        val apps = listOf(app("io.ente.auth", "ente-auth-v4.4.24.apk", installedVersion = "auth-v4.4.24"))
        assertEquals(
            AssetOwnership.VariantStatus.UPDATE,
            AssetOwnership.variantStatus("ente-auth-v4.4.25.apk", "auth-v4.4.25", apps),
        )
    }

    @Test
    fun an_older_release_of_the_installed_variant_is_neither() {
        val apps = listOf(app("io.ente.auth", "ente-auth-v4.4.24.apk", installedVersion = "auth-v4.4.24"))
        assertEquals(
            AssetOwnership.VariantStatus.OTHER_VERSION,
            AssetOwnership.variantStatus("ente-auth-v4.4.20.apk", "auth-v4.4.20", apps),
        )
    }

    @Test
    fun another_variant_has_no_status() {
        val apps = listOf(app("com.app", "app-arm64-v8a-1.2.0.apk", installedVersion = "v1.2.0"))
        assertNull(AssetOwnership.variantStatus("app-armeabi-v7a-1.3.0.apk", "v1.3.0", apps))
    }
}
