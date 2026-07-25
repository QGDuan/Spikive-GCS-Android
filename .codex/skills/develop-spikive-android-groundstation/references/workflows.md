# Spikive Android Ground Station Workflows

## Contents

- [First inspection](#first-inspection)
- [Implementing the Android host](#implementing-the-android-host)
- [Integrating the web build](#integrating-the-web-build)
- [USB Ethernet integration](#usb-ethernet-integration)
- [Foxglove end-to-end diagnosis](#foxglove-end-to-end-diagnosis)
- [Release acceptance](#release-acceptance)

## First inspection

1. Read applicable repository instructions and inspect both root and nested web repository status.
2. Read Android Gradle files, manifest, Activity, and any existing server/WebView/network classes.
3. Read the web package manager/version declarations, production build command, Webpack output path, and `publicPath` behavior.
4. For Foxglove or ROS behavior, read the existing Lichtblick Skill and relevant `doc/` pages.
5. Classify the requested change as Android host, packaging, Ethernet, Foxglove/ROS, or a cross-layer change.
6. Record assumptions that require physical hardware confirmation; do not encode them as facts silently.

Useful initial commands:

```bash
git status --short
git -C webProject/Spikive-Lichtblick status --short
rg --files app webProject/Spikive-Lichtblick/web webProject/Spikive-Lichtblick/packages/suite-web
```

The root directory may not itself be a Git repository while the nested web project is. Handle each worktree independently.

## Implementing the Android host

Recommended sequence:

1. Define a lifecycle-owned host component with explicit states: starting, ready with URL, failed, stopped.
2. Implement the loopback static server independently and test path/MIME/fallback behavior.
3. Add a WebView screen that waits for the server URL and presents recoverable load errors.
4. Apply restrictive WebView settings and navigation allowlisting.
5. Add structured logs before adding UI diagnostics.
6. Add Ethernet observation as a separate service/state holder; do not couple it to static asset serving.
7. Integrate the two state surfaces only at the presentation layer.

Avoid placing server, WebView, Ethernet callbacks, and all state directly in `MainActivity`. Separate responsibilities so recreation and testing are predictable.

## Integrating the web build

Target Gradle workflow:

1. Verify the web repository's declared Node and package-manager versions.
2. Add a Gradle task that runs the production web build from `webProject/Spikive-Lichtblick`.
3. Add a synchronization task from `web/.webpack/` to a generated Android assets directory.
4. Wire generated assets through Android source sets rather than copying into handwritten source directories when practical.
5. Make release asset processing depend on web build and synchronization.
6. Add a deterministic debug override for reusing an existing build; default release behavior remains strict.
7. Fail with an actionable message if Node/Yarn is missing, the build output is empty, or `index.html` is absent.

Validation checklist:

- Run from a clean web output directory.
- Confirm `index.html`, hashed JavaScript/CSS, workers, fonts, images, and WASM are copied.
- Verify generated paths preserve their relative structure.
- Build the APK twice and ensure stale files removed from the web output do not remain in assets.
- Confirm generated output is ignored unless policy explicitly changes.

Do not build the web bundle at Android application runtime.

## USB Ethernet integration

### App-level observation

1. Register a `NetworkCallback` for Ethernet transport.
2. On availability and link-property changes, capture network handle, interface name, addresses, routes, DNS, and capabilities.
3. On loss, clear only the matching network and report the transition.
4. Avoid treating lack of Internet validation as failure for a private mesh.
5. Check route selection for the configured module IP.

### Device-level diagnosis

Use physical-device evidence when the app cannot see a valid link:

```bash
adb shell ip link
adb shell ip addr
adb shell ip route
adb shell dumpsys connectivity
adb shell dumpsys ethernet
adb logcat
```

Command availability varies by Android build. Prefer available platform diagnostics rather than adding app permissions or raw USB code to compensate for unsupported hardware.

### Route competition

When Wi-Fi and Ethernet coexist:

1. Determine which route Android selects for the exact module IP.
2. Confirm the Ethernet subnet and gateway do not overlap Wi-Fi unexpectedly.
3. Test a socket bound to the Ethernet `Network` before adopting process-wide binding.
4. If process binding is necessary, test loopback page loading, external links, DNS, detach recovery, and rebinding.

## Foxglove end-to-end diagnosis

Follow this order:

1. Confirm the page loaded fully with no missing chunks, workers, or WASM.
2. Confirm Android reports an Ethernet route to the module address.
3. Confirm the module TCP port is reachable from the device/network context.
4. Inspect WebView console for cleartext, mixed-content, DNS, connection, and subprotocol errors.
5. Confirm the bridge accepts a supported Foxglove WebSocket subprotocol.
6. Confirm channel advertisements and schemas appear.
7. Only then debug ROS topics, `drone_id`, manager handshake, selection, visualization, or command publishing with the existing Lichtblick Skill.

Do not add an Android WebSocket proxy solely to hide a route or cleartext misconfiguration. Introduce a proxy only for an explicit, documented product requirement.

## Release acceptance

Run on representative hardware:

1. Install a clean release APK with no developer server or Internet access.
2. Start with USB Ethernet detached; verify the UI loads and reports network absence.
3. Attach the adapter/module; verify detection and transition without restarting the app.
4. Connect to the Foxglove endpoint and confirm advertisements and telemetry.
5. Exercise screen recreation and background/foreground transitions without duplicate servers or callbacks.
6. Detach and reattach Ethernet during an active WebSocket session; verify clear failure and recoverable reconnection behavior.
7. Enable Wi-Fi concurrently and prove module traffic still uses the correct route.
8. Make the module unreachable and confirm diagnostics distinguish route, TCP, WebSocket, and ROS failures.
9. Verify the local HTTP server is unreachable from other devices and interfaces.
10. Confirm release assets correspond to the intended web revision and no debug-only WebView access remains enabled.
