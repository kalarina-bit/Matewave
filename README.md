<p align="center">
  <img src="assets/icon-rounded.png" width="128" alt="Matewawe icon">
</p>

<h1 align="center">Matewawe </h1>

<p align="center">
  <a href="https://github.com/kalarina-bit/Matewawe/releases"><img src="assets/get-it-on-gitea.png" height="60" alt="Get it on Gitea"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/github/downloads/kalarina-bit/Matewawe/total?label=downloads&color=2e7d32&labelColor=1b1b1b" alt="downloads counter">
</p>



**A beautiful, open-source chess app for Android.**

Play chess against AI, solve chess puzzles, learn and practice
chess openings, or play local multiplayer games with friends
over Bluetooth and Wi-Fi/LAN.

## Features

- 🤖 AI opponent with adjustable difficulty
- 🧩 Chess puzzles and tactical training
- 📖 Chess openings — learn and practice opening lines
- ♟️ Opening practice for different variations and move sequences
- 📶 Local multiplayer over Bluetooth/LAN
- 👥 Nearby player discovery over local Wi-Fi
- 🌙 Dark theme
- 🌍 English, Russian, German, Japanese, Lithuanian and Chinese
- 🔓 100% open source


## Screenshots

<p align="center">
  <img src="assets/screenshots/5-move-count.png" width="200" alt="Make your next move count">
  <img src="assets/screenshots/4-home.png" width="200" alt="Home screen">
  <img src="assets/screenshots/1-friendly-matches.png" width="200" alt="From solo games to friendly matches">
  <img src="assets/screenshots/3-play-together.png" width="200" alt="Play together">
  <img src="assets/screenshots/2-keep-growing.png" width="200" alt="Keep growing">
</p>

## Installation

<img src="assets/icon-install.png" width="36" align="left">

1. Download the APK for the version you want from the [Releases page](https://github.com/kalarina-bit/Matewawe/releases).
2. On your Android device, allow installs from unknown sources for the app you use to open the file (Settings → Apps → Special access → Install unknown apps).
3. Open the downloaded `.apk` file and confirm the install.

Or via `adb` (after downloading):

```sh
adb install Matewawe-v1.0.42.apk
```

## Verifying a download

<img src="assets/icon-verify.png" width="36" align="left">

Compare the SHA-256 checksum against the value listed in [CHANGELOG.md](CHANGELOG.md):

```sh
sha256sum Matewawe-v1.0.42.apk
```

## Repository structure

<img src="assets/icon-structure.png" width="36" align="left">

```
.
├── assets/              # Icon and README images
│   └── screenshots/     # App screenshots
├── CHANGELOG.md         # Per-version build dates, sizes and checksums (links to GitHub Releases)
├── LICENSE              # GNU GPLv3
└── README.md
```

APK builds themselves are published as assets on the [Releases page](https://github.com/kalarina-bit/Matewawe/releases), not stored in this repository.

## License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.
