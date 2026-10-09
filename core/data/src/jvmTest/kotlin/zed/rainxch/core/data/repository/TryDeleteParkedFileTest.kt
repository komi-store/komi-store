package zed.rainxch.core.data.repository

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The one production step a discard cannot fake away: whether the pointer is safe to clear after
// the file's delete was attempted. A failed delete that leaves the file on disk must not read as
// success, or the file is orphaned with nothing naming it.
class TryDeleteParkedFileTest {

    @Test
    fun a_deleted_file_is_gone() {
        val file = Files.createTempFile("komi-parked", ".apk").toFile()

        try {
            assertEquals(ParkedFileDisposal.Gone, tryDeleteParkedFile(file.absolutePath))
            assertFalse(file.exists())
        } finally {
            file.delete()
        }
    }

    @Test
    fun a_file_the_installer_already_took_is_gone() {
        val dir = Files.createTempDirectory("komi-parked").toFile()
        try {
            val taken = dir.resolve("taken.apk")

            assertEquals(ParkedFileDisposal.Gone, tryDeleteParkedFile(taken.absolutePath))
        } finally {
            dir.delete()
        }
    }

    @Test
    fun a_file_that_cannot_be_deleted_survives() {
        // A non-empty directory cannot be deleted by File.delete() on any platform, which stages
        // the one outcome the caller must keep the pointer for.
        val dir = Files.createTempDirectory("komi-parked").toFile()
        try {
            val payload = dir.resolve("still-there")
            payload.writeText("x")

            assertEquals(ParkedFileDisposal.Survived, tryDeleteParkedFile(dir.absolutePath))
            assertTrue(dir.exists())
        } finally {
            dir.resolve("still-there").delete()
            dir.delete()
        }
    }
}
