# SpikiveGCS Project Map

## Contents

- [Repository shape](#repository-shape)
- [Android entry points](#android-entry-points)
- [Web entry points](#web-entry-points)
- [Spikive domain code](#spikive-domain-code)
- [Documentation and Skills](#documentation-and-skills)
- [Current baseline](#current-baseline)

## Repository shape

The Android project is rooted at the workspace root. The Lichtblick web application is a nested project under `webProject/Spikive-Lichtblick` and has its own Git metadata. Always inspect both scopes before modifying files.

## Android entry points

| Path | Purpose |
| --- | --- |
| `settings.gradle.kts` | Root project and module inclusion |
| `build.gradle.kts` | Root plugin declarations |
| `gradle/libs.versions.toml` | Android/Kotlin dependency and plugin versions |
| `app/build.gradle.kts` | Application SDK, build types, source sets, dependencies, future web build wiring |
| `app/src/main/AndroidManifest.xml` | Permissions, network security, Activity and application policy |
| `app/src/main/java/com/example/spikivegcs/MainActivity.kt` | Current Android application entry point |

Search for newer server, WebView, network, or architecture files before assuming they do not exist:

```bash
rg -n -g '*.kt' -g '*.java' -g '*.xml' -g '*.gradle* \
  '(WebView|ConnectivityManager|TRANSPORT_ETHERNET|NetworkCallback|127\.0\.0\.1|assets)' app
```

## Web entry points

| Path | Purpose |
| --- | --- |
| `webProject/Spikive-Lichtblick/package.json` | Yarn version, Node requirement, workspaces, production build command |
| `webProject/Spikive-Lichtblick/web/webpack.config.ts` | Web build output configuration |
| `webProject/Spikive-Lichtblick/web/src/entrypoint.tsx` | Browser application entry point |
| `webProject/Spikive-Lichtblick/packages/suite-web/src/webpackConfigs.ts` | `publicPath`, HTML generation, chunks and asset handling |
| `webProject/Spikive-Lichtblick/packages/suite-web/src/WebRoot.tsx` | Browser root and data-source registration |
| `webProject/Spikive-Lichtblick/web/.webpack/` | Generated production output; normally ignored and absent before build |

At Skill creation time, the production command is `yarn web:build:prod`, the web project requires Node 20+, uses Yarn 3.6.3, and emits to `web/.webpack/`. Recheck all four facts before wiring or running builds.

## Spikive domain code

Most project-specific web behavior lives in:

- `webProject/Spikive-Lichtblick/packages/suite-base/src/spikive/`
- `webProject/Spikive-Lichtblick/packages/suite-base/src/components/MultiRobotSidebar/`

Foxglove client and data-source code is under:

- `packages/suite-base/src/dataSources/FoxgloveWebSocketDataSourceFactory.ts`
- `packages/suite-base/src/players/FoxgloveWebSocketPlayer/`

Do not modify these areas for Android routing or static-serving failures unless evidence shows an actual web protocol defect.

## Documentation and Skills

| Path | Use |
| --- | --- |
| `webProject/Spikive-Lichtblick/doc/00-README.md` | Web ground-station overview and document index |
| `webProject/Spikive-Lichtblick/doc/01-architecture-overview.md` | Current PC/web/robot architecture |
| `webProject/Spikive-Lichtblick/doc/05-api-reference.md` | Stores, messages, topic APIs, manager contracts |
| `webProject/Spikive-Lichtblick/doc/07-backend-manager-integration.md` | Manager command/status integration and safety |
| `webProject/Spikive-Lichtblick/.codex/skills/spikive-lichtblick-groundstation/SKILL.md` | Existing web-only project Skill |

Use the existing Skill for `drone_id` routing, Select/Visual semantics, 3D rendering, ROS1 publish/subscribe, waypoint flows, manager commands, and web performance. Use both Skills when a task crosses WebView/Android and web business behavior.

## Current baseline

At Skill creation time:

- Android is a minimal Compose application displaying a greeting.
- `minSdk` is API 35 and should not be lowered merely to implement the host.
- No local HTTP server, WebView, Ethernet observer, USB byte transport, or Gradle web synchronization exists in the Android app.
- The web app is a customized Lichtblick v1.24.3 ground station.
- The web app connects directly to Foxglove WebSocket endpoints; Android is not its ROS backend.
- The mesh device is expected to appear as a standard USB Ethernet adapter.

These are discovery snapshots, not guarantees. Re-run targeted searches and read current files before every task.
