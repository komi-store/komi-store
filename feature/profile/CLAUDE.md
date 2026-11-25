# Profile Feature

Account hub: signed-in user card (or sign-in prompt), links to Stars / Favourites / Recently viewed, What's new, Announcements, Tweaks, About, and logout. **Settings live in `feature/tweaks/`**; this module is narrow on purpose. Presentation-only; the user comes from core `UserSessionRepository`.

## Structure

```
feature/profile/presentation/
├── ProfileViewModel / State / Action / Event / Root
├── components/      # ProfileSections, LogoutDialog
├── announcements/   # AnnouncementsViewModel + stateless AnnouncementsScreen, mute sheet, AnnouncementUi
└── whatsnew/        # WhatsNewViewModel + stateless WhatsNewHistoryScreen, WhatsNewEntryUi / WhatsNewSectionUi
```

## Navigation

`GithubStoreGraph.ProfileGraph` > `ProfileScreen`, built by `profileGraph()` in `composeApp/.../app/navigation/ProfileGraph.kt`. Also `AnnouncementsScreen` and `WhatsNewHistoryScreen`, registered by `profilePages()` in the same file.

## Notes

- `ProfileState.session` is sealed (`Loading` / `SignedOut` / `SignedIn`) and seeded from `UserSessionRepository.lastKnownSession` (primed by `MainViewModel` at startup), so the screen opens in the last known state instead of signed-out.
- Stars and Logout rows show only when signed in.
- `AnnouncementsViewModel` and `WhatsNewViewModel` are app-wide: created with `koinViewModel()` at the top of `AppNavigation` (and in `AppOverlays` for the what's-new sheet), not inside a route. Their screens take state as parameters.
- `AnnouncementsViewModel.unreadCount` drives the unread marker on the Profile row, the bottom nav and the desktop sidebar.
- `WhatsNewViewModel.pendingEntry` feeds `WhatsNewSheet` (composeApp) once per version code; `markSeen()` persists `lastSeenWhatsNewVersionCode` in `TweaksRepository`. Entries come from `WhatsNewLoader` (impl in composeApp) and reload when the app language changes.
- Both app-wide ViewModels map domain models to UI models (`AnnouncementUi`, `WhatsNewEntryUi`) before the screens and `WhatsNewSheet` see them. They catch nothing: `AnnouncementsRepository.refresh()` returns its failure as a `Result`, and the tweaks reads and writes and `WhatsNewLoader` swallow their own failures.
- "Tweaks" tap -> `TweaksScreen`, "About" -> `AboutScreen` (both owned by `feature/tweaks`). Don't add settings here.
