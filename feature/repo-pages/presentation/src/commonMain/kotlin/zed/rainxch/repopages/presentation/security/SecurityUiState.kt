package zed.rainxch.repopages.presentation.security

import zed.rainxch.repopages.presentation.model.SecurityOverviewUi

data class SecurityUiState(
    val isLoading: Boolean = false,
    val overview: SecurityOverviewUi? = null,
    val errorMessage: String? = null,
)
