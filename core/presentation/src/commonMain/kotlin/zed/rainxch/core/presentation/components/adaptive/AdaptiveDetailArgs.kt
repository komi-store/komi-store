package zed.rainxch.core.presentation.components.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Immutable
data class AdaptiveDetailArgs(
    val repositoryId: Long = -1L,
    val owner: String? = null,
    val repo: String? = null,
    val isComingFromUpdate: Boolean = false,
    val sourceHost: String? = null,
    val initialAssetName: String? = null,
    val packageName: String? = null,
)
