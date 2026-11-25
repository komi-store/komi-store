package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Replays the update check offline over 48 projects' real release histories (tools/asset-replay/collect_corpus.py).
class AssetMatchingReplayTest {
    private data class CorpusRelease(
        val tag: String,
        val published: String,
        val prerelease: Boolean,
        val assets: List<String>,
    ) {
        val apkNames: List<String> get() = assets.filter { it.endsWith(".apk", ignoreCase = true) }
    }

    private val corpus: Map<String, List<CorpusRelease>> by lazy { loadCorpus() }

    private fun loadCorpus(): Map<String, List<CorpusRelease>> {
        val stream =
            javaClass.getResourceAsStream("/asset-replay/corpus.tsv")
                ?: error("asset-replay corpus not on the test classpath")
        val repos = linkedMapOf<String, MutableList<CorpusRelease>>()
        stream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val fields = line.split('\t')
                if (fields.size < 4) return@forEach
                repos.getOrPut(fields[0]) { mutableListOf() } +=
                    CorpusRelease(
                        tag = fields[1],
                        published = fields[2],
                        prerelease = fields[3] == "1",
                        assets = fields.drop(4),
                    )
            }
        }
        return repos
    }

    // ---- the replay, a single-app copy of resolveTrackedRelease ----

    // ReleaseWindow.kt's defaults: newest first by publish date, pre-releases (API flag or tag) filtered out.
    private fun windowOf(releases: List<CorpusRelease>): List<CorpusRelease> =
        releases
            .sortedByDescending { it.published }
            .filterNot { replayRelease(it).isEffectivelyPreRelease() }

    private fun replayPick(
        releases: List<CorpusRelease>,
        installedTag: String,
        installedAssetName: String?,
    ): CorpusRelease? {
        val self = replayApp(installedTag, installedAssetName)
        return releases.firstOrNull { release -> replayPrimary(self, releases, release)?.name?.isNotEmpty() == true }
    }

    // Device-only parts are pinned: installable means a .apk name, and the pick falls back to the first same-app candidate.
    private fun replayPrimary(
        self: InstalledApp,
        releases: List<CorpusRelease>,
        release: CorpusRelease,
    ): GithubAsset? {
        val installableForPlatform = release.assets.filter { it.endsWith(".apk", ignoreCase = true) }
        val installableForApp =
            installableForPlatform.filterNot { belongsElsewhere(self, releases, it, release.tag, installableForPlatform) }
        if (installableForApp.isEmpty()) return null

        val sameApp =
            AssetOwnership.narrowToApp(
                installableForApp.map(::replayAsset),
                self.installedAssetName,
                release.tag,
                self.installedVersion,
            )

        val fingerprintMatch =
            AssetVariant.resolvePreferredAsset(
                assets = sameApp,
                pinnedVariant = null,
                pinnedTokens = null,
                pinnedGlob = null,
                releaseTag = release.tag,
            )

        val autoPickPool =
            AssetOwnership.narrowToApp(
                AssetVariant.filterByPackageFlavor(installableForApp.map(::replayAsset), self.packageName),
                self.installedAssetName,
                release.tag,
                self.installedVersion,
            )

        return fingerprintMatch ?: autoPickPool.firstOrNull()
    }

    private fun belongsElsewhere(
        self: InstalledApp,
        releases: List<CorpusRelease>,
        assetName: String,
        releaseTag: String,
        releaseAssets: List<String>,
    ): Boolean {
        if (!AssetOwnership.canOwn(self, assetName)) return true
        val owner =
            AssetOwnership.ownerOf(
                assetName = assetName,
                apps = listOf(self),
                releaseAssets = releaseAssets.map(::replayAsset),
                releaseHistory = releases.map(::replayRelease),
                releaseTag = releaseTag,
            ) ?: return self.installedAssetName != null || !self.assetGlobPattern.isNullOrBlank()
        return owner.packageName != self.packageName
    }

    private fun replayApp(
        installedTag: String,
        installedAssetName: String?,
    ): InstalledApp =
        InstalledApp(
            packageName = "replay.app",
            repoId = 1L,
            repoName = "replay",
            repoOwner = "replay",
            repoOwnerAvatarUrl = "",
            repoDescription = null,
            primaryLanguage = null,
            repoUrl = "https://github.com/replay/replay",
            installedVersion = installedTag,
            installedAssetName = installedAssetName,
            installedAssetUrl = null,
            latestVersion = null,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = "Replay",
            installSource = InstallSource.MANUAL,
            installedAt = 0L,
            lastCheckedAt = 0L,
            lastUpdatedAt = 0L,
            isUpdateAvailable = false,
            signingFingerprint = null,
            systemArchitecture = "arm64-v8a",
            fileExtension = "apk",
        )

    private fun replayAsset(name: String): GithubAsset =
        GithubAsset(
            id = name.hashCode().toLong(),
            name = name,
            contentType = "application/octet-stream",
            size = 1L,
            downloadUrl = "https://example.invalid/$name",
        )

    private fun replayRelease(release: CorpusRelease): GithubRelease =
        GithubRelease(
            id = release.tag.hashCode().toLong(),
            tagName = release.tag,
            name = release.tag,
            publishedAt = release.published,
            description = null,
            assets = release.assets.map(::replayAsset),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
            isPrerelease = release.prerelease,
        )

    // ---- the pass ----

    // Repos where landing behind the newest release is the designed conservative behaviour, not a miss.
    private val conservativeFallbackRepos =
        setOf(
            "wxxsfxyzm/InstallerX-Revived", // variant lock: an offline/debug pick never gets its online/release sibling
            "iamr0s/InstallerX", // variant lock
            "VegaBobo/DSU-Sideloader", // variant lock
            "thunderbird/thunderbird-android", // two brands side by side (K9MAIL_*), each updating in its own family
            "topjohnwu/Magisk", // two brands side by side (manager-*)
            "godotengine/godot", // separate lines that must not cross: 3.x vs 4.x
            "tiann/KernelSU", // separate lines: a dev-build asset name
            "brunodev85/winlator", // separate lines: a Revision-suffixed name
        )

    @Test
    fun everyReplaySampleLandsOnTheNewestOwnRelease() {
        var samples = 0
        var missed = 0
        var older = 0
        var fellBack = 0
        var lostNewest = 0
        val fallbackRepos = mutableSetOf<String>()
        val offenders = mutableListOf<String>()

        corpus.forEach { (repo, allReleases) ->
            val releases = windowOf(allReleases)
            val apkReleases = releases.filter { it.apkNames.isNotEmpty() }
            if (apkReleases.isEmpty()) return@forEach
            val newestApk = apkReleases.first()

            // An app must never lose its own newest release to the ownership check (the 26.09 -> 26.09.8ede272 defect).
            val newestMatch = replayPick(releases, newestApk.tag, newestApk.apkNames.first())
            if (newestMatch == null || newestMatch.tag != newestApk.tag) {
                lostNewest++
                offenders += "$repo: newest ${newestApk.tag} -> ${newestMatch?.tag ?: "none"}"
            }

            apkReleases.forEach { installed ->
                val matched = replayPick(releases, installed.tag, installed.apkNames.first())
                samples++

                val ordered = { a: String, b: String -> a.isNotEmpty() && b.isNotEmpty() && a < b }

                when {
                    matched == null -> {
                        missed++
                        offenders += "$repo ${installed.tag} -> none (newest apk ${newestApk.tag})"
                    }

                    ordered(matched.published, installed.published) -> {
                        older++
                        offenders += "$repo ${installed.tag} -> ${matched.tag} (points at an older release)"
                    }

                    matched.tag != newestApk.tag && ordered(matched.published, newestApk.published) -> {
                        fellBack++
                        fallbackRepos += repo
                        offenders += "$repo ${installed.tag} -> ${matched.tag} (behind newest apk ${newestApk.tag})"
                    }
                }
            }
        }

        println("=== asset replay: samples=$samples missed=$missed older=$older fellBack=$fellBack lostNewest=$lostNewest ===")
        offenders.take(200).forEach { println("  $it") }

        assertTrue(samples >= 1300, "corpus did not load: samples=$samples")
        assertEquals(0, missed, "releases were skipped entirely")
        assertEquals(0, older, "an update pointed at an older release")
        assertEquals(0, lostNewest, "an app lost its own newest release")
        assertEquals(
            emptySet(),
            fallbackRepos - conservativeFallbackRepos,
            "a fallback appeared outside the conservative set; check the offenders above",
        )
    }
}
