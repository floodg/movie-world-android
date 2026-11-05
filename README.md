# Movie World Android

A standalone Kotlin and Jetpack Compose movie catalogue for Android, with a bundled SQLite database, Telegram handoff, Samsung DLNA playback, and saved watch progress.

## Features

- Browse the catalogue offline by genre, IMDb rating, title, actor, and release year.
- View movie details and cast, and save favourites on the phone.
- Open the original movie post in Telegram over Wi-Fi or mobile data.
- Download a movie through Telegram and stream it from the phone to a compatible TV.
- Resume TV playback through Continue Watching, with pause, stop, and start-over controls.

## Open in Android Studio

1. Clone this repository and open its root folder in Android Studio.
2. Install Android SDK Platform 35 and allow Gradle sync to finish. The Gradle daemon configuration requests JDK 21.
3. Select a physical Android phone running Android 8.0 or later and run the app configuration.
4. For terminal builds on Windows, set JAVA_HOME to a suitable JDK, then run the commands below.

The catalogue is included at app/src/main/assets/movie-world.sqlite. The app reads an on-device copy and keeps watch progress in a separate Room database. Posters require internet access when they are not in memory.

Open this folder in Android Studio and run the app on a physical Android phone. Use the bundled Android Studio JDK for Gradle.

## Play on TV

1. Put the phone and Samsung TV on the same Wi-Fi network and turn the TV on.
2. Open TV settings, tap Find TVs and select the renderer. If discovery is blocked, enter the TV's full DLNA description URL and tap Check and save TV. The renderer ID is remembered so a different device at the same address is detected.
3. In Telegram for TV playback, enter your own api_id and api_hash from https://my.telegram.org/apps. Sign in with the account that has joined the movie channel, entering the Telegram sign-in code and two-step verification password if requested. Credentials are encrypted with Android Keystore. This login is separate from the installed Telegram app.
4. Open a movie and tap Play on TV. The full video downloads to phone storage before the TV starts. Keep the phone connected to Wi-Fi while watching.
5. Pause, Resume, seek backward 30 seconds, or Stop from the app. The notification also provides Stop. Continue Watching records positions reported by the TV and survives app restarts. Resume verifies the TV reaches the saved position. Start over begins a new viewing.
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

The connected tests require an Android device with debugging enabled. They load the native Telegram library and check Room progress storage using an isolated database. They do not sign into Telegram or start TV playback.

Build, unit tests, lint, and the native-library/Room device tests have passed. Actual Telegram authentication, downloads, and TV playback require account setup and testing on your own TV.

The debug APK is app/build/outputs/apk/debug/app-debug.apk. Gradle installs the main debug app during connected-device tests. Test progress uses an isolated in-memory database.

TDLib is supplied by io.github.tdlib-android:core:0.1.1 from Maven Central, a community prebuilt package of Telegram TDLib. Native loading is checked on-device. Upstream documentation is at https://core.telegram.org/tdlib/docs/ and package setup is at https://tdlib-android.vercel.app/setup.
