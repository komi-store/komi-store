package zed.rainxch.tweaks.presentation.mirror

import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.model.mirror.MirrorStatus
import zed.rainxch.core.domain.model.mirror.MirrorType

data class MirrorUi(
    val id: String,
    val name: String,
    val urlTemplate: String?,
    val type: MirrorType,
    val status: MirrorStatus,
    val latencyMs: Int?,
)

fun MirrorConfig.toUi(): MirrorUi =
    MirrorUi(
        id = id,
        name = name,
        urlTemplate = urlTemplate,
        type = type,
        status = status,
        latencyMs = latencyMs,
    )
