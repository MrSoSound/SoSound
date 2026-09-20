# SoSound

An Android music player that lives entirely on your phone: search for a
track, download it, listen offline with the screen off. No server,
nothing to configure.

<p align="center">
  <img src="screenshots/libreria.png" width="45%" alt="SoSound library">
  <img src="screenshots/impostazioni.png" width="45%" alt="SoSound settings">
</p>

## Features

- Search **Songs, Albums, Artists, Podcasts** through the YouTube Music API
- Offline download and playback, with no re-encoding: no quality loss,
  no waiting
- Library with playlists, and playlist import from **Spotify**
- **Casting** to Chromecast and to DLNA/UPnP devices (TVs, speakers)
- Lock-screen controls and Bluetooth headset buttons
- The download engine (`yt-dlp`) updates itself, no app reinstall needed

## Installation

The APK isn't on the Play Store: install it manually.

1. Grab **`SoSound-arm64.apk`** from the [Releases](../../releases)
   page (works on most phones from the last ten years). If it gives an
   architecture error, use `SoSound-universal.apk` instead
2. Open it from your file manager and allow "install unknown apps"
3. Open it. There's nothing to configure

## Legal

SoSound is not affiliated with YouTube, Google, or Spotify, does not
host any content, and has no server: every download is a direct
connection between the phone and YouTube. It's distributed under
**GPL-3.0**, with no warranty, and it's up to whoever uses it to comply
with the copyright laws of their own jurisdiction. Details in
[`DISCLAIMER.md`](DISCLAIMER.md), license in [`LICENSE`](LICENSE),
dependencies in [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md).
