# Repo Pages Feature

GitHub repo sub-pages opened from Details: issues list (filter, paging, new issue), issue detail (comments, thumbs-up reactions), security (advisories + SECURITY.md), pull requests.

## Structure

```text
feature/repo-pages/
  domain/          # RepoPagesRepository (every call returns Result<T>) + models
  data/            # RepoPagesRepositoryImpl, dto/, mappers/RepoPagesMappers, di (repoPagesModule)
  presentation/
    issues/  issuedetail/  security/  pulls/   # XRoot + XViewModel + XUiState + XAction per screen
    components/      # shared top bar, loading / empty / error, LabelChip, RepoMarkdown
```

## Navigation

`GithubStoreGraph.RepoIssuesScreen(owner, repo)`, `RepoIssueDetailScreen(owner, repo, issueNumber)`, `RepoSecurityScreen(owner, repo)`, `RepoPullsScreen(owner, repo)`. Details opens them from its stats grid, only for GitHub repos (`sourceHost == null`): the Issues tile and a Security tile become tappable. `RepoPullsScreen` is registered in `AppNavigation` but nothing navigates to it today; `PullsRoot` opens a PR in `RepoIssueDetailScreen` by number.

## Notes

- Data source: GitHub REST via `GitHubClientProvider.client` directly. No backend mediator, no cache, no Forgejo path.
- `/issues` also returns PRs; the impl drops them (`pullRequest == null`) and sets `hasMore` from the raw page size (30). Pulls return a plain `List`; `PullsViewModel` derives `endReached` from its own `PER_PAGE = 30`.
- Issue detail fetches the issue and up to 100 comments in parallel. Security = published advisories + the first non-blank of `SECURITY.md`, `.github/SECURITY.md`, `docs/SECURITY.md`.
- `RateLimitException` is rethrown out of the `Result` functions, not wrapped.
- Issues and Pulls keep a `requestGeneration` counter: every fresh load (filter change, retry) bumps it, and responses from an older generation are dropped, so a stale page never lands in the new filter.
- Thumbs-up reactions (`+1` only) are optimistic: the count bumps at once and rolls back on failure, or when GitHub answers 200 (already reacted) instead of 201. New issue, comment and reactions check `UserSessionRepository.isCurrentlyUserLoggedIn()` first.
