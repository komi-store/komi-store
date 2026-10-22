package zed.rainxch.core.domain.model.account

data class SessionSnapshot(
    val isLoggedIn: Boolean,
    val profile: UserProfile?,
) {
    init {
        require(isLoggedIn || profile == null) {
            val carried = profile?.username
            "A signed-out snapshot cannot carry a profile: isLoggedIn=$isLoggedIn, " +
                "profile=$carried"
        }
    }
}
