# Codeberg, Forgejo and Gitea

Forge repositories are fetched directly from the forge, with no backend in between.

- **Clients:** one `ForgejoApiClient` per host (60 s request, 30 s connect and socket timeouts, exponential retry on 5xx and `IOException`). `ForgejoClientRegistry.clientFor(host)` caches them behind a Mutex; it is proxy-aware and drops cached clients when the proxy changes.
- **IDs:** `RepoIdCodec` packs a 23-bit host fingerprint and the 40-bit raw id into the existing 64-bit `repoId` slot (sign bit set = foreign source), so the GitHub-shaped schema stays as it is.
- **URLs:** `RepositoryUrlParser` recognises GitHub, Codeberg, gitea.com, git.disroot.org and user-added forge hosts.
- **README:** `/contents/README.md?ref={branch}`; Forgejo has no `/readme` endpoint.
- **License:** sniffed from `/contents/LICENSE` against SPDX headers.
- **Downloads:** the sum of `asset.download_count` across releases.
- **Details:** a non-null `DetailsScreen.sourceHost` routes every `DetailsRepository` call through `ForgejoClientRegistry`.

## Per-host access tokens

`HostTokenRepository` stores `{host, token, label, createdAt}` rows, AES-256-GCM encrypted through KSafe. `HostTokenInterceptor` (a Ktor plugin) injects `Authorization: token $pat` on requests to the matching host. `HostNames.apiHostToTokenHost` maps `api.github.com` to `github.com`, so the GitHub-direct client looks up the right token. The UI is Tweaks, Access Tokens (`HostTokensScreen`).
