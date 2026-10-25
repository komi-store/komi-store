# Reviews Feature

The full Reviews screen for a repository: rating summary, sort and star filter, paging, the viewer's own review, up and down votes, reports and developer replies. The API contract with the backend is `docs/agents/reviews.md`.

## Structure

```text
feature/reviews/
  presentation/    # ReviewsRoot + ReviewsViewModel + ReviewsUiState + ReviewsAction + ReviewsEvent
    moderation/    # ModerationRoot + ModerationViewModel: the queue for moderators
    components/    # ReviewSheets: ComposerSheet, ReportSheet, ReplySheet; ReviewsEmpty
    utils/         # ReviewErrorMessages: ReviewError -> string resource
```

There are no domain or data layers here: Details needs reviews too, and features don't depend on each other, so the models and `ReviewsRepository` live in `core/domain`, the backend calls (`BackendReviewsApi`, `ReviewsRepositoryImpl`) in `core/data`, and `ReviewCard`, `RatingSummaryPanel` and `StarRating` in `core/presentation/components/reviews`.

## Navigation

`GithubStoreGraph.ReviewsScreen(owner, repo, sourceHost, openComposer, composerRating, version, appLabel)`, opened from the Details section. `openComposer` opens the write sheet once `/mine` has loaded; `composerRating` (the star tapped on Details, 0 for none) fills that first sheet only, and `version` and `appLabel` prefill the review. Sign-in goes to `AuthenticationScreen`. When `/mine` says `isModerator`, a top bar action opens `GithubStoreGraph.ReviewModerationScreen`, whose repo labels open that repo's `ReviewsScreen`.

## Notes

- The public list is a CDN copy that can lag a write by minutes, so the viewer's own review comes from `/mine`, is pinned above the list and is dropped from it. `isHidden` on it shows the moderator notice.
- Every fresh load (sort, filter, retry) bumps `requestGeneration`; responses from an older generation are dropped.
- The composer never opens before `/mine` answers: a tap then waits for it (`composerPending`), since a blank composer would post a new review over the viewer's existing one. A failed `/mine` drops the wait with an error message.
- Look: the summary's bars are the star filter (tap a bar, tap it again to clear; a chip names the active filter), sort is a `KomiSegmented` group, and the pinned own review follows the filter too. Load-more stops after a failed page and shows Retry, rather than retrying in a loop.
- The shared pieces in `core/presentation/components/reviews` carry the expressive look: the average on a cookie (a tilted ink stamp in Manga) with bars that grow in once per visit, stars that pop in a wave as they fill, votes as one connected pill (`ReviewVotes.kt`) whose score rolls, the developer's reply as a speech bubble (`ReviewReply.kt`, `SpeechBubbleShape`) with a Developer badge, and the viewer's own review on a tinted card labelled Your review. The composer names each level (Hate it ... Love it).
- Votes show as up, net score, down. They're optimistic; tapping your current vote again takes it back (DELETE), and switching moves the score by 2. One `Mutex` per review sends them in tap order, and only the newest tap's failure rolls back, to `confirmedVotes` (the last vote the server accepted). Taps before `/mine` loads are ignored, since earlier votes come with it.
- Signed in means a stored token (`isUserLoggedIn`); `getUser` only supplies `viewerId`, and `/mine` fills it in when the profile call fails. Sign-in from this screen returns here (`AppNavigation` checks the entry under the auth screen), and a composer asked for while signed out opens once `/mine` loads.
- After the viewer's own post or delete, `ReviewsRepositoryImpl` sends a `fresh` nonce on that repo's list reads for five minutes, so the CDN's copy (s-maxage 300) can't undo the write on the next load, here or in Details. Reports and votes are hidden on your own review; Reply shows only when `/mine` says `canReply` (push access, GitHub repos only).
- A plain 404, a 5xx or no network reads as "unavailable" (the backend has `REVIEWS_ENABLED` off or can't be reached), not as an error.
- `ReviewsRepository.changes` emits after a post, edit or delete, so Details reloads its section.
- Moderation: Reported and Hidden queues. Hide, dismiss, unhide and delete each take the review out of the queue it's in once the backend says 204; delete and ban ask first. Since the server's queue shrinks too, load-more asks for page `items / 20 + 1` and drops reviews already shown; deciding the last loaded review loads the next page, and a failed page shows Retry. Ban stops the author's future writes only. The card's own hidden notice is for the author, so the queue turns it off.
