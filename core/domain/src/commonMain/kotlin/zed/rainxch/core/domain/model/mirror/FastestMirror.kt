package zed.rainxch.core.domain.model.mirror

fun fastestReleaseMirror(
    mirrors: List<MirrorConfig>,
    measuredLatencies: Map<String, Int>,
): Pair<MirrorConfig, Int>? =
    mirrors
        .filter { TrafficKind.RELEASE_ASSET in it.trafficKinds }
        .mapNotNull { mirror -> measuredLatencies[mirror.id]?.let { mirror to it } }
        .minByOrNull { it.second }
