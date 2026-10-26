package zed.rainxch.core.data.local

import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.system.DesktopOs
import java.io.File
import java.io.IOException

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

    // KSafe's default ~/.eu_anifantakis_ksafe is not persistent under Flatpak; pin it beside Room and copy any legacy store once.
    @Synchronized
    fun ksafeBaseDir(): File {
        val target = appDataDir()
        val marker = File(target, ".ksafe_migrated")
        if (marker.exists()) return target
        try {
            val legacy = File(System.getProperty("user.home"), ".eu_anifantakis_ksafe")
            if (legacy.isDirectory) {
                legacy.copyRecursively(target, overwrite = false)
            }
            marker.createNewFile()
        } catch (e: IOException) {
            Logger.w(e) { "Could not copy the legacy KSafe store; retrying on the next launch" }
        }
        return target
    }

    fun migrateFromTmpIfNeeded(filename: String): Boolean {
        val newFile = File(appDataDir(), filename)
        val legacyFile = File(System.getProperty("java.io.tmpdir"), filename)
        if (newFile.exists() || !legacyFile.exists()) return false
        return try {
            legacyFile.copyTo(newFile, overwrite = false)
            true
        } catch (e: IOException) {
            Logger.w(e) { "Could not move $filename out of the temp folder" }
            false
        }
    }
}
