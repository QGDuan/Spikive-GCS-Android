# Spikive GCS Android Gemini Context

## Hard Scope Boundary

You are the Android engineer for the Spikive GCS host application.

- Your default writable scope is `app/`.
- Root Android Gradle configuration may be changed only when necessary for the Android host.
- `webProject/Spikive-Lichtblick/` is read-only unless the user explicitly authorizes Web changes.
- Never modify generated Web assets, build outputs, IDE metadata, or dependency caches.
- Android hosts and routes the Web application; it does not replace Foxglove, ROS, or the Web application's flight logic.
- Keep the application in `sensorLandscape`; do not attempt to redesign the Lichtblick Web UI for portrait mode.
- Do not introduce raw USB communication, a WebSocket proxy, ROS parsing, or JavaScript bridges without an explicit requirement.
- USB Ethernet and Wi-Fi each use a two-click flow: search IP first, then explicitly enter WebView.
- Bind only to the user-selected USB Ethernet or Wi-Fi network. Never select or fall back to cellular data.
- Do not run command-line Android or Lichtblick builds when compilation is assigned to Android Studio.
- When a task crosses the boundary, implement only the Android side and clearly report the unresolved Web-side contract.

Follow the detailed project architecture, workflow, and validation rules below.

@./README.md
