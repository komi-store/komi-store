package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepoIdCodecTest {
    @Test
    fun a_github_repo_has_no_source_host() {
        assertNull(RepoIdCodec.sourceHostOf(123L, "https://github.com/ente/ente"))
    }

    @Test
    fun a_codeberg_repo_takes_its_host_from_the_url() {
        val id = RepoIdCodec.encode("codeberg.org", 42L)
        assertEquals("codeberg.org", RepoIdCodec.sourceHostOf(id, "https://codeberg.org/raincord/rainManager"))
    }

    @Test
    fun a_custom_forge_host_is_kept_as_is() {
        val id = RepoIdCodec.encode("codefloe.com", 7L)
        assertEquals("codefloe.com", RepoIdCodec.sourceHostOf(id, "https://CodeFloe.com/owner/app"))
    }
}
