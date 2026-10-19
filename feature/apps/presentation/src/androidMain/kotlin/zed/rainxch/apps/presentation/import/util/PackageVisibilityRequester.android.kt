package zed.rainxch.apps.presentation.import.util

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GRANT_THRESHOLD = 30

@Composable
actual fun rememberPackageVisibilityRequester(): PackageVisibilityRequester {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidPackageVisibilityRequester(context) }
}

private class AndroidPackageVisibilityRequester(
    private val context: Context,
) : PackageVisibilityRequester {
    override suspend fun isGranted(): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext true
            val pm = context.packageManager
            val visible = runCatching { pm.getInstalledPackages(0) }.getOrElse { emptyList() }
            visible.size >= GRANT_THRESHOLD
        }

    override suspend fun requestOrOpenSettings(): Boolean = false
}
