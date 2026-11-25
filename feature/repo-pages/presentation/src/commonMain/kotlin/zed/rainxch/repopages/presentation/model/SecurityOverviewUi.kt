package zed.rainxch.repopages.presentation.model

import kotlinx.collections.immutable.ImmutableList

data class SecurityOverviewUi(
    val advisories: ImmutableList<SecurityAdvisoryUi>,
    val securityPolicyMarkdown: String?,
)
