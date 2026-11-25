package zed.rainxch.tweaks.presentation.model

import zed.rainxch.core.domain.model.installation.InstallerAttribution
import zed.rainxch.core.domain.model.installation.PresetKey

data class InstallerAttributionUi(
    val preset: PresetKey? = null,
    val customPackageName: String? = null,
) {
    val isSystemDefault: Boolean get() = preset == null && customPackageName == null

    val isCustom: Boolean get() = customPackageName != null
}

fun InstallerAttribution.toUi(): InstallerAttributionUi =
    when (this) {
        InstallerAttribution.SystemDefault -> InstallerAttributionUi()
        is InstallerAttribution.Preset -> InstallerAttributionUi(preset = key)
        is InstallerAttribution.Custom -> InstallerAttributionUi(customPackageName = packageName)
    }
