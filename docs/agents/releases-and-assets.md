# Releases, assets and installed apps

How a release asset is matched to the platform and to an installed app. All of this is pure logic in `core/domain/utils`, shared by Details and the update checker, with tests in `core/domain/src/commonTest` (start with `UpdateVerdictTest` and the corpus replay in `AssetMatchingReplayTest`).

- **`AssetVariant`**: token, glob and stem fingerprints of an asset name, plus `versionMajor`. `tagGlob` blanks a name's own release version and adjacent build ids, so callers pass each name's release tag.
- **`AssetOwnership`**: which installed app owns an asset when a repository ships several apps. Filter first, then glob, then stem; ties go to the matching major version.
- **`assetPlatformOf`**: the platform an asset is for.
- **`newestReleasePerPlatform`**: the newest release for each platform, the device's platform first. Drives the Details platform chips.
- **`ReleaseLines`**: which app a tag belongs to in a monorepo (`auth-v4.4.25` belongs to `auth`). Renames and Details chips stay within a line.

`DetailsScreen.packageName`, set when Details opens from Apps, selects the installed app in a multi-app repository.
