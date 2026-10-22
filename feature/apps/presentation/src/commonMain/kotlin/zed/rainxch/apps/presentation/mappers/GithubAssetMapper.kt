package zed.rainxch.apps.presentation.mappers

import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.apps.presentation.model.GithubUserUi
import zed.rainxch.core.domain.model.account.github.GithubAsset

fun GithubAsset.toUi(): GithubAssetUi =
    GithubAssetUi(
        id = id,
        name = name,
        contentType = contentType,
        size = size,
        downloadUrl = downloadUrl,
        uploader =
            uploader?.let {
                GithubUserUi(
                    id = it.id,
                    login = it.login,
                    avatarUrl = it.avatarUrl,
                    htmlUrl = it.htmlUrl,
                )
            },
    )
