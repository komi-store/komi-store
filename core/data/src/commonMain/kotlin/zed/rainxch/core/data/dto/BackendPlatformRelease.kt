package zed.rainxch.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class BackendPlatformRelease(
    val tag: String? = null,
    val publishedAt: String? = null,
)
