package zed.rainxch.core.data.local

import zed.rainxch.core.domain.system.DesktopOs
import java.io.File

object DesktopAppDataPaths {
    private const val APP_DIR_NAME = "Komi-Store"

    fun appDataDir(): File {
        val home = File(System.getProperty("user.home"))
        val dir =
            when {
                DesktopOs.isMac -> {
                    File(home, "Library/Application Support/$APP_DIR_NAME")
                }

                DesktopOs.isWindows -> {
                    val appData =
                        System.getenv("APPDATA")?.let(::File)
                            ?: System.getenv("LOCALAPPDATA")?.let(::File)
                            ?: File(home, "AppData/Roaming")
                    File(appData, APP_DIR_NAME)
                }

                else -> {
                    val dataHome =
                        System.getenv("XDG_DATA_HOME")?.let(::File)
                            ?: File(home, ".local/share")
                    File(dataHome, APP_DIR_NAME)
                }
            }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // KSafe's JVM default baseDir is ~/.eu_anifantakis_ksafe, which is not reliably
    // persistent inside the Flatpak sandbox (writes land off the per-app tree and are
    // silently swallowed). Pin it to the same persistent dir Room uses. One-time,
    // best-effort copy of any legacy store so existing desktop users keep their settings.
    @Synchronized
    fun ksafeBaseDir(): File {
        val target = appDataDir()
        runCatching {
            val marker = File(target, ".ksafe_migrated")
            if (!marker.exists()) {
                val legacy = File(System.getProperty("user.home"), ".eu_anifantakis_ksafe")
                if (legacy.isDirectory) {
                    legacy.copyRecursively(target, overwrite = false)
                }
                marker.createNewFile()
            }
        }
        return target
    }

    fun migrateFromTmpIfNeeded(filename: String): Boolean {
        val newFile = File(appDataDir(), filename)
        if (newFile.exists()) return false
        val legacyFile = File(System.getProperty("java.io.tmpdir"), filename)
        if (!legacyFile.exists()) return false
        return try {
            legacyFile.copyTo(newFile, overwrite = false)
            true
        } catch (_: Exception) {
            false
        }
    }
}
