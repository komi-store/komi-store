package zed.rainxch.core.domain.network

import zed.rainxch.core.domain.model.mirror.MirrorConfig

interface MirrorLatencyProbe {

    suspend fun probe(mirrors: List<MirrorConfig>): Map<String, Int>
}