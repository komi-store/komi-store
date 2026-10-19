package zed.rainxch.core.domain.model.transfer

import kotlinx.serialization.Serializable

@Serializable
data class ExportedAppList(
    val version: Int = 4,
    val exportedAt: Long = 0L,
    val apps: List<ExportedApp> = emptyList(),
)
