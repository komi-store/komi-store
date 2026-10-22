# Platform notes

## Installing apps on Android

Silent installs go through Shizuku (`ShizukuProvider`, then AIDL, then `pm install -S`), Dhizuku, or root through libsu (`services/root/RootServiceManager`). `services/installer/SilentInstallerDispatcher` in `core/data/src/androidMain` picks one from the installer setting and falls back to the standard installer on failure.

The standard installer (setting Default) opens the system package installer directly when other APK installers are present and Android has no default for them, so no chooser appears. A handed-off install keeps `SystemInstallSerializer`'s gate until the package broadcast arrives; `MainActivity.onResume` releases it, because a cancelled installer screen sends no broadcast.

The Shizuku and Dhizuku AIDL interfaces live in `core/data-aidl`, a plain Android library, because the Android KMP library plugin (`com.android.kotlin.multiplatform.library`) has no AIDL support. They keep their `zed.rainxch.core.data.services.*` packages, which the Shizuku keep rules in `androidApp/proguard-rules.pro` name.

## Local network access

The app targets Android 17, which blocks traffic to LAN addresses (Wi-Fi or Ethernet; private, link-local, CGNAT and multicast ranges) without the `ACCESS_LOCAL_NETWORK` runtime permission. `MainActivity` resolves the hosts of the HTTP/SOCKS proxies, custom forges and the LibreTranslate URL (`LocalNetworkHosts.kt`). When any of them is on the LAN, it asks for the permission at launch, and asks again in the same session only when that set of hosts changes. After two denials Android stops showing the prompt. The address ranges are in `core/domain/.../network/LocalNetworkAddress.kt`. Loopback isn't a local network, so an on-device proxy at 127.0.0.1 needs nothing.

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
