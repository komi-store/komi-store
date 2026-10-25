package zed.rainxch.core.presentation.model

import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.presentation.components.cards.KomiLibraryStatus

// What a library row knows about a repo's app on this device: the package whose icon it shows, and
// whether any of the repo's installed apps has an update.
data class LibraryInstall(
    val packageName: String,
    val status: KomiLibraryStatus,
)

// Pending installs aren't on the device yet, so they don't count.
fun List<InstalledApp>.installsByRepo(): Map<Long, LibraryInstall> =
    filterNot { it.isPendingInstall }
        .groupBy { it.repoId }
        .mapValues { (_, apps) ->
            LibraryInstall(
                packageName = apps.first().packageName,
                status = if (apps.any { it.isUpdateAvailable }) KomiLibraryStatus.Update else KomiLibraryStatus.Installed,
            )
        }
