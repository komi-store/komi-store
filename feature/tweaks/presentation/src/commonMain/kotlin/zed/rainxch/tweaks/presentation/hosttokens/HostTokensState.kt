package zed.rainxch.tweaks.presentation.hosttokens

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.StringResource
import zed.rainxch.core.domain.model.account.ForgeKind

data class HostTokensState(
    val tokens: ImmutableList<HostTokenUi> = persistentListOf(),
    val validationByHost: ImmutableMap<String, ValidationLine> = persistentMapOf(),
    val validatingHosts: ImmutableSet<String> = persistentSetOf(),
    val draftMode: DraftMode = DraftMode.Closed,
    val draftForge: ForgeKind? = null,
    val draftHost: String = "",
    val draftHostNormalized: String = "",
    val draftToken: String = "",
    val draftDisplayName: String = "",
    val draftHostError: StringResource? = null,
    val draftTokenError: StringResource? = null,
    val draftDetectedTokenKind: String? = null,
    val isLoading: Boolean = false,
    val isOAuthSignedInToGithub: Boolean = false,
    val pendingUndoDelete: HostTokenUi? = null,
)
