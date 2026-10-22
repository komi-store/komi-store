package zed.rainxch.core.data.mirror

import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.model.mirror.MirrorStatus
import zed.rainxch.core.domain.model.mirror.MirrorType
import zed.rainxch.core.domain.model.mirror.TrafficKind

object BundledMirrors {
    private val FULL_PROXY_KINDS = setOf(TrafficKind.RELEASE_ASSET, TrafficKind.RAW_FILE)
    private val RAW_FILE_ONLY = setOf(TrafficKind.RAW_FILE)

    val ALL: List<MirrorConfig> =
        listOf(
            entry(
                id = "direct",
                name = "Direct GitHub",
                template = null,
                type = MirrorType.OFFICIAL,
            ),
            entry(
                id = "fastly_jsdelivr",
                name = "fastly.jsdelivr.net",
                template = "https://fastly.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}",
                type = MirrorType.COMMUNITY,
                trafficKinds = RAW_FILE_ONLY,
            ),
            entry(
                id = "ghfast_top",
                name = "ghfast.top",
                template = "https://ghfast.top/{url}",
                type = MirrorType.COMMUNITY,
            ),
            entry(
                id = "gh_proxy_com",
                name = "gh-proxy.com",
                template = "https://gh-proxy.com/{url}",
                type = MirrorType.COMMUNITY,
            ),
            entry(
                id = "moeyy_xyz",
                name = "github.moeyy.xyz",
                template = "https://github.moeyy.xyz/{url}",
                type = MirrorType.COMMUNITY,
            ),
            entry(
                id = "ghps_cc",
                name = "ghps.cc",
                template = "https://ghps.cc/{url}",
                type = MirrorType.COMMUNITY,
            ),
            entry(
                id = "gh_99988866_xyz",
                name = "gh.api.99988866.xyz",
                template = "https://gh.api.99988866.xyz/{url}",
                type = MirrorType.COMMUNITY,
            ),
        )

    private fun entry(
        id: String,
        name: String,
        template: String?,
        type: MirrorType,
        trafficKinds: Set<TrafficKind> = FULL_PROXY_KINDS,
    ) = MirrorConfig(
        id = id,
        name = name,
        urlTemplate = template,
        type = type,
        status = MirrorStatus.UNKNOWN,
        latencyMs = null,
        lastCheckedAt = null,
        trafficKinds = trafficKinds,
    )
}
