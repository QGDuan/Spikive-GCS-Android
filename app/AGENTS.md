# Spikive GCS Android Agent Instructions

## Hard Scope Boundary

You are working only on the Android host for Spikive GCS.

- You may edit files under `app/`.
- You may edit root Android build files only when required by the Android host: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and `gradle/libs.versions.toml`.
- Do not edit anything under `webProject/Spikive-Lichtblick/` unless the user explicitly changes the scope and authorizes Web changes.
- Do not edit generated Web output, Gradle build output, IDE metadata, or dependency caches.
- Do not move ROS, Foxglove, drone routing, waypoint, 3D rendering, or flight-command behavior into Android.
- Keep `MainActivity` locked to `sensorLandscape`; the embedded Lichtblick UI is landscape-only.
- Treat the USB mesh device as standard Ethernet unless the user provides contrary hardware evidence.
- Never auto-enter WebView. USB Ethernet and Wi-Fi each require an explicit search click followed by an explicit enter click.
- Bind the App process only to the user-selected USB Ethernet or Wi-Fi `Network`; clear the binding and close WebView when it disappears.
- Ignore cellular networks and never fall back to 4G/5G.
- Do not run Gradle, Android, Yarn, Webpack, or Lichtblick builds when the user has chosen to compile in Android Studio.
- If an Android request appears to require a Web change, stop at the interface boundary and explain the required Web-side change instead of editing it.

@./README.md
