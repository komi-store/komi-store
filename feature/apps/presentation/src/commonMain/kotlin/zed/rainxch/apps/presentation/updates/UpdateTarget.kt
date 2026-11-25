package zed.rainxch.apps.presentation.updates

import zed.rainxch.core.domain.model.account.github.GithubAsset

data class UpdateTarget(
    val tag: String,
    val releaseId: Long?,
    val asset: GithubAsset,
    val fromStoredMatch: Boolean,
) {
    val extension: String get() = asset.name.substringAfterLast('.', "").lowercase()
}
