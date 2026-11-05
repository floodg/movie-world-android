# UI redesign plan: modern, sleek, streaming-style

Branch: `ui/netflix-redesign`

## Goal

Replace the current utilitarian UI (stacked filters, text-only buttons, list of small cards) with a cinematic, poster-led, dark interface in the style of modern streaming apps. Navigation, layout and motion should feel familiar to anyone who has used a streaming service.

## Out of scope (do not touch)

- `PlaybackService.kt`, `MediaServer.kt`, `TvNetwork.kt`, `TelegramSource.kt`, `PlaybackDatabase.java`: playback, DLNA and Telegram logic stay as they are. The redesign only reads `Playback.state` and calls existing `Playback.start` / `Playback.control`.
- Catalogue rules: every new query must keep the Adult exclusion (`visible` clause) and the existing rank order semantics.
- Offline-first behaviour: the catalogue works without internet; posters degrade to a placeholder.
- Security behaviour in TV settings: Telegram `api_hash` and sign-in challenges stay out of saved instance state.
- Branding: use an original wordmark, accent colour and icon. Borrow layout patterns only, not any streaming service's logo, name, red wordmark or assets.

## Current state (what the code does today)

- One large `MovieWorld` composable in `MainActivity.kt` holds all state and screens. Navigation is boolean flags (`settings`, `selectedId`, `picked`) plus `BackHandler`.
- Home is a single column: title, "TV settings" text button, playback text controls, Continue Watching as outlined buttons, search field, chips, two year fields, genre chips, then a list of 60x88dp poster cards.
- Detail screen is a `LazyColumn` of plain text and outlined buttons under a 220dp backdrop.
- `Artwork` is a hand-rolled loader (`HttpURLConnection` + `LruCache`) with no disk cache, no crossfade, no placeholder animation.
- Theme is the stock `darkColorScheme()` with default typography and shapes.
- Favourites live in `SharedPreferences`.

## Phases

Each phase should end with a building, runnable app so it can be committed and reviewed on its own.

### Phase 0: Baseline

- Run `:app:assembleDebug`, unit tests and lint on the branch to confirm a green start.
- Capture "before" screenshots (home, detail, TV settings, playing state) for comparison.

### Phase 1: Design system

New package `ui/theme/`:

- `Color.kt`: near-black background (about `#0A0A0F`), slightly lifted surfaces for cards and sheets, one saturated accent (decide in open questions), muted text tiers, error and success colours. Verify contrast of text on scrims.
- `Type.kt`: bolder, tighter display style for titles, calm body style, small caps-style label for metadata. One bundled font family at most.
- `Shape.kt` and `Dimens.kt`: 8dp poster corners, 16dp sheet corners, spacing scale, poster aspect ratio 2:3, minimum touch target 48dp.
- `Theme.kt`: `MovieWorldTheme` replaces the inline `MaterialTheme(darkColorScheme())`. Dark only. Edge-to-edge with transparent system bars.
- Shared components in `ui/components/`: `PosterCard`, `SectionRow`, `RatingBadge`, `GenreChip`, `PrimaryButton` (filled, pill) and `SecondaryButton` (tonal), shimmer placeholder.

### Phase 2: Image pipeline

- Replace `Artwork` with Coil (`coil-compose` plus the OkHttp network artifact): disk and memory cache, crossfade, size hints so poster rows don't decode full backdrops.
- Placeholder and error state: dark tile with a subtle film icon, so offline use still looks intentional.
- Remove the `LruCache` / `HttpURLConnection` code once Coil is in. This adds a dependency, flagged in open questions.

### Phase 3: Navigation and structure

- Add Navigation Compose. Routes: `home`, `search`, `list` (My List), `movie/{id}`, `settings`. Back handling and predictive back come from the nav graph instead of booleans.
- Bottom navigation bar: Home, Search, My List. TV settings moves to a cast icon in the top bar of Home.
- Split `MainActivity.kt` into `ui/home`, `ui/search`, `ui/detail`, `ui/settings` and thin ViewModels (`HomeViewModel`, `SearchViewModel`, `DetailViewModel`) so state survives rotation and the composables get smaller.

### Phase 4: Home screen

- Hero billboard: full-bleed backdrop of a featured title, bottom gradient scrim, title, genre line, and two buttons: Play on TV (or Resume) and My List.
- Transparent top bar over the hero with the app wordmark and a cast/TV status icon, becoming solid on scroll.
- Rows of 2:3 posters with snap-friendly horizontal scrolling:
  - Continue Watching (posters with a thin progress bar from saved position)
  - My List
  - Top Rated
  - One row per popular genre
- New read-only catalogue helpers in `Catalogue.kt`: `topRated(limit)`, `byGenre(genreId, limit)`, `featured()`. All reuse the `visible` clause. Rows load lazily so home appears quickly.

### Phase 5: Search and browse

- Search field at top, Title / Actor segmented toggle.
- Genre chips in a single scrolling row.
- Year range moves behind a "Filters" bottom sheet with a range slider, replacing the two number fields.
- Results in an adaptive poster grid (`LazyVerticalGrid`, about 110dp minimum cell). Empty, loading (shimmer grid) and error states are designed, not plain text.
- Keep the existing 200ms debounce and query semantics.

### Phase 6: Movie detail

- Full-bleed backdrop with top and bottom gradients, back button floating over it.
- Title, then a meta row: year, IMDb rating badge, genres.
- Primary action: Play on TV, or Resume with position, with a progress bar. Secondary actions: Start over, Open in Telegram, My List toggle, Play downloaded video.
- Overview with expand/collapse.
- Cast as a row of name chips.
- "More like this" row from shared genres.
- Subtle shared-element or fade transition from poster to detail.

### Phase 7: Playback UI

- Replace the text-based `PlaybackControls` with:
  - A persistent "Now playing on TV" mini bar above the bottom nav (poster thumbnail, title, play/pause, stop, thin progress line).
  - Tap to expand to a bottom sheet with position / duration, a seek bar, -30s, pause/resume and stop.
  - Clear visual states for downloading, preparing, playing, paused, warning and error, driven by existing `Playback.state` fields (`message`, `warning`, `active`, `controllable`, `playing`, `position`, `duration`).
- No changes to how commands are sent.

### Phase 8: TV settings restyle

- Group into two cards: "Your TV" and "Telegram", each with a status indicator (connected / not set up).
- Telegram setup becomes a clear step flow: credentials, phone, code, password, ready.
- Discovered TVs shown as selectable list rows with the selected one marked.
- Same fields, same security rules, same save logic.

### Phase 9: Polish

- Motion: row and card press feedback, fade-in for images, animated bar show/hide.
- Accessibility: content descriptions on posters and icons, TalkBack order, 48dp targets, large font scale check, contrast check.
- Adaptive layout: landscape and tablet use more grid columns and a wider hero.
- App icon (adaptive and themed) and splash screen via `core-splashscreen`.

### Phase 10: Verification

- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` all green.
- Existing `PlaybackProtocolTest` and `PlaybackDeviceTest` unchanged and passing.
- Add Compose UI tests for: home rows render, search filters, detail Play/Resume label logic, mini player states.
- Manual pass on a physical phone: offline catalogue, no-internet posters, rotation, small and large screens, and one full TV playback.

## Suggested commit sequence

1. docs: UI redesign plan
2. theme: design system and shared components
3. images: Coil pipeline
4. nav: Navigation Compose, ViewModels, file split
5. home: hero and rows
6. search: grid and filter sheet
7. detail: new detail screen
8. playback: mini player and sheet
9. settings: restyle
10. polish: motion, accessibility, icon, splash

## Risks

- Splitting `MainActivity.kt` touches a lot of lines; doing it in its own commit (phase 3) keeps later diffs readable.
- Home needs several catalogue queries; on a large database these must run off the main thread and stay indexed. Check query time on the bundled database early.
- Heavy poster rows can be memory hungry; rely on Coil size hints and keep rows to a bounded item count.
- Backdrop images are remote, so the hero needs a good offline fallback (gradient plus poster).

## Open questions

1. Accent colour: a deep red reads as "streaming" but is closest to the obvious reference. A different accent (electric blue, amber, violet) keeps the look original. Which do you prefer?
2. Is adding Coil acceptable, or should the custom image loader stay?
3. Phone portrait only, or should tablet and landscape be a first-class target?
4. Should favourites move from `SharedPreferences` to Room/DataStore as the original port plan intended, or stay as is?
5. Is the Samsung TV playback test still pending? If so, should the redesign wait, or proceed in parallel on this branch?
