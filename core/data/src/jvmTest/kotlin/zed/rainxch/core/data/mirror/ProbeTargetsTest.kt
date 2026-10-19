package zed.rainxch.core.data.mirror

import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.model.mirror.MirrorStatus
import zed.rainxch.core.domain.model.mirror.MirrorType
import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeTargetsTest {
    private val readme = "https://raw.githubusercontent.com/octocat/Hello-World/master/README"

    @Test
    fun direct_is_probed_against_github_itself() {
        assertEquals(listOf("direct" to readme), probeTargets(listOf(mirror("direct", null))))
    }

    @Test
    fun a_url_template_wraps_the_probe_file() {
        assertEquals(
            listOf("ghfast_top" to "https://ghfast.top/$readme"),
            probeTargets(listOf(mirror("ghfast_top", "https://ghfast.top/{url}"))),
        )
    }

    @Test
    fun a_decomposed_template_gets_the_probe_file_parts() {
        assertEquals(
            listOf("fastly_jsdelivr" to "https://fastly.jsdelivr.net/gh/octocat/Hello-World@master/README"),
            probeTargets(
                listOf(mirror("fastly_jsdelivr", "https://fastly.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}")),
            ),
        )
    }

    @Test
    fun a_template_without_placeholders_is_skipped_instead_of_probing_github_directly() {
        assertEquals(emptyList(), probeTargets(listOf(mirror("broken", "https://example.com/proxy"))))
    }

    private fun mirror(
        id: String,
        template: String?,
    ) = MirrorConfig(
        id = id,
        name = id,
        urlTemplate = template,
        type = MirrorType.COMMUNITY,
        status = MirrorStatus.OK,
        latencyMs = null,
        lastCheckedAt = null,
    )
}
