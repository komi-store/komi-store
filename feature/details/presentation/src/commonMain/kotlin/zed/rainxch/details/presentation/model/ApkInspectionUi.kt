package zed.rainxch.details.presentation.model

import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.core.domain.model.apk.ApkInspection

data class ApkInspectionUi(
    val appLabel: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val signingFingerprint: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val permissions: ImmutableList<ApkPermissionUi>,
    val mainActivity: String?,
    val activityCount: Int,
    val serviceCount: Int,
    val receiverCount: Int,
    val fileSizeBytes: Long?,
    val filePath: String?,
    val debuggable: Boolean,
    val source: ApkInspection.Source,
)
