package zed.rainxch.repopages.domain.model

data class SecurityOverview(
    val advisories: List<SecurityAdvisory>,
    val securityPolicyMarkdown: String?,
)
