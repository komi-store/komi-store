package zed.rainxch.apps.presentation.import.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPackageVisibilityRequester(): PackageVisibilityRequester = remember { JvmPackageVisibilityRequester }

private object JvmPackageVisibilityRequester : PackageVisibilityRequester {
    override suspend fun isGranted(): Boolean = true

    override suspend fun requestOrOpenSettings(): Boolean = true
}
