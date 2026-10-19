package zed.rainxch.core.domain

import android.os.Build
import zed.rainxch.core.domain.model.system.Platform
import java.util.Locale

actual fun getPlatform(): Platform = Platform.ANDROID

actual fun getOsVersion(): String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

actual fun getSystemLocaleTag(): String = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() } ?: "und"
