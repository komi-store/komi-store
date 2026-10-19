package zed.rainxch.core.domain.system

import zed.rainxch.core.domain.model.apk.ApkInspection

interface ApkInspector {
    suspend fun inspectFile(filePath: String): ApkInspection?

    suspend fun inspectInstalled(packageName: String): ApkInspection?
}
