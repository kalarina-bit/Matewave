<p align="center">
  <img src="assets/icon-rounded.png" width="112" alt="Matewave icon">
</p>

<h1 align="center">Matewave</h1>

<p align="center">
  <b>A beautiful, open-source chess app for Android.</b><br>
  Play against the AI, solve puzzles, learn openings, or play friends nearby over Bluetooth and Wi-Fi/LAN.
</p>

<p align="center">
  <a href="https://github.com/kalarina-bit/Matewave/releases/latest"><img src="https://img.shields.io/github/v/release/kalarina-bit/Matewave?style=for-the-badge&label=release&labelColor=1b1b1b&color=2e7d32" alt="Latest release"></a>
</p>

<p align="center">
  <a href="https://f-droid.org/packages/cc.skysparkle.matewave/"><img src="assets/get-it-on%20(1).png" height="56" alt="Get it on F-Droid"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://git.skysparkle.cc/kalarina/Matewave"><img src="assets/badge_obtainium.png" height="56" alt="Get it on Obtainium"></a>
  <a href="https://github.com/kalarina-bit/Matewave/releases/latest"><img src="assets/get-it-on-github.png" height="56" alt="Get it on GitHub"></a>
  <a href="https://git.skysparkle.cc/kalarina/Matewave/releases"><img src="assets/get-it-on-gitea.png" height="56" alt="Get it on Gitea"></a>
</p>

<p align="center">
  <img src="assets/screenshots/5-move-count.png" width="160" alt="Make your next move count">
  <img src="assets/screenshots/4-home.png" width="160" alt="Home screen">
  <img src="assets/screenshots/1-friendly-matches.png" width="160" alt="From solo games to friendly matches">
  <img src="assets/screenshots/3-play-together.png" width="160" alt="Play together">
  <img src="assets/screenshots/2-keep-growing.png" width="160" alt="Keep growing">
</p>

## ✨ Features

- 🤖 **AI opponent** with adjustable difficulty
- 🧩 **Puzzles** and tactical training
- 📖 **Opening trainer** — learn and practice opening lines and their variations
- 📶 **Local multiplayer** over Bluetooth or Wi-Fi/LAN, with nearby player discovery
- 📲 **Share the app** with a nearby friend via QR code over the local network
- 🎷 **Optional jazz radio**
- 🌙 **Dark theme**
- 🌍 **Six languages** — English, Russian, German, Japanese, Lithuanian and Chinese

## <img src="assets/icon-install.png" width="32" alt=""> Installation

- **[F-Droid](https://f-droid.org/packages/cc.skysparkle.matewave/)** — install and update through the F-Droid client.
- **[Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://git.skysparkle.cc/kalarina/Matewave)** — installs the APK straight from Gitea releases and keeps it up to date.
- **Manually** — download the latest APK from [GitHub](https://github.com/kalarina-bit/Matewave/releases/latest) or [Gitea](https://git.skysparkle.cc/kalarina/Matewave/releases), allow installs from unknown sources for the app you open it with (Settings → Apps → Special access → Install unknown apps), then open the `.apk` and confirm.

## <img src="assets/icon-verify.png" width="32" alt=""> Verifying a download

Every official APK is signed with the same key. Check it before installing:

```sh
apksigner verify --print-certs Matewave-v<version>.apk
```

The SHA-256 fingerprint of the signing certificate must be:

```
1C:3E:54:54:9D:09:9A:9D:3D:5B:BE:ED:C5:63:F5:7F:60:12:56:3C:88:85:99:B1:88:B4:FB:17:E3:46:46:A9
```

`apksigner` prints the same value in lowercase without colons. On Android you can check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier) (app ID `cc.skysparkle.matewave`).

## <img src="assets/icon-structure.png" width="32" alt=""> Building from source

Requires JDK 17+ and Android Studio with AGP 9.2 support.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest   # JVM unit tests
```

Nearby multiplayer needs two physical devices — the emulator has no real Bluetooth, and mDNS discovery is often blocked on emulator networks.

## 🤝 Contributing

Bug reports, puzzle and opening fixes, translation fixes and pull requests are welcome in the [issue tracker](https://git.skysparkle.cc/kalarina/Matewave/issues). Please read [CONTRIBUTING.md](CONTRIBUTING.md) first — it explains what fits the project and how to send changes. To open an issue or a pull request, [create an account](https://git.skysparkle.cc/user/sign_up) on this Gitea.

## 📄 License

[GNU General Public License v3.0 or later](LICENSE).
