package zed.rainxch.core.domain.model.mirror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FastestMirrorTest {
    private val direct = mirror("direct", template = null)
    private val ghfast = mirror("ghfast_top", template = "https://ghfast.top/{url}")
    private val ghProxy = mirror("gh_proxy_com", template = "https://gh-proxy.com/{url}")
    private val jsdelivr =
        mirror(
            "fastly_jsdelivr",
            template = "https://fastly.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}",
            kinds = setOf(TrafficKind.RAW_FILE),
        )
    private val all = listOf(direct, ghfast, ghProxy, jsdelivr)

    @Test
    fun direct_wins_when_it_is_the_fastest() {
        val fastest = fastestReleaseMirror(all, mapOf("direct" to 760, "gh_proxy_com" to 980, "ghfast_top" to 1450))

        assertEquals(direct to 760, fastest)
    }

    @Test
    fun a_mirror_wins_when_it_beats_direct() {
        val fastest = fastestReleaseMirror(all, mapOf("direct" to 4200, "gh_proxy_com" to 390))

        assertEquals(ghProxy to 390, fastest)
    }

    @Test
    fun a_raw_file_only_mirror_never_wins_for_release_assets() {
        val fastest = fastestReleaseMirror(all, mapOf("fastly_jsdelivr" to 56, "ghfast_top" to 980))

        assertEquals(ghfast to 980, fastest)
    }

    @Test
    fun nothing_measured_means_no_pick() {
        assertNull(fastestReleaseMirror(all, emptyMap()))
        assertNull(fastestReleaseMirror(all, mapOf("fastly_jsdelivr" to 56)))
    }

    private fun mirror(
        id: String,
        template: String?,
        kinds: Set<TrafficKind> = setOf(TrafficKind.RELEASE_ASSET, TrafficKind.RAW_FILE),
    ) = MirrorConfig(
        id = id,
        name = id,
        urlTemplate = template,
        type = if (id == "direct") MirrorType.OFFICIAL else MirrorType.COMMUNITY,
        status = MirrorStatus.OK,
        latencyMs = null,
        lastCheckedAt = null,
        trafficKinds = kinds,
    )
}
