package zed.rainxch.feed.presentation.marks

import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.hasActualUpdate
import zed.rainxch.core.domain.model.installation.isReallyInstalled

data class LibraryMarks(
    val installedById: Map<Long, List<InstalledApp>> = emptyMap(),
    val favouriteIds: Set<Long> = emptySet(),
    val starredIds: Set<Long> = emptySet(),
    val currentUserLogin: String? = null,
) {
    fun isInstalled(repoId: Long): Boolean = installedById[repoId].orEmpty().any { it.isReallyInstalled() }

    fun isUpdateAvailable(repoId: Long): Boolean = installedById[repoId].orEmpty().any { it.hasActualUpdate() }

    fun isCurrentUser(ownerLogin: String): Boolean = currentUserLogin != null && ownerLogin.equals(currentUserLogin, ignoreCase = true)
}
