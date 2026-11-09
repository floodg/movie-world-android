# Movie World Android

A standalone Kotlin and Jetpack Compose movie catalogue for Android, with a bundled SQLite database, Telegram handoff, Samsung DLNA playback, and saved watch progress.

## Features

- Browse the catalogue offline by genre, IMDb rating, title, actor, and release year.
- View movie details and cast, and save favourites on the phone (My List), with automatic personal-data sync through Telegram Saved Messages.
- Open the original movie post in Telegram over Wi-Fi or mobile data.
- Download a movie through Telegram and stream it from the phone to a compatible TV.
- Resume TV playback through Continue Watching, with pause, stop, and start-over controls.

## Open in Android Studio

1. Clone this repository and open its root folder in Android Studio.
2. Install Android SDK Platform 35 and allow Gradle sync to finish. The Gradle daemon configuration requests JDK 21.
3. Select a physical Android phone running Android 8.0 or later and run the app configuration.
4. For terminal builds on Windows, set JAVA_HOME to a suitable JDK, then run the commands below.

The catalogue is included at app/src/main/assets/movie-world.sqlite. The app reads an on-device copy and keeps favourites and watch progress in a separate Room database. Posters and backdrops are fetched with Coil and cached on disk; without internet they fall back to a placeholder tile.

## Personal data sync

Connect Telegram in TV settings. My List, resume positions, completion status and watch history remain in Room/SQLite locally and sync as labelled JSON files in Saved Messages. Android works with the PC off. Devices using the same Telegram account merge changes, including removals and playback resets. The catalogue, credentials, sessions, downloads and TV settings are excluded.

Sync runs on launch, foreground return, network recovery and local changes. Retries run every minute while the process is alive. Playback uploads are throttled to once a minute and flushed on pause/stop. Android can suspend or terminate the process; reopen the app or use **Sync now** to catch up. Settings show pending changes, errors and the last successful sync. The latest two confirmed snapshots per device are retained; older copies are removed only when their records are covered by those retained backups. Both hyphenated and Telegram-normalized underscore filenames are supported. Cleanup failures appear in sync status and retry on later syncs. These are ordinary Telegram cloud files, without a separate encryption password. Avoid editing attachments by hand: damaged or unsupported sync files report an error and keep local data intact.

The Android app has no Supabase sign-in, migration form, or Supabase SDK dependency. Migration is handled externally by publishing a compatible Movie World snapshot to the same Telegram account's Saved Messages. The phone restores it through normal Telegram sync; no password or file import is required on the phone. Existing records merge individually, preserving newer local edits. Supabase source data remains untouched.

The unchanged PC/browser app continues using Supabase and will not receive new Android changes in this phase. Signing out of Telegram keeps local data; switching accounts archives and restores each account's personal data separately. Stop playback before switching. Existing unassigned local data belongs to the first Telegram account connected.

The interface is a dark, poster-led layout: a Home screen with a featured title and rows (Continue Watching, My List, Top Rated, popular genres), a Search tab with title/actor search, genre chips and a year-range filter sheet, a My List tab, a full-bleed movie detail screen, and a persistent "now playing on TV" bar that expands into playback controls. TV settings open from the cast icon on Home. See docs/ui-redesign-plan.md.

Open this folder in Android Studio and run the app on a physical Android phone. Use the bundled Android Studio JDK for Gradle.

## Play on TV

1. Put the phone and Samsung TV on the same Wi-Fi network and turn the TV on.
2. Tap the cast icon on Home to open TV settings, tap Find TVs and select the renderer. If discovery is blocked, enter the TV's full DLNA description URL and tap Check and save TV. The renderer ID is remembered so a different device at the same address is detected.
3. In Telegram for TV playback, enter your own api_id and api_hash from https://my.telegram.org/apps. Sign in with your Telegram account, entering the Telegram sign-in code and two-step verification password if requested. After sign-in, the app automatically joins Johns Movie Channel using the configured invite. Channel setup must succeed before TV playback is ready; settings offers a retry if joining fails or admin approval is pending. Credentials are encrypted with Android Keystore. This login is separate from the installed Telegram app.
4. Open a movie and tap Play on TV. The full video downloads to phone storage before the TV starts. Keep the phone connected to Wi-Fi while watching.
5. Pause, Resume, seek, skip back 30 seconds, or Stop from the now-playing bar at the bottom of the app (tap it for the full controls). The notification also provides Stop. Continue Watching records positions reported by the TV and survives app restarts. Resume verifies the TV reaches the saved position. Start over begins a new viewing.
6. For the first DLNA test, use Play downloaded video on TV and choose a known-compatible MP4 already on the phone. This tests TV playback independently of Telegram sign-in.

Completed videos are cached in app-private storage, with an 8 GB cache budget and a 256 MB free-space reserve for Telegram downloads. Individual files above 8 GB are rejected. Selected local videos are copied to a temporary app cache file during casting, then removed. One TV session is allowed at a time. Process termination stops the phone-hosted stream, and saved progress remains available for a new session.

TV codec compatibility still needs testing on the Samsung RU7100. Unsupported files require a different encoding or the Open in Telegram action. The app does not transcode. Phone/code/password Telegram login is implemented. Additional Telegram account challenges are reported in settings. If Android terminates the service, open the app and retry. Android data-sync time limits apply to downloads.

## Development and verification

The sibling ../movie-world repository is the read-only reference for web UI behavior, catalogue queries, and Samsung playback. See docs/android-port-plan.md.

From the repository root in PowerShell, run the following commands.

~~~powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
~~~

The connected tests require an Android device with debugging enabled. They load the native Telegram library, check Room progress storage using an isolated database, and render the Compose screens (home rows, search filters, detail Play/Resume labels, mini player states) with fixed state. They do not sign into Telegram or start TV playback.

Build, unit tests, lint, and the native-library/Room device tests have passed. Actual Telegram authentication, downloads, and TV playback require account setup and testing on your own TV.

The debug APK is app/build/outputs/apk/debug/app-debug.apk. Gradle installs the main debug app during connected-device tests. Test progress uses an isolated in-memory database.

TDLib is supplied by io.github.tdlib-android:core:0.1.1 from Maven Central, a community prebuilt package of Telegram TDLib. Native loading is checked on-device. Upstream documentation is at https://core.telegram.org/tdlib/docs/ and package setup is at https://tdlib-android.vercel.app/setup.

Downloaded Telegram movies expire seven days after their last playback session ends. Cleanup runs when Telegram starts and hourly while the app process remains alive; expired movies are removed the next time the app runs if it was closed. Active playback is protected. The 8 GB quota may remove files sooner. Existing downloads start being tracked when first used after this upgrade.
