package zed.rainxch.core.domain.model.apk

data class ApkPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val appName: String,
    val signingFingerprint: String?,
    // The APK file's own mtime, when the reader can report it: the parked-file sweep uses it to
    // tell "the system was installed after this file finished" apart from "this file is still
    // what the user has to install". Null where it cannot be read.
    val fileLastModified: Long? = null,
)
