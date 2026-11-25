package zed.rainxch.details.presentation.handlers

import zed.rainxch.details.presentation.model.LogResult

internal data class InstallRequest(
    val downloadUrl: String,
    val assetName: String,
    val sizeBytes: Long,
    val releaseTag: String,
    val isUpdate: Boolean,
)

internal fun DetailsSession.appendLog(
    request: InstallRequest,
    result: LogResult,
) = appendLog(assetName = request.assetName, size = request.sizeBytes, tag = request.releaseTag, result = result)

internal fun InstallRequest.finishedResult(): LogResult = if (isUpdate) LogResult.Updated else LogResult.Installed
