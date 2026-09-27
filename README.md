<p align="center">
  <img src="assets/icon.jpg" width="128" alt="Matewawe icon">
</p>

<h1 align="center">Matewawe</h1>

<p align="center">
  <a href="releases/Matewawe-v1.0.37.apk"><img src="assets/get-it-on-gitea.png" height="60" alt="Get it on Gitea"></a>
</p>

Android APK builds for **Matewawe**, organized as sequential, incrementally versioned releases (`v1.0.0` → `v1.0.37`).

> **Note:** `v1.0.32`–`v1.0.36` have no recoverable build timestamp (reproducible Android builds normalize internal file dates); they are ordered right before `v1.0.37`, the file explicitly named `Matewawe.apk` — the current, official latest build.

## Screenshots

<p align="center">
  <img src="assets/screenshots/open-source-ui.png" width="200" alt="100% Open Source, Beautiful Dark & Light UI">
  <img src="assets/screenshots/multiplayer.png" width="200" alt="Bluetooth, Wi-Fi & LAN Multiplayer">
  <img src="assets/screenshots/find-friends.png" width="200" alt="Find Friends Nearby via Wi-Fi">
  <img src="assets/screenshots/play-anywhere.png" width="200" alt="Play Chess Anytime, Anywhere">
</p>

## Installation

<img src="assets/icon-install.png" width="36" align="left">

1. Download the APK for the version you want from [`releases/`](releases/).
2. On your Android device, allow installs from unknown sources for the app you use to open the file (Settings → Apps → Special access → Install unknown apps).
3. Open the downloaded `.apk` file and confirm the install.

Or via `adb`:

```sh
adb install releases/Matewawe-v1.0.37.apk
```

## Verifying a download

<img src="assets/icon-verify.png" width="36" align="left">

Compare the SHA-256 checksum against the value listed in [CHANGELOG.md](CHANGELOG.md):

```sh
sha256sum releases/Matewawe-v1.0.37.apk
```

## Repository structure

<img src="assets/icon-structure.png" width="36" align="left">

```
.
├── releases/           # Versioned APK builds (Matewawe-vX.Y.Z.apk)
├── assets/              # Icon and README images
│   └── screenshots/     # App screenshots
├── CHANGELOG.md         # Per-version build dates, sizes and checksums
└── README.md
```

## Versioning

Builds are numbered sequentially by build date where known. `v1.0.37` (`Matewawe.apk`) is the latest official build.
