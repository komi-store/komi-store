package zed.rainxch.core.domain.model.account.github

import zed.rainxch.core.domain.utils.PreReleaseTags

// Only the API flag and the tag decide: the release name is free prose ("beta fixes") that hid real stable updates.
fun GithubRelease.isEffectivelyPreRelease(): Boolean =
    isPrerelease ||
        PreReleaseTags.isPreReleaseTag(tagName)

fun GithubRelease.preReleaseLabel(): String? =
    PreReleaseTags.preReleaseMarkerLabel(tagName)
        ?: PreReleaseTags.preReleaseMarkerLabel(name)
