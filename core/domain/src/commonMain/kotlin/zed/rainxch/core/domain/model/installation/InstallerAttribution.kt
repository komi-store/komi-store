package zed.rainxch.core.domain.model.installation
import kotlinx.serialization.Serializable

@Serializable
sealed interface InstallerAttribution {
    @Serializable
    data object SystemDefault : InstallerAttribution

    @Serializable
    data class Preset(
        val key: PresetKey,
    ) : InstallerAttribution

    @Serializable
    data class Custom(
        val packageName: String,
    ) : InstallerAttribution

    fun resolvePackageName(): String? =
        when (this) {
            SystemDefault -> null
            is Preset -> key.packageName
            is Custom -> packageName.trim().takeIf { it.isNotBlank() }
        }
}
