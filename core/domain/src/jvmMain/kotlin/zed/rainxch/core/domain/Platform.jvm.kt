package zed.rainxch.core.domain

import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.system.DesktopOs
import java.util.Locale

actual fun getPlatform(): Platform =
    when {
        DesktopOs.isWindows -> Platform.WINDOWS
        DesktopOs.isMac -> Platform.MACOS
        else -> Platform.LINUX
    }

actual fun getOsVersion(): String = System.getProperty("os.version") ?: "unknown"

actual fun getSystemLocaleTag(): String = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() } ?: "und"
