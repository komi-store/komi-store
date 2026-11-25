package zed.rainxch.details.presentation.mappers

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.account.github.preReleaseLabel
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.PlatformRelease
import zed.rainxch.core.domain.utils.assetPlatformOf
import zed.rainxch.details.presentation.model.AssetUi
import zed.rainxch.details.presentation.model.PlatformHandoffUi
import zed.rainxch.details.presentation.model.PlatformReleaseUi
import zed.rainxch.details.presentation.model.ReleaseUi

internal fun GithubRelease.toUi(): ReleaseUi =
    ReleaseUi(
        id = id,
        tagName = tagName,
        name = name,
        publishedAt = publishedAt,
        description = description,
        isPreRelease = isEffectivelyPreRelease(),
        preReleaseLabel = preReleaseLabel(),
    )

internal fun GithubAsset.toUi(): AssetUi =
    AssetUi(
        id = id,
        name = name,
        size = size,
        downloadUrl = downloadUrl,
        platform = assetPlatformOf(name),
        variantTag = AssetVariant.extract(name),
    )

internal fun PlatformRelease.toUi(): PlatformReleaseUi = PlatformReleaseUi(platform = platform, release = release.toUi())

internal fun PlatformRelease.toHandoffUi(): PlatformHandoffUi =
    PlatformHandoffUi(
        platform = platform,
        tagName = release.tagName,
        publishedAt = release.publishedAt,
        assets =
            release.assets
                .filter { assetPlatformOf(it.name) == platform }
                .map { it.toUi() }
                .toImmutableList(),
    )
