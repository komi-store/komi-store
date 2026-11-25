package zed.rainxch.tweaks.presentation.hosttokens

import zed.rainxch.core.domain.model.account.HostToken

data class HostTokenUi(
    val host: String,
    val token: String,
    val displayName: String?,
)

fun HostToken.toUi(): HostTokenUi = HostTokenUi(host = host, token = token, displayName = displayName)
