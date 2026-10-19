# Codeberg, Forgejo and Gitea

Forge repositories are fetched directly from the forge, with no backend in between.

- **Clients:** one `ForgejoApiClient` per host (60 s request, 30 s connect and socket timeouts, exponential retry on 5xx and `IOException`). `ForgejoClientRegistry.clientFor(host)` caches them behind a Mutex; it is proxy-aware and drops cached clients when the proxy changes.
- **IDs:** `RepoIdCodec` packs a 23-bit host fingerprint and the 40-bit raw id into the existing 64-bit `repoId` slot (sign bit set = foreign source), so the GitHub-shaped schema stays as it is.
- **URLs:** `RepositoryUrlParser` recognises GitHub, Codeberg, gitea.com, git.disroot.org and user-added forge hosts.
- **README:** `/contents/README.md?ref={branch}`; Forgejo has no `/readme` endpoint.
- **License:** the first of `LICENSE`, `LICENSE.md`, `LICENSE.txt` and `COPYING` found under `/contents/`, matched against SPDX headers.
- **Downloads:** the sum of `asset.download_count` across releases.
- **Details:** a non-null `DetailsScreen.sourceHost` routes every `DetailsRepository` call through `ForgejoClientRegistry`.

## Per-host access tokens

`HostTokenRepository` stores `HostToken(host, token, displayName, createdAtEpochMillis)` entries as one JSON list under a single KSafe key (AES-256-GCM). Tweaks, Access Tokens (`HostTokensScreen`) adds, validates and deletes them. `HostTokenInterceptor` (a Ktor plugin on every `ForgejoApiClient` and the GitHub-direct client; never the backend, `test` or download clients) adds `Authorization: token $pat` to an https request whose host, with its port, matches a saved token, reading it per request so edits apply without a restart. An `Authorization` already set, such as the GitHub account sign-in, wins. `HostNames.apiHostToTokenHost` maps `api.github.com`, `uploads.github.com` and `raw.githubusercontent.com` to the `github.com` token. Ktor drops the header on cross-host redirects, and the mirror rewrite drops it when the host changes.
