package zed.rainxch.apps.domain.repository

import zed.rainxch.apps.domain.model.ImportResult

interface AppsBackup {
    suspend fun exportApps(): String

    suspend fun exportObtainium(): String

    suspend fun importApps(json: String): ImportResult
}
