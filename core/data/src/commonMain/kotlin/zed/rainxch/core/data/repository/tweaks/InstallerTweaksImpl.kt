package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.repository.tweaks.InstallerTweaks

internal class InstallerTweaksImpl(
    private val store: TweakStore,
) : InstallerTweaks {
    override fun getInstallerType(): Flow<InstallerType> =
        store.gatedGetFlow(K_INSTALLER_TYPE, "").map { InstallerType.fromName(it.ifEmpty { null }) }

    override suspend fun setInstallerType(type: InstallerType) {
        store.awaitMigration()
        store.ksafe.safePut(K_INSTALLER_TYPE, type.name)
    }

    override fun getExternalInstallerPackage(): Flow<String> = store.gatedGetFlow(K_EXTERNAL_INSTALLER, "")

    override suspend fun setExternalInstallerPackage(packageName: String) {
        store.awaitMigration()
        store.ksafe.safePut(K_EXTERNAL_INSTALLER, packageName)
    }

    override fun getInstallerAttribution(): Flow<zed.rainxch.core.domain.model.installation.InstallerAttribution> =
        store.gatedGetFlow(K_INSTALLER_ATTRIBUTION, "").map { decodeInstallerAttribution(it.ifEmpty { null }) }

    override suspend fun setInstallerAttribution(attribution: zed.rainxch.core.domain.model.installation.InstallerAttribution) {
        store.awaitMigration()
        store.ksafe.safePut(K_INSTALLER_ATTRIBUTION, encodeInstallerAttribution(attribution))
    }

    private fun decodeInstallerAttribution(raw: String?): zed.rainxch.core.domain.model.installation.InstallerAttribution {
        if (raw.isNullOrBlank()) return zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault
        val parts = raw.split(":", limit = 2)
        return when (parts[0]) {
            "preset" -> {
                val key =
                    parts.getOrNull(1)?.let {
                        zed.rainxch.core.domain.model.installation.PresetKey
                            .fromName(it)
                    }
                if (key != null) {
                    zed.rainxch.core.domain.model.installation.InstallerAttribution
                        .Preset(key)
                } else {
                    zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault
                }
            }

            "custom" -> {
                val name = parts.getOrNull(1).orEmpty()
                if (name.isNotBlank()) {
                    zed.rainxch.core.domain.model.installation.InstallerAttribution
                        .Custom(name)
                } else {
                    zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault
                }
            }

            else -> {
                zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault
            }
        }
    }

    private fun encodeInstallerAttribution(attribution: zed.rainxch.core.domain.model.installation.InstallerAttribution): String =
        when (attribution) {
            zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault -> ""
            is zed.rainxch.core.domain.model.installation.InstallerAttribution.Preset -> "preset:${attribution.key.name}"
            is zed.rainxch.core.domain.model.installation.InstallerAttribution.Custom -> "custom:${attribution.packageName.trim()}"
        }
}
