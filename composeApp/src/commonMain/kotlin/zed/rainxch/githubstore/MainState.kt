package zed.rainxch.githubstore

import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.model.error.RateLimitInfo

data class MainState(
    val isLoggedIn: Boolean = false,
    val rateLimitInfo: RateLimitInfo? = null,
    val showRateLimitDialog: Boolean = false,
    val showSessionExpiredDialog: Boolean = false,
    val personality: AppPersonality = AppPersonality.MANGA,
    val accent: AccentId = AccentId.CRIMSON,
    val mangaPaper: MangaPaperId = MangaPaperId.DAY,
    val isAmoledTheme: Boolean = false,
    val isDarkTheme: Boolean? = null,
    val isScrollbarEnabled: Boolean = false,
    val contentWidth: ContentWidth = ContentWidth.COMPACT,
    val appLanguageTag: String? = null,
    val signedInAvatarUrl: String? = null,
    val isAppearanceLoaded: Boolean = false,
)
