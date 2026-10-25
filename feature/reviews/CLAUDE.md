# Reviews Feature

The full Reviews screen for a repository: rating summary, sort and star filter, paging, the viewer's own review, up and down votes, reports and developer replies. The API contract with the backend is `docs/agents/reviews.md`.

## Structure

```text
feature/reviews/
  presentation/    # ReviewsRoot + ReviewsViewModel + ReviewsUiState + ReviewsAction + ReviewsEvent
    moderation/    # ModerationRoot + ModerationViewModel: the queue for moderators
    components/    # ReviewSheets: ComposerSheet, ReportSheet, ReplySheet
    utils/         # ReviewErrorMessages: ReviewError -> string resource
```

There are no domain or data layers here: Details needs reviews too, and features don't depend on each other, so the models and `ReviewsRepository` live in `core/domain`, the backend calls (`BackendReviewsApi`, `ReviewsRepositoryImpl`) in `core/data`, and `ReviewCard`, `RatingSummaryPanel` and `StarRating` in `core/presentation/components/reviews`.

## Navigation

`GithubStoreGraph.ReviewsScreen(owner, repo, sourceHost, openComposer, version, appLabel)`, opened from the Details section. `openComposer` opens the write sheet once `/mine` has loaded; `version` and `appLabel` prefill the review. Sign-in goes to `AuthenticationScreen`. When `/mine` says `isModerator`, a top bar action opens `GithubStoreGraph.ReviewModerationScreen`, whose repo labels open that repo's `ReviewsScreen`.

## Notes

- The public list is a CDN copy that can lag a write by minutes, so the viewer's own review comes from `/mine`, is pinned above the list and is dropped from it. `isHidden` on it shows the moderator notice.
- Every fresh load (sort, filter, retry) bumps `requestGeneration`; responses from an older generation are dropped.
- Votes show as up, net score, down. They're optimistic and roll back on failure; tapping your current vote again takes it back (DELETE), and switching moves the score by 2. Reports and votes are hidden on your own review; Reply shows only when `/mine` says `canReply` (push access, GitHub repos only).
- A plain 404, a 5xx or no network reads as "unavailable" (the backend has `REVIEWS_ENABLED` off or can't be reached), not as an error.
- `ReviewsRepository.changes` emits after a post, edit or delete, so Details reloads its section.
- Moderation: Reported and Hidden queues. Hide, dismiss, unhide and delete each take the review out of the queue it's in once the backend says 204; delete and ban ask first. Ban stops the author's future writes only. The card's own hidden notice is for the author, so the queue turns it off.
