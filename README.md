<p align="center">
  <img src="assets/icon-rounded.png" width="112" alt="Matewave icon">
</p>

<h1 align="center">Matewave: Offline Chess &amp; AI</h1>

<p align="center">
  <b>A friendly, open-source chess app for Android — no account, no ads.</b><br>
  Play the AI, solve puzzles, learn openings, or challenge friends nearby over Bluetooth and Wi-Fi.
</p>

<p align="center">
  <a href="https://github.com/kalarina-bit/Matewave/releases/latest"><img src="https://img.shields.io/github/v/release/kalarina-bit/Matewave?style=for-the-badge&label=release&labelColor=1b1b1b&color=2e7d32" alt="Latest release"></a>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=cc.skysparkle.matewave"><img src="assets/get-it-on-google-play.png" height="56" alt="Get it on Google Play"></a>
  <a href="https://f-droid.org/packages/cc.skysparkle.matewave/"><img src="assets/get-it-on%20(1).png" height="56" alt="Get it on F-Droid"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/kalarina-bit/Matewave"><img src="assets/badge_obtainium.png" height="56" alt="Get it on Obtainium"></a>
  <a href="https://github.com/kalarina-bit/Matewave/releases/latest"><img src="assets/get-it-on-github.png" height="56" alt="Get it on GitHub"></a>
</p>

<p align="center">
  <img src="assets/screenshots/1-play-learn-solve-left.png" width="160" alt="Play, learn, solve"><img src="assets/screenshots/2-play-learn-solve-right.png" width="160" alt="Home screen">
  <img src="assets/screenshots/3-play-ai-or-a-friend.png" width="160" alt="Play AI or a friend">
  <img src="assets/screenshots/4-puzzles.png" width="160" alt="Puzzles for your rating">
</p>

## ✨ What's inside

- 🤖 **AI opponent** with adjustable difficulty
- 🧩 **Puzzles** picked for your rating
- 📖 **Opening trainer** — learn lines and their variations step by step
- 📶 **Play nearby** over Bluetooth or Wi-Fi/LAN, with up to 20 spectators
- 📲 **Share the app** with a friend via QR code
- 🎷 **Optional jazz radio**, 🌙 dark theme, no ads
- 🌍 **Seven languages** — English, Russian, German, Japanese, Lithuanian, Chinese and Spanish

## <img src="assets/icon-install.png" width="32" alt=""> Install

Get it from **Google Play**, **F-Droid** or **Obtainium** using the buttons above — they keep the app up to date for you. You can also grab the APK from [GitHub Releases](https://github.com/kalarina-bit/Matewave/releases/latest) and install it by hand.

<details>
<summary><img src="assets/icon-verify.png" width="20" alt=""> Verifying an APK</summary>

Every official APK is signed with the same key:

```sh
apksigner verify --print-certs Matewave-v<version>.apk
```

The SHA-256 certificate fingerprint must be:

```
1C:3E:54:54:9D:09:9A:9D:3D:5B:BE:ED:C5:63:F5:7F:60:12:56:3C:88:85:99:B1:88:B4:FB:17:E3:46:46:A9
```

`apksigner` prints it in lowercase without colons. On Android you can use [AppVerifier](https://github.com/soupslurpr/AppVerifier) (app ID `cc.skysparkle.matewave`).

</details>

## <img src="assets/icon-structure.png" width="32" alt=""> Building

Needs JDK 17+ and Android Studio with AGP 9.2 support.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Nearby play needs two real devices — emulators have no Bluetooth and often block mDNS.

## 🤝 Say hi

Ideas, bug reports, translation fixes and pull requests are all welcome — open an [issue](https://github.com/kalarina-bit/Matewave/issues) or write to [cuken107@proton.me](mailto:cuken107@proton.me). Please glance at [CONTRIBUTING.md](CONTRIBUTING.md) before sending code.

If you enjoy Matewave, a ⭐ helps others find it. And if you'd like to, you can also [support the project](https://buy.stripe.com/00wcN48ak8ZHgdIghof7i00) ☕

## 📄 License

[GPL-3.0-or-later](LICENSE)
