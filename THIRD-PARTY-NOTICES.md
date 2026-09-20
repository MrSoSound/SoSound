# Licenze di terze parti

SoSound è pubblicato sotto **GNU GPL v3.0** (vedi [`LICENSE`](LICENSE)),
condizione imposta dall'inclusione di `youtubedl-android`, che è a sua
volta GPL-3.0 senza eccezioni di linking: l'APK risultante è un'opera
combinata soggetta a quella licenza.

| Componente | Uso | Licenza |
|---|---|---|
| [youtubedl-android](https://github.com/junkfood02/youtubedl-android) | impacchetta yt-dlp (Python) per Android | GPL-3.0 |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | risoluzione e scaricamento dei flussi audio | Unlicense |
| [Media3 / ExoPlayer](https://github.com/androidx/media) | riproduzione audio | Apache-2.0 |
| [AndroidX (Room, DataStore, Lifecycle, Navigation, WorkManager, ecc.)](https://developer.android.com/jetpack/androidx) | infrastruttura app | Apache-2.0 |
| [Ktor client](https://github.com/ktorio/ktor) | chiamate HTTP verso l'API InnerTube | Apache-2.0 |
| [Coil](https://github.com/coil-kt/coil) | caricamento immagini/copertine | Apache-2.0 |
| [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) | server locale per la trasmissione DLNA | BSD 3-Clause |
| [play-services-cast-framework](https://developers.google.com/cast) | trasmissione su Chromecast | licenza Google Play Services SDK |

Tutte le licenze permissive elencate (Apache-2.0, BSD, Unlicense) sono
compatibili con la GPL-3.0 nel senso in cui vengono qui usate — come
dipendenze incluse in un'opera combinata GPL-3.0, non il contrario.
