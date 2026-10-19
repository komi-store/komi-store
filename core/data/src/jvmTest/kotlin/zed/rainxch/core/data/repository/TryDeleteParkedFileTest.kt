package zed.rainxch.core.data.repository

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
