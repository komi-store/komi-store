package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.AssetNetwork
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubUser

fun AssetNetwork.toDomain(): GithubAsset =
    GithubAsset(
        id = id,
        name = name,
        contentType = contentType ?: "application/octet-stream",
        size = size,
        downloadUrl = downloadUrl,
        uploader =
            uploader?.let {
                GithubUser(
                    id = it.id,
                    login = it.login,
                    avatarUrl = it.avatarUrl,
                    htmlUrl = it.htmlUrl,
                )
            },
        downloadCount = downloadCount,
        digest = digest,
    )
