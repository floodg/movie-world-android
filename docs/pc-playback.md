# Play from PC

In a movie's More options, Play from PC appears while the configured PC server is reachable over the phone's Wi-Fi and has TV playback enabled. Set the PC server address and select a TV in TV settings first.

The action checks the PC cache first. On a miss it uploads a completed video from the app's Telegram cache, or a video previously selected for that movie through Downloaded video. Android cannot inspect another app's private Telegram storage; select external downloads once using the system file picker. Missing or inaccessible remembered files are ignored. If neither cache has a video, the PC downloads the catalogue movie through its own configured Telegram account. It never downloads an extra phone copy for this action.

The PC discovers and controls the selected TV. The phone does not connect directly to the TV in this mode. The server owns playback and its job ID; Android and the web app send pause, resume, seek and stop to that same job. Android polls server state every two seconds and saves reported progress. Resume chooses the most recently updated PC or phone position, using the existing finished-movie rules.

Phone uploads stream with a fixed content length and percentage progress; the server checks quota and validates the uploaded video using its existing upload API. Stop cancels the phone operation or stops the shared server session. Closing the Android UI does not stop PC playback. Phone subtitle overrides are not transferred by this action; the PC uses its own subtitle preparation.

Validation: Android unit tests, lint, debug APK build; Python cached-playback API/manager tests; PcPlaybackDeviceTest exercises HTTP upload byte integrity, explicit PC download requests, resume selection, shared control URLs and disabled-server availability on a connected Android device.
