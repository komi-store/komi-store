package zed.rainxch.core.data.services

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import zed.rainxch.core.data.download.PartialNaming
import zed.rainxch.core.data.network.DesktopDigestVerifier
import zed.rainxch.core.domain.utils.AssetFileName

class OrphanSweepTest {

    private val body = ByteArray(64) { it.toByte() }

    @Test
    fun a_finished_download_whose_name_looks_like_a_partial_survives_the_sweep() =
        runBlocking {
            withTempDownloadsDir { dir ->
                val finished = AssetFileName.scoped("owner", "repo", "foo.part")
                File(dir, finished).writeBytes(body)

                val downloader = newDownloader(dir)
                val removed = downloader.reclaimOrphanedPartials(claimedNames = emptySet())

                assertTrue(
                    File(dir, finished).exists(),
                    "a finished download must never be collected, however partial-like its name",
                )
                assertEquals(0, removed, "nothing in this directory is a partial")
            }
        }

    @Test
    fun a_lone_sidecar_is_collected_without_touching_a_finished_download() =
        runBlocking {
            withTempDownloadsDir { dir ->
                val finished = AssetFileName.scoped("owner", "repo", "foo.part")
                File(dir, finished).writeBytes(body)
                val sidecar = PartialNaming.meta(finished)
                File(dir, sidecar).writeText("assetId=7\ndigest=\nsize=64\n")

                val downloader = newDownloader(dir)
                downloader.reclaimOrphanedPartials(claimedNames = emptySet())

                assertFalse(File(dir, sidecar).exists(), "an orphan sidecar is still collected")
                assertTrue(
                    File(dir, finished).exists(),
                    "collecting the sidecar must not take the finished file with it",
                )
            }
        }

    @Test
    fun a_legacy_partial_is_still_collected_by_its_shape() =
        runBlocking {
            withTempDownloadsDir { dir ->
                val asset = AssetFileName.scoped("owner", "repo", "app.apk")
                val legacy = "$asset.part-3f2b1c4e-5a6d-4f80-9b1c-2d3e4f5a6b7c"
                File(dir, legacy).writeBytes(ByteArray(8))

                val downloader = newDownloader(dir)
                val removed = downloader.reclaimOrphanedPartials(claimedNames = emptySet())

                assertFalse(File(dir, legacy).exists(), "legacy partials are collected by shape")
                assertEquals(1, removed)
            }
        }

    @Test
    fun a_claimed_half_pair_is_left_alone() =
        runBlocking {
            withTempDownloadsDir { dir ->
                val asset = AssetFileName.scoped("owner", "repo", "app.apk")
                val sidecar = PartialNaming.meta(asset)
                File(dir, sidecar).writeText("assetId=7\ndigest=\nsize=64\n")

                val downloader = newDownloader(dir)
                val removed = downloader.reclaimOrphanedPartials(claimedNames = setOf(asset))

                assertTrue(File(dir, sidecar).exists(), "a claim protects the pair")
                assertEquals(0, removed)
            }
        }

    private fun newDownloader(dir: File) =
        DesktopDownloader(
            files = FakeFileLocations(dir),
            tokenStore = FakeTokenStore(),
            digestVerifier = DesktopDigestVerifier(),
        )

    private suspend fun withTempDownloadsDir(block: suspend (File) -> Unit) {
        val dir = Files.createTempDirectory("komi-orphan-sweep").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
