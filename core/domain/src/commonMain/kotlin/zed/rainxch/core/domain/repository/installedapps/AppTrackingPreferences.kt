package zed.rainxch.core.domain.repository.installedapps

interface AppTrackingPreferences {
    suspend fun setIncludePreReleases(
        packageName: String,
        enabled: Boolean,
    )

    suspend fun setUpdateCheckEnabled(
        packageName: String,
        enabled: Boolean,
    )

    suspend fun setAssetFilter(
        packageName: String,
        regex: String?,
        fallbackToOlderReleases: Boolean,
    )

    suspend fun setPreferredVariant(
        packageName: String,
        variant: String?,
        tokens: String? = null,
        glob: String? = null,
        pickedIndex: Int? = null,
        siblingCount: Int? = null,
    )

    suspend fun clearPreferredVariant(packageName: String)

    suspend fun setSkippedReleaseTag(
        packageName: String,
        tag: String?,
    )
}
