package zed.rainxch.core.data.download

object ForegroundPrimary {

    fun choose(
        activePackages: Collection<String>,
        activeSince: Map<String, Long>,
    ): String? =
        activePackages.maxWithOrNull(
            compareBy(
                { activeSince[it] ?: 0L },
                { it },
            ),
        )
}
