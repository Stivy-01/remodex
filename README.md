<p align="center">
  <img src="CodexMobile/CodexMobile/Assets.xcassets/remodex-og1.imageset/remodex-og2%20%281%29.png" alt="Remodex" />
</p>

# Remodex

[![License: ISC](https://img.shields.io/badge/License-ISC-blue.svg)](LICENSE)

Remodex is a local-first bridge and mobile app for controlling Codex on your Mac from a paired phone. The repo keeps the runtime on your machine, uses a local or self-hosted relay for pairing, and does not depend on a hosted production service.

## Current State

- Local-first bridge for Codex and git actions
- QR-based pairing and trusted reconnect flow
- Local relay runner for development and self-hosted testing
- Mobile app sources in the repo
- Desktop app handoff and thread history integration
- No hosted deployment runbook or public production endpoint in this repository

## Repository Layout

```text
phodex-bridge/   Node.js bridge CLI and runtime
relay/           Local relay server used by the bridge
android/         Android app source and build files
CodexMobile/     iOS app source and Xcode project
shared/          Shared protocol and model code
run-local-remodex.sh  Local relay + bridge launcher
```

## Prerequisites

- Node.js 18 or newer
- Codex CLI installed and available on your PATH
- A mobile build installed on your device before pairing
- Xcode 16+ if you want to build the iOS app
- Android Studio if you want to build the Android app

## Quick Start

Start the local relay and bridge:

```sh
./run-local-remodex.sh
```

That script:

- starts the local relay from `relay/`
- starts the bridge from `phodex-bridge/`
- prints the pairing QR in the terminal

If you already have a relay and only want the bridge, run:

```sh
cd phodex-bridge
npm start
```

## Bridge Commands

The installed `remodex` CLI currently exposes:

- `remodex up`
- `remodex start`
- `remodex restart`
- `remodex stop`
- `remodex status`
- `remodex run-service`
- `remodex reset-pairing`
- `remodex resume`
- `remodex watch [threadId]`
- `remodex --version`

Current package version:

```sh
1.3.9
```

## Environment

Important runtime variables:

- `REMODEX_RELAY` for a custom relay URL
- `REMODEX_CODEX_ENDPOINT` to connect to an existing Codex WebSocket instead of spawning a local one
- `REMODEX_REFRESH_ENABLED` to enable desktop refresh behavior explicitly
- `REMODEX_PUSH_SERVICE_URL` for optional push integration

If you are running from source, use the local relay script or set `REMODEX_RELAY` yourself.

## Building the Mobile Apps

### iOS

```sh
cd CodexMobile
open CodexMobile.xcodeproj
```

### Android

Open the `android/` project in Android Studio and build from there.

## Notes

- This repo is intended to stay local-first.
- Keep relay and pairing data local unless you explicitly self-host it.
- Avoid committing local caches, build output, keystores, or other machine-specific artifacts.

## License

[ISC](LICENSE)
