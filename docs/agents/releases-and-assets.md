# Releases, assets and installed apps

How a release asset is matched to the platform and to an installed app. All of this is pure logic in `core/domain/utils`, shared by Details and the update checker, with tests in `core/domain/src/commonTest` (start with `UpdateVerdictTest`). `AssetMatchingReplayTest` in `core/domain/src/jvmTest` replays a corpus of real repositories collected by `tools/asset-replay/`.

- **`AssetVariant`**: token, glob and stem fingerprints of an asset name, plus `versionMajor`. `tagGlob` blanks a name's own release version and adjacent build ids, so callers pass each name's release tag.
- **`AssetOwnership`**: which installed app owns an asset when a repository ships several apps. Filter first, then glob, then stem; ties go to the matching major version.
- **`assetPlatformOf`**: the platform an asset is for.
- **`newestReleasePerPlatform`**: the newest release for each platform, the device's platform first. Drives the Details platform chips.
- **`ReleaseLines`**: which app a tag belongs to in a monorepo (`auth-v4.4.25` belongs to `auth`). Renames and Details chips stay within a line.

`DetailsScreen.packageName`, set when Details opens from Apps, selects the installed app in a multi-app repository.

In the Library, an update or re-download starts from the match the last update check stored on the installed app (`latestReleaseId` and the `latestAsset*` fields). A fresh release lookup runs only when nothing is stored or a download from the stored match failed (`AppsViewModel.resolveUpdateTarget`).
