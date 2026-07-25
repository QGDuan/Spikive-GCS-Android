# Spikive Android Ground Station Architecture

## Contents

- [Target topology](#target-topology)
- [Responsibility boundaries](#responsibility-boundaries)
- [Runtime data flow](#runtime-data-flow)
- [Local static server](#local-static-server)
- [USB Ethernet and routing](#usb-ethernet-and-routing)
- [WebView policy](#webview-policy)
- [Failure model](#failure-model)

## Target topology

```text
Android APK
├── Activity / lifecycle owner
├── loopback static HTTP server (127.0.0.1 only)
├── WebView -> http://127.0.0.1:<port>/
├── Ethernet observer / diagnostics
└── packaged Lichtblick production assets
                │
                │ Foxglove WebSocket through Android network stack
                ▼
USB Ethernet NIC -> self-organizing network module -> airborne Foxglove Bridge -> ROS1 nodes
```

The loopback server and Foxglove WebSocket are separate flows. The page is loaded from loopback; robot traffic goes to the module IP through Ethernet.

## Responsibility boundaries

### Android owns

- Process and screen lifecycle.
- Starting and stopping the loopback static server.
- Safe WebView configuration and navigation policy.
- Observing Ethernet networks, addresses, routes, and transitions.
- Presenting actionable connection diagnostics.
- Packaging and serving a reproducible web build.

### Web owns

- Lichtblick UI and state.
- Foxglove WebSocket protocol handling.
- ROS1 schemas, subscribe/advertise/publish flows.
- Multi-drone routing, selection, visualization, waypoints, and manager command semantics.

### Module and aircraft own

- USB Ethernet link and mesh transport.
- Reachability to the airborne systems.
- Foxglove Bridge service, normally on TCP port `8765`.
- ROS1 nodes and authoritative flight/manager state.

Do not move responsibilities across these boundaries without a measured requirement. In particular, avoid implementing a second Foxglove protocol stack in Android.

## Runtime data flow

### Application assets

1. Android starts a static server on an available loopback port.
2. WebView loads the server root.
3. The page requests hashed JavaScript/CSS chunks, workers, images, fonts, and WASM from the same loopback origin.
4. Client-side routes fall back to `index.html`; missing concrete assets return `404`.

### Robot communications

1. Android observes a network with `TRANSPORT_ETHERNET`.
2. Diagnostics expose link addresses, interface, routes, and module reachability.
3. The web UI opens `ws://<module-ip>:8765` with Foxglove-supported subprotocols.
4. Android routes packets through the selected system network; it does not decode ROS payloads.
5. The Foxglove Bridge advertises channels and schemas; the web UI subscribes and publishes according to the existing ground-station contracts.

## Local static server

Required properties:

- Bind explicitly to `127.0.0.1`, not `0.0.0.0`.
- Select and report a usable port deterministically within the process.
- Normalize and decode request paths safely; reject traversal and malformed paths.
- Serve MIME types required by HTML, JavaScript modules, CSS, JSON, SVG, fonts, images, source maps, workers, and WASM.
- Return `index.html` only for valid navigation routes that do not identify a concrete missing asset.
- Avoid stale browser caches after an APK/web asset upgrade; hashed asset names remain the primary cache-busting mechanism.
- Support byte serving only if a real packaged asset requires it; do not add unnecessary server complexity.

Avoid `file:///android_asset/` as the primary production architecture because workers, origin rules, fetch behavior, routing, and debugging are more predictable over a loopback HTTP origin.

## USB Ethernet and routing

The hardware contract is a standard Ethernet transport. Expected Android evidence includes an Ethernet `Network`, an interface name, link addresses, and routes. Absence of these is a hardware/kernel/device-support problem before it is an app problem.

Use `ConnectivityManager` callbacks instead of polling. Maintain a state model that can represent:

- adapter absent;
- Ethernet detected but not configured;
- address assigned but module route missing;
- route available but host/TCP unreachable;
- TCP available but WebSocket/Foxglove failure;
- fully connected.

Do not infer reachability from `NET_CAPABILITY_VALIDATED`; a private mesh network may intentionally have no Internet validation. Inspect the route to the target module address.

Wi-Fi may remain the default network. If module traffic selects the wrong interface, evaluate targeted network binding. Process-wide binding is a high-impact choice because it can affect every non-loopback connection; document and test it before adoption.

## WebView policy

- Enable only capabilities required by the built application.
- Restrict top-level navigation to the loopback origin; open or reject other schemes explicitly.
- Keep file and content access disabled unless a verified Lichtblick feature requires them.
- Avoid broad mixed-content and cleartext allowances. Scope network-security configuration to required endpoints.
- Use WebView debugging only in debuggable builds.
- Capture console messages, HTTP errors, render-process termination, and page-load failures in structured logs.
- Do not expose arbitrary Android objects to JavaScript. Prefer page-visible HTTP/configuration data or narrowly scoped interfaces.

## Failure model

Treat failures as a layered chain:

```text
APK lifecycle
  -> local server
    -> WebView engine and page assets
      -> Ethernet enumeration and route
        -> target TCP port
          -> WebSocket/Foxglove handshake
            -> ROS topics and application semantics
```

Each layer must provide enough evidence to prove success before debugging the next layer. This prevents UI or ROS changes from concealing host and routing failures.
