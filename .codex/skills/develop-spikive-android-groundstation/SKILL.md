---
name: develop-spikive-android-groundstation
description: Use when developing, debugging, reviewing, documenting, or planning the SpikiveGCS Android host that embeds the Spikive-Lichtblick web ground station, including WebView hosting, loopback static HTTP serving, Gradle-to-Yarn web asset packaging, USB Ethernet detection and routing, self-organizing network module connectivity, Foxglove WebSocket access from WebView, offline APK deployment, and Android/Web/network integration diagnostics. Do not use for Lichtblick-only drone_id, ROS1 topic, waypoint, or 3D-rendering changes unless the task also crosses the Android boundary.
---

# Develop Spikive Android Ground Station

## Start with repository truth

Before proposing or changing behavior:

1. Inspect the current worktree and applicable `AGENTS.md` files. Preserve unrelated user changes.
2. Read the Android entry point and build configuration, then inspect the actual web build and networking code involved in the task.
3. Treat the paths and versions in the bundled references as orientation, not permanent truth. Current source wins.
4. If the task changes Lichtblick business behavior, also use `webProject/Spikive-Lichtblick/.codex/skills/spikive-lichtblick-groundstation/SKILL.md`.

Read references selectively:

- System boundaries or design decisions: `references/architecture.md`
- Build, implementation, integration, or release work: `references/workflows.md`
- Entry points, commands, and repository navigation: `references/project-map.md`

## Non-negotiable architecture

- The attached mesh-network device is assumed to enumerate as a standard Android Ethernet transport. Use `ConnectivityManager` and network/link properties; do not design raw `UsbManager` byte I/O unless hardware evidence disproves this assumption.
- The web app is the Foxglove WebSocket client. It connects to the module or airborne bridge, normally `ws://<module-ip>:8765`, through Android's network stack.
- Android owns application lifecycle, local page hosting, WebView policy, Ethernet observation, route selection where required, connection diagnostics, and packaging.
- Android does not parse ROS messages and does not reimplement a Foxglove or ROS backend by default.
- Serve packaged web assets from an HTTP server bound only to `127.0.0.1`. Do not expose the asset server to Ethernet, Wi-Fi, or the mesh network.
- WebView loads the loopback origin. The server must support correct MIME types, hashed chunks, workers, WASM, static assets, and SPA fallback without converting missing asset requests into `index.html`.
- Avoid JavaScript bridges unless a concrete feature cannot be implemented through normal web and Android APIs. If one is required, expose the smallest allowlisted interface.

## Work by layer

Classify the problem before editing:

| Layer | Owns | Typical evidence |
| --- | --- | --- |
| Android host | Activity, lifecycle, WebView, local server, permissions | Logcat, manifest, Kotlin stack traces |
| Web packaging | Webpack output, asset URLs, workers, WASM, cache busting | WebView console, HTTP status and MIME |
| Ethernet | USB NIC enumeration, addresses, routes, network binding | `ConnectivityManager`, `LinkProperties`, `adb shell ip` |
| Foxglove/ROS | WebSocket handshake, topics, schemas, publish/subscribe | browser console, bridge logs, existing web Skill |

Fix the lowest failing layer first. Do not patch ROS or UI logic to mask a missing Ethernet route or broken static asset.

## Build and packaging contract

- The web source remains under `webProject/Spikive-Lichtblick`; do not duplicate source code into the Android module.
- Production assets come from the web production build output and are synchronized into an Android assets directory by Gradle.
- Use the repository-pinned package manager and supported Node version. At the time this Skill was created, the web repo declares Node 20+ and Yarn 3.6.3; verify before use.
- Release assembly must depend on a build-and-sync task and fail when assets are missing, stale, or the web build fails.
- Debug builds may explicitly opt into reusing existing assets for iteration speed, but the choice must be visible and deterministic.
- Keep generated web output out of source control unless the project deliberately changes this policy.

## Network behavior

- Observe Ethernet with `NetworkCallback`; derive interface name, addresses, routes, DNS, validation, and reachability from the selected `Network` and `LinkProperties`.
- Do not assume Android chooses Ethernet over Wi-Fi. Diagnose the route used for the module IP.
- Prefer binding only module-bound sockets or process traffic when necessary and after verifying that doing so will not break the loopback asset server or unrelated connectivity.
- Distinguish these states in diagnostics: adapter absent, Ethernet present without address, address present without route, route present but host unreachable, TCP reachable but WebSocket rejected, WebSocket connected but Foxglove handshake/topic flow invalid.
- Never implement blind retries for flight-control commands. Preserve the web project's publish-once and ACK/status safety rules.

## WebView and server safety

- Allow navigation only to the loopback application origin and explicitly approved external destinations.
- Disable file/content access and universal file URL access unless demonstrated necessary.
- Enable JavaScript, DOM storage, workers, and other capabilities only as required by Lichtblick.
- Scope cleartext policy to required loopback and module endpoints; do not enable broad insecure traffic without documenting the reason.
- Forward WebView console, renderer crashes, HTTP failures, and network state into structured diagnostic logs without logging sensitive payloads.
- Stop the local server and unregister callbacks according to lifecycle ownership; prevent duplicate servers across recreation.

## Troubleshooting order

1. **Blank page:** verify loopback server startup, `index.html`, CSP, JavaScript errors, WebView compatibility, and root URL.
2. **Chunks/workers/WASM fail:** inspect requested URLs, status codes, MIME types, `publicPath`, worker origin, compression, and SPA fallback exclusions.
3. **USB Ethernet missing:** verify hardware enumeration and Android Ethernet transport before touching WebView or Foxglove code.
4. **Ethernet exists but module is unreachable:** inspect IP, subnet, route, interface, ARP/neighbour state, and Wi-Fi route competition.
5. **TCP works but WebSocket fails:** check URL, cleartext policy, WebView console, subprotocol negotiation, and bridge availability.
6. **WebSocket connects but no robot data:** switch to the Lichtblick Skill and inspect Foxglove advertisements, ROS schemas, `drone_id`, topics, and manager handshake.
7. **Desktop browser works but WebView fails:** compare browser engine support, origin, asset paths, mixed content, network binding, cache, worker, and WASM behavior.

## Validation expectations

For implementation tasks, validate the smallest affected layer first, then the integrated path:

- Static server unit tests for path normalization, MIME, missing assets, SPA routes, and traversal rejection.
- Android tests for lifecycle ownership, allowed navigation, network-state mapping, and error presentation where practical.
- Gradle task checks for clean web build, asset synchronization, stale asset rejection, and release dependency wiring.
- A physical-device matrix covering cold start, USB NIC attach/detach, Wi-Fi coexistence, module unreachable/recovery, WebSocket reconnect, screen recreation, and offline APK startup.
- Confirm the local server is loopback-only and no raw USB protocol code was introduced without changed hardware requirements.

## Scope boundary

Use this Skill alone for Android hosting, asset packaging, Ethernet, routing, and integration failures. Combine it with the existing Lichtblick Skill when work changes both sides. For pure React, ROS1 topic routing, `activeDroneId`, `visualDroneId`, waypoints, manager commands, or 3D rendering, use the existing Lichtblick Skill instead.
