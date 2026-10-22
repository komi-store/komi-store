# Flatpak Packaging for Komi Store

## Sandbox limitations

The sandbox can't exec or install host binaries, so the Flatpak is browse-and-download only: files land in `~/Downloads` (`--filesystem=xdg-download:rw`); the user installs them manually outside.

Don't add `--filesystem=host` or `--talk-name=org.freedesktop.Flatpak` without a tested host-spawn path — Flathub rejects unjustified host access. For install + auto-update, use the AppImage / deb / rpm / Arch builds.

## Prerequisites

Install Flatpak and the build tools:

```bash
# Fedora
sudo dnf install flatpak flatpak-builder

# Ubuntu/Debian
sudo apt install flatpak flatpak-builder

# Arch
sudo pacman -S flatpak flatpak-builder
```

Install the required runtimes:

```bash
flatpak install flathub org.freedesktop.Platform//24.08
flatpak install flathub org.freedesktop.Sdk//24.08
flatpak install flathub org.freedesktop.Sdk.Extension.openjdk21//24.08
```

## Setup (One-Time)

### 1. Generate the offline dependency sources

Offline builds need every Maven/Gradle artifact pre-pinned. There are **two**
source files, each from a different generator — they cover what the other can't:

```bash
# a) project deps -> flatpak-sources.json
rm -rf ~/.gradle/caches                                                     # avoid pinning stale versions
./gradlew :composeApp:packageUberJarForCurrentOS --no-configuration-cache   # populate cache
python3 packaging/flatpak/generate-all-sources.py                           # scan cache + auto-verify

# b) build-logic classpath -> flatpak-sources-convention.json
./gradlew -p build-logic :convention:flatpakGradleGenerator --no-configuration-cache
python3 packaging/flatpak/verify-sources.py packaging/flatpak/flatpak-sources-convention.json
```

Why two: the root `flatpakGradleGenerator` plugin under-captures the KMP
multiplatform graph, so the project half uses the cache-scanning
`generate-all-sources.py`. build-logic is a plain JVM build, so its **own** plugin
task captures its classpath correctly — including buildscript transitives like
`gson` that the project cache-scan never sees (and it downloads the jars while
resolving). The manifest lists both files.

Never commit a sources file that fails `verify-sources.py` — every pinned URL
must serve its recorded sha512. `generate-all-sources.py` runs that check
automatically and aborts on any mismatch.

### 2. Verify SHA256 hashes

The manifest uses pre-computed SHA256 hashes. To verify or update them:

```bash
# Gradle distribution
curl -sL https://services.gradle.org/distributions/gradle-9.8.1-bin.zip | sha256sum

# JBR x64 (check latest at https://github.com/JetBrains/JetBrainsRuntime/releases)
curl -sL https://cache-redirector.jetbrains.com/intellij-jbr/jbr-21.0.10-linux-x64-b1163.105.tar.gz | sha256sum

# JBR aarch64
curl -sL https://cache-redirector.jetbrains.com/intellij-jbr/jbr-21.0.10-linux-aarch64-b1163.105.tar.gz | sha256sum
```

### 3. Update screenshot URLs

Edit `com.kurikomi.komistore.metainfo.xml` to point to hosted screenshot images.
Flathub requires at least one screenshot with a publicly accessible URL.

## Building Locally

Flatpak builds run on Linux only (`flatpak-builder` is not available on macOS/Windows).

```bash
cd packaging/flatpak

# For a local test build, temporarily swap the manifest's git source for a local dir:
#   - type: dir
#     path: ../../
# (Flathub itself requires the pinned git source — revert before submitting.)

# Build
flatpak-builder --force-clean build-dir com.kurikomi.komistore.yml

# Test run
flatpak-builder --run build-dir com.kurikomi.komistore.yml komistore

# Install locally
flatpak-builder --user --install --force-clean build-dir com.kurikomi.komistore.yml
```

## Validating

```bash
# Validate AppStream metainfo
flatpak run org.freedesktop.appstream-glib validate com.kurikomi.komistore.metainfo.xml

# Lint manifest (requires org.flatpak.Builder)
flatpak run --command=flatpak-builder-lint org.flatpak.Builder manifest com.kurikomi.komistore.yml
```

## Publishing to Flathub

Prerequisites:

- **Domain verification.** The app ID `com.kurikomi.komistore` is domain-based, so
  `kurikomi.com` must be reachable over HTTPS and host a verification token at
  `https://kurikomi.com/.well-known/org.flathub.VerifiedApps.txt` (see Flathub docs).
- **A real release tag.** Set the manifest's `commit:` to the SHA that `v1.9.2` points
  to. Flathub rejects a git source without a pinned commit.

Steps:

1. Fork `https://github.com/flathub/flathub` (do **not** copy only the master branch).
2. Clone the `new-pr` branch and create a feature branch off it:
   `git clone --branch=new-pr git@github.com:<you>/flathub.git && git checkout -b komistore new-pr`
3. Copy the manifest **and all three source JSONs** to the repo root:
   `com.kurikomi.komistore.yml`, `flatpak-sources.json`,
   `flatpak-sources-convention.json`, `flatpak-sources-manual.json`.
   (The desktop entry, metainfo, launcher, and `disable-android` script are pulled
   from the git source at build time, so they do not need copying.)
4. Open a PR with base branch **`new-pr`**, titled `Add com.kurikomi.komistore`.
5. Reviewers / the bot trigger test builds. Address any lint or build feedback.
6. After approval, you get write access to `flathub/com.kurikomi.komistore`.

## File Reference

| File | Purpose |
|------|---------|
| `com.kurikomi.komistore.yml` | Flatpak build manifest |
| `com.kurikomi.komistore.desktop` | Desktop launcher entry |
| `com.kurikomi.komistore.metainfo.xml` | AppStream metadata for Flathub listing |
| `komistore.sh` | Shell launcher (invokes `java -jar` with bundled JRE) |
| `disable-android-for-flatpak.sh` | Strips Android targets for sandbox build |
| `flatpak-sources.json` | Pre-downloaded Gradle dependencies (generated) |
