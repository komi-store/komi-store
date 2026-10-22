# Platform notes

## Installing apps on Android

Silent installs go through Shizuku (`ShizukuProvider`, then AIDL, then `pm install -S`), Dhizuku, or root through libsu (`services/root/RootServiceManager`). `services/installer/SilentInstallerDispatcher` in `core/data/src/androidMain` picks one from the installer setting and falls back to the standard installer on failure.

The Shizuku and Dhizuku AIDL interfaces live in `core/data-aidl`, a plain Android library, because the Android KMP library plugin (`com.android.kotlin.multiplatform.library`) has no AIDL support. They keep their `zed.rainxch.core.data.services.*` packages, which the Shizuku keep rules in `androidApp/proguard-rules.pro` name.

## Desktop logs

`CrashReporter.install()`, called at the start of `DesktopApp.main`, tees stdout and stderr to a rotating `session.log` and writes `crash-<ts>.log` on an uncaught exception.

| OS | Path |
|---|---|
| macOS | `~/Library/Logs/Komi-Store/` |
| Windows | `%LOCALAPPDATA%/Komi-Store/logs/` |
| Linux | `$XDG_STATE_HOME/Komi-Store/logs/` (or `~/.local/state/Komi-Store/logs/`) |

Android logs go to Logcat.

## Encrypted storage

KSafe provides AES-256-GCM, backed by the hardware Keystore on Android. Persisted credentials and preferences go through the extension functions in `core/data/secure/KSafeSafe.kt` (`safeGet`, `safePut`, `safeDelete`, `safeGetFlow`), which log and return null or false on a transient failure instead of throwing through coroutine scopes. A few raw calls remain, for example in `HostTokenRepositoryImpl`.

## Gradle

Configuration cache and build cache are on. The Gradle daemon gets 4 GB of heap and the Kotlin daemon 3 GB (`gradle.properties`). Library versions live in `gradle/libs.versions.toml`.
