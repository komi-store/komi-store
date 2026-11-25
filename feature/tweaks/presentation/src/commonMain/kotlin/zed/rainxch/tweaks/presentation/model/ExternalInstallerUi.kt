package zed.rainxch.tweaks.presentation.model

import zed.rainxch.core.domain.model.installation.ExternalInstallerApp

data class ExternalInstallerUi(
    val packageName: String,
    val label: String,
)

fun ExternalInstallerApp.toUi(): ExternalInstallerUi = ExternalInstallerUi(packageName = packageName, label = label)
