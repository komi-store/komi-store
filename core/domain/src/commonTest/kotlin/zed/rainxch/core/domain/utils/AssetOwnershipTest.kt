package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp

class AssetOwnershipTest {
    private fun asset(name: String) =
        GithubAsset(
            id = name.hashCode().toLong(),
            name = name,
            contentType = "application/vnd.android.package-archive",
            size = 1L,
            downloadUrl = "https://dl/$name",
        )

    private fun release(tag: String, assets: List<GithubAsset>) =
        GithubRelease(
            id = tag.hashCode().toLong(),
            tagName = tag,
            name = tag,
            publishedAt = "2026-01-01T00:00:00Z",
            description = null,
            assets = assets,
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
        )

    private fun app(
        packageName: String,
        installedAssetName: String?,
        installedVersion: String = "1.0.0",
        glob: String? = null,
        filter: String? = null,
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
        assetFilterRegex = filter,
        assetGlobPattern = glob,
    )

    private fun ownerOf(name: String, apps: List<InstalledApp>, assets: List<GithubAsset>) =
        AssetOwnership.ownerOf(name, apps, assets, listOf(release("current", assets)))

    private val abiRelease = listOf(
        asset("app-arm64-v8a-1.3.0.apk"),
        asset("app-armeabi-v7a-1.3.0.apk"),
        asset("app-universal-1.3.0.apk"),
    )

    private val morphe = listOf(
        asset("google-photos-arm64-v8a-morphe-patches-v7.95.0.989626323.apk"),
        asset("instagram-arm64-v8a-piko-patches-v440.0.0.1.1.apk"),
        asset("instagram-armeabi-v7a-piko-patches-v436.0.0.1.1.apk"),
        asset("youtube-universal-morphe-patches-v20.1.1.apk"),
    )

    private val godotV4 = app(
        "org.godotengine.editor.v4",
        "Godot_v4.7.2-stable_android_editor.apk",
        installedVersion = "4.7.2-stable",
        glob = "godot_*-stable_android_editor.apk",
    )
    private val godotV3 = app(
        "org.godotengine.editor.v3",
        "Godot_v3.6.3-stable_android_editor.apk",
        installedVersion = "3.6.3-stable",
    )
    private val godot363 = listOf(asset("Godot_v3.6.3-stable_android_editor.apk"))
    private val godot472 = listOf(
        asset("Godot_v4.7.2-stable_android_debug.perfetto.apk"),
        asset("Godot_v4.7.2-stable_android_editor.apk"),
        asset("Godot_v4.7.2-stable_android_editor_horizonos.apk"),
        asset("Godot_v4.7.2-stable_android_editor_picoos.apk"),
        asset("Godot_v4.7.2-stable_android_release.perfetto.apk"),
    )

    @Test
    fun versionMajorReadsTheFirstDottedVersion() {
        assertEquals(4, AssetVariant.versionMajor("Godot_v4.7.2-stable_android_editor.apk"))
        assertEquals(3, AssetVariant.versionMajor("3.6.3-stable"))
        assertEquals(1, AssetVariant.versionMajor("MyApp_v1.2.apk"))
        assertEquals(439, AssetVariant.versionMajor("instagram-arm64-v8a-piko-patches-v439.0.0.37.89.apk"))
        assertNull(AssetVariant.versionMajor("app-arm64-v8a.apk"))
        assertNull(AssetVariant.versionMajor("app-x86_64-release.apk"))
    }

    @Test
    fun singleAppKeepsOwnershipAcrossAbis() {
        val installed = app("com.app", "app-arm64-v8a-1.2.3.apk")
        assertEquals(installed, ownerOf("app-universal-1.3.0.apk", listOf(installed), abiRelease))
        assertEquals(installed, ownerOf("app-armeabi-v7a-1.3.0.apk", listOf(installed), abiRelease))
    }

    @Test
    fun singleAppKeepsOwnershipAcrossRenames() {
        val separators = app("com.app", "MyApp_v1.2.apk")
        assertEquals(separators, ownerOf("MyApp-1.3.apk", listOf(separators), listOf(asset("MyApp-1.3.apk"))))

        val renamed = app("com.app", "OldName-1.0.apk")
        val newest = listOf(asset("NewName-2.0.apk"))
        val history = listOf(
            release("2.0", newest),
            release("1.1", listOf(asset("NewName-1.1.apk"), asset("OldName-1.1.apk"))),
            release("1.0", listOf(asset("OldName-1.0.apk"))),
        )
        assertEquals(renamed, AssetOwnership.ownerOf("NewName-2.0.apk", listOf(renamed), newest, history))
    }

    @Test
    fun appsReleasedSideBySideAreNotARename() {
        val k9 = app("com.fsck.k9", "k9mail-23.0.apk", installedVersion = "K9MAIL_23_0")
        val thunderbird = listOf(asset("thunderbird-23.1.apk"))
        val history = listOf(
            release("THUNDERBIRD_23_1", thunderbird),
            release("K9MAIL_23_1", listOf(asset("k9mail-23.1.apk"))),
            release("THUNDERBIRD_23_0", listOf(asset("thunderbird-23.0.apk"))),
            release("K9MAIL_23_0", listOf(asset("k9mail-23.0.apk"))),
        )
        assertNull(AssetOwnership.ownerOf("thunderbird-23.1.apk", listOf(k9), thunderbird, history))
        assertEquals(
            k9,
            AssetOwnership.ownerOf("k9mail-23.1.apk", listOf(k9), listOf(asset("k9mail-23.1.apk")), history),
        )
    }

    @Test
    fun aSoleAppInAMonorepoDoesNotOwnTheOtherAppsReleases() {
        val auth = app("io.ente.auth.independent", "ente-auth-v4.4.24.apk", installedVersion = "auth-v4.4.24")
        val photos = listOf(asset("ente-photos-v1.3.64.apk"))
        val ensu = listOf(asset("ensu-v0.1.20.apk"))
        val authNewest = listOf(asset("ente-auth-v4.4.25.apk"))
        val history = listOf(
            release("photos-v1.3.64", photos),
            release("ensu-v0.1.20", ensu),
            release("auth-v4.4.25", authNewest),
            release("photos-v1.3.59", listOf(asset("ente-photos-v1.3.59.apk"))),
            release("auth-v4.4.24", listOf(asset("ente-auth-v4.4.24.apk"))),
            release("photos-v1.3.57", listOf(asset("ente-photos-v1.3.57.apk"))),
            release("ensu-v0.1.17", listOf(asset("ensu-v0.1.17.apk"))),
        )
        assertNull(AssetOwnership.ownerOf("ente-photos-v1.3.64.apk", listOf(auth), photos, history))
        assertNull(AssetOwnership.ownerOf("ensu-v0.1.20.apk", listOf(auth), ensu, history))
        assertEquals(auth, AssetOwnership.ownerOf("ente-auth-v4.4.25.apk", listOf(auth), authNewest, history))
    }

    @Test
    fun aSiblingNobodyInstalledIsOwnedByNoneOfTheInstalledApps() {
        val auth = app("io.ente.auth.independent", "ente-auth-v4.4.24.apk")
        val ensu = app("io.ente.ensu", "ensu-v0.1.19.apk")
        val photos = listOf(asset("ente-photos-v1.3.64.apk"))
        val history = listOf(
            release("photos-v1.3.64", photos),
            release("ensu-v0.1.20", listOf(asset("ensu-v0.1.20.apk"))),
            release("auth-v4.4.25", listOf(asset("ente-auth-v4.4.25.apk"))),
            release("photos-v1.3.63", listOf(asset("ente-photos-v1.3.63.apk"))),
        )
        assertNull(AssetOwnership.ownerOf("ente-photos-v1.3.64.apk", listOf(auth, ensu), photos, history))
        assertEquals(
            ensu,
            AssetOwnership.ownerOf("ensu-v0.1.20.apk", listOf(auth, ensu), listOf(asset("ensu-v0.1.20.apk")), history),
        )
    }

    @Test
    fun oneRenamedAppAmongSeveralKeepsItsUpdates() {
        val renamed = app("com.app", "OldName-1.0.apk")
        val other = app("com.app.companion", "Companion-1.0.apk")
        val newest = listOf(asset("NewName-2.0.apk"), asset("Companion-2.0.apk"))
        val history = listOf(
            release("2.0", newest),
            release("1.0", listOf(asset("OldName-1.0.apk"), asset("Companion-1.0.apk"))),
        )
        assertEquals(renamed, AssetOwnership.ownerOf("NewName-2.0.apk", listOf(renamed, other), newest, history))
        assertEquals(other, AssetOwnership.ownerOf("Companion-2.0.apk", listOf(renamed, other), newest, history))
    }

    @Test
    fun aMonoreposNewAppIsNotARenameOfTheInstalledOne() {
        val auth = app("io.ente.auth.independent", "ente-auth-v4.4.25.apk")
        val locker = listOf(asset("ente-locker-v1.0.0.apk"))
        val history = listOf(
            release("locker-v1.0.0", locker),
            release("auth-v4.4.25", listOf(asset("ente-auth-v4.4.25.apk"))),
            release("auth-v4.4.24", listOf(asset("ente-auth-v4.4.24.apk"))),
        )
        assertNull(AssetOwnership.ownerOf("ente-locker-v1.0.0.apk", listOf(auth), locker, history))
    }

    @Test
    fun otherAppsInTheSameReleaseAreNotOwned() {
        val instagram = app("com.instagram", "instagram-arm64-v8a-piko-patches-v439.0.0.37.89.apk")
        val youtube = app("com.youtube", "youtube-universal-morphe-patches-v19.9.9.apk")
        assertNull(ownerOf("youtube-universal-morphe-patches-v20.1.1.apk", listOf(instagram), morphe))
        assertEquals(
            instagram,
            ownerOf("instagram-armeabi-v7a-piko-patches-v436.0.0.1.1.apk", listOf(instagram), morphe),
        )
        assertEquals(
            youtube,
            ownerOf("youtube-universal-morphe-patches-v20.1.1.apk", listOf(instagram, youtube), morphe),
        )
        assertNull(
            ownerOf(
                "google-photos-arm64-v8a-morphe-patches-v7.95.0.989626323.apk",
                listOf(instagram, youtube),
                morphe,
            ),
        )
    }

    @Test
    fun appsNamedAlikeAreToldApartByMajorVersion() {
        val repoApps = listOf(godotV4, godotV3)
        assertEquals(godotV3, ownerOf(godot363.single().name, repoApps, godot363))
        assertEquals(godotV4, ownerOf("Godot_v4.7.2-stable_android_editor.apk", repoApps, godot472))
        assertNull(ownerOf("Godot_v4.7.2-stable_android_editor_horizonos.apk", repoApps, godot472))
    }

    @Test
    fun packagesThatNameTheirMajorNeverOwnAnotherMajor() {
        assertNull(ownerOf(godot363.single().name, listOf(godotV4), godot363))
        assertEquals(godotV4, ownerOf("Godot_v4.7.2-stable_android_editor.apk", listOf(godotV4), godot472))

        val ordinary = app("com.app", "app-1.9.0.apk", installedVersion = "1.9.0")
        val major2 = listOf(asset("app-2.0.0.apk"))
        assertEquals(ordinary, ownerOf("app-2.0.0.apk", listOf(ordinary), major2))
    }

    @Test
    fun linkedAppsFallBackToTheirInstalledVersionLine() {
        val linkedV3 = godotV3.copy(installedAssetName = null, assetGlobPattern = "godot_*-stable_android_editor.apk")
        val linkedV4 = godotV4.copy(installedAssetName = null)
        assertEquals(linkedV3, ownerOf(godot363.single().name, listOf(linkedV4, linkedV3), godot363))
        assertEquals(
            linkedV4,
            ownerOf("Godot_v4.7.2-stable_android_editor.apk", listOf(linkedV3, linkedV4), godot472),
        )
    }

    @Test
    fun filterWinsAndIgnoresCase() {
        val filtered = app("com.ig", "x.apk", filter = "^instagram")
        val other = app("com.other", "other-1.0.apk")
        assertEquals(filtered, ownerOf("Instagram-arm64-v8a-1.0.apk", listOf(other, filtered), morphe))
    }

    @Test
    fun narrowToAppKeepsOnlyTheAnchorsFamily() {
        val narrowed = AssetOwnership.narrowToApp(godot472, "Godot_v4.6.3-stable_android_editor.apk")
        assertEquals(listOf("Godot_v4.7.2-stable_android_editor.apk"), narrowed.map { it.name })
        assertEquals(abiRelease, AssetOwnership.narrowToApp(abiRelease, "app-arm64-v8a-1.2.3.apk"))
        assertEquals(abiRelease, AssetOwnership.narrowToApp(abiRelease, "unrelated-1.0.apk"))
        assertEquals(abiRelease, AssetOwnership.narrowToApp(abiRelease, null))
    }

    @Test
    fun pinnedTokensResolveWithinTheAppsOwnFamily() {
        val pinned = setOf("stable")
        assertEquals(
            "Godot_v4.7.2-stable_android_debug.perfetto.apk",
            AssetVariant.resolvePreferredAsset(godot472, null, pinned)?.name,
        )
        val sameApp = AssetOwnership.narrowToApp(godot472, godotV4.installedAssetName)
        assertEquals(
            "Godot_v4.7.2-stable_android_editor.apk",
            AssetVariant.resolvePreferredAsset(sameApp, null, pinned)?.name,
        )
    }

    @Test
    fun emptyStemsNeverMatch() {
        assertFalse(AssetOwnership.isSameApp("arm64-v8a.apk", "x86.apk"))
        assertFalse(AssetOwnership.isSameApp("1.2.3.apk", "2.0.0.apk"))
    }
}
