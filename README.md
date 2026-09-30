<p align="center">
  <img src="assets/icon-rounded.png" width="128" alt="Matewawe icon">
</p>

<h1 align="center">Matewawe </h1>

<p align="center">
  <a href="releases/Matewawe-v1.0.41.apk"><img src="assets/get-it-on-gitea.png" height="60" alt="Get it on Gitea"></a>
</p>



**A beautiful, open-source chess app for Android.**

Play chess against AI, solve chess puzzles, learn and practice
chess openings, or play local multiplayer games with friends
over Bluetooth and Wi-Fi/LAN.

<p align="center">
  <img src="assets/banner.png" width="100%" alt="Matewawe — Play. Learn. Improve. AI games, puzzles, and openings in one app.">
</p>

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
  <img src="assets/screenshots/1-play-your-way.png" width="200" alt="Play your way">
  <img src="assets/screenshots/2-friends-nearby.png" width="200" alt="Friends nearby">
  <img src="assets/screenshots/3-learn-together.png" width="200" alt="Learn together">
  <img src="assets/screenshots/4-home.png" width="200" alt="Home screen">
  <img src="assets/screenshots/5-game-waiting.png" width="200" alt="A chess game is waiting">
</p>

## Installation

<img src="assets/icon-install.png" width="36" align="left">

1. Download the APK for the version you want from [`releases/`](releases/).
2. On your Android device, allow installs from unknown sources for the app you use to open the file (Settings → Apps → Special access → Install unknown apps).
3. Open the downloaded `.apk` file and confirm the install.

Or via `adb`:

```sh
adb install releases/Matewawe-v1.0.41.apk
```

## Verifying a download

<img src="assets/icon-verify.png" width="36" align="left">

Compare the SHA-256 checksum against the value listed in [CHANGELOG.md](CHANGELOG.md):

```sh
sha256sum releases/Matewawe-v1.0.41.apk
```

## Repository structure

<img src="assets/icon-structure.png" width="36" align="left">

```
.
├── releases/           # Versioned APK builds (Matewawe-vX.Y.Z.apk)
├── assets/              # Icon and README images
│   └── screenshots/     # App screenshots
├── CHANGELOG.md         # Per-version build dates, sizes and checksums
├── LICENSE              # GNU GPLv3
└── README.md
```

## License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.
