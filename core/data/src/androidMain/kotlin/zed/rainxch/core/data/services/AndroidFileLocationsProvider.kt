package zed.rainxch.core.data.services

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

class AndroidFileLocationsProvider(
    private val context: Context,
) : zed.rainxch.core.data.services.FileLocationsProvider {
    @Volatile
    private var cachedDownloadsDir: String? = null

    override fun appDownloadsDir(): String =
        cachedDownloadsDir ?: synchronized(this) {
            cachedDownloadsDir ?: resolveDownloadsDir().also { cachedDownloadsDir = it }
        }

    private fun resolveDownloadsDir(): String {
        val candidates =
            listOf(
                { runCatching { context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) }.getOrNull() },
                { runCatching { context.getExternalFilesDir(null)?.let { File(it, "downloads") } }.getOrNull() },
                { File(context.filesDir, "downloads") },
            )
        for (factory in candidates) {
            val dir = factory.invoke() ?: continue
            val ready = ensureUsable(dir)
            if (ready != null) {
                if (dir != context.filesDir) {
                    Log.i(TAG, "Downloads dir resolved: ${ready.absolutePath}")
                }
                return ready.absolutePath
            }
            Log.w(TAG, "Downloads dir candidate unusable: ${dir.absolutePath}")
        }

        val fallback = File(context.filesDir, "downloads")
        fallback.mkdirs()
        return fallback.absolutePath
    }

    private fun ensureUsable(dir: File): File? =
        try {
            val exists = dir.exists() || dir.mkdirs() || dir.exists()
            dir.takeIf { exists && it.canWrite() }
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed probing ${dir.absolutePath}: ${e.message}")
            null
        }

    override fun userDownloadsDir(): String = appDownloadsDir()

    override fun setExecutableIfNeeded(path: String) = Unit

    override fun getCacheSizeBytes(): Long {
        val dir = File(appDownloadsDir())
        return calculateDirSize(dir)
    }

    override fun clearCacheFiles(): Boolean {
        val dir = File(appDownloadsDir())
        return deleteDirectoryContents(dir)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDirectoryContents(dir: File): Boolean {
        if (!dir.exists()) return true
        var allDeleted = true
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (!deleteDirectoryContents(file)) allDeleted = false
                if (!file.delete()) allDeleted = false
            } else {
                if (!file.delete()) allDeleted = false
            }
        }
        return allDeleted
    }

    private companion object {
        const val TAG = "AndroidFileLocations"
    }
}
