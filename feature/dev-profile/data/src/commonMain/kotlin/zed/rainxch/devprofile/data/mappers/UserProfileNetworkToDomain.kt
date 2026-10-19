package zed.rainxch.devprofile.data.mappers

import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.devprofile.domain.model.DeveloperProfile

fun UserProfileNetwork.toDeveloperProfile(): DeveloperProfile =
    DeveloperProfile(
        login = login,
        name = name,
        avatarUrl = avatarUrl,
        bio = bio,
        company = company,
        location = location,
        email = email,
        blog = blog,
        twitterUsername = twitterUsername,
        publicRepos = publicRepos,
        publicGists = publicGists ?: 0,
        followers = followers,
        following = following,
        createdAt = createdAt.orEmpty(),
        updatedAt = updatedAt.orEmpty(),
        htmlUrl = htmlUrl,
        userType = type,
    )
