package zed.rainxch.core.data.services.installer

import zed.rainxch.core.data.services.dhizuku.DhizukuServiceManager
import zed.rainxch.core.data.services.root.RootServiceManager
import zed.rainxch.core.data.services.shizuku.ShizukuServiceManager

class SilentInstallBackends(
    val shizuku: ShizukuServiceManager,
    val dhizuku: DhizukuServiceManager,
    val root: RootServiceManager,
)
