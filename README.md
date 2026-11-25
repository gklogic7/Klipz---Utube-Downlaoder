# Klipz — Native Android Media Downloader & Manager

Klipz is a local-first, privacy-respecting Android application built with Kotlin, Jetpack Compose, and Material 3 for inspecting, downloading, processing, playing, and organizing authorized video, image, and audio media across Android phones and tablets.

## Highlights

- **Zero Mandatory Account or Backend**: All history, queues, bookmarks, and preferences live locally in Android Room and DataStore.
- **Minimal Monochrome Dark-First UI**: Adaptive 4-tab shell (`Home`, `Browser`, `Tasks`, `Settings`) switching automatically between bottom `NavigationBar` (phones) and left `NavigationRail` + split-pane layouts (tablets), with Light, Dark, and System theme options plus Comfortable/Compact density.
- **Standalone Resumable Download Engine**: OkHttp streaming transfers with HTTP `Range` resume, `If-Range` (`ETag` / `Last-Modified`) validation, semaphore concurrency control (1–5), live speed & ETA telemetry, startup recovery, WorkManager `dataSync` background execution, and Android 14+ User-Initiated Data Transfer (`JobScheduler`).
- **Scoped Storage (`MediaStore`)**: Atomic `IS_PENDING` commits into `Downloads/Klipz/{Videos,Images,Audio}` or optional app-private storage—zero broad storage permissions required.
- **Bundled Python + yt-dlp + QuickJS/EJS + FFmpeg Engine**: Embeds the `youtubedl-android` (`0.18.1`) Python 3 + `yt-dlp` + QuickJS/EJS + FFmpeg runtime directly in the APK (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`). Extracts real YouTube, Facebook, Instagram, and public platform **Video** (up to full native `2160p`/`1440p`/`1080p`/`720p`/`480p`/`360p`/`240p`/`144p` preserving original aspect ratios like `16:9`, `9:16`, `21:9`) and **Audio** (`M4A`, `MP3`) streams, auto-merging high-resolution adaptive video + audio into **MP4** or **MKV**. Strictly filters out DRM streams and never fabricates unavailable resolutions.
- **Hybrid Media3 + FFmpeg Architecture**:
  - **Primary**: Android Media3 ExoPlayer (in-app playback) & Media3 Transformer (hardware-accelerated MP4 conversion, compression, trimming, and AAC/M4A audio extraction).
  - **Advanced**: Bundled FFmpeg auto-muxer for MP4/MKV adaptive streams plus custom C++ NDK/JNI bridge (`app/src/main/cpp/`) and reproducible LGPL cross-compilation script (`scripts/build_ffmpeg_android.sh`).
- **Modular Authorized Extraction**: Separate `MediaProvider` adapters (`YouTubeProvider`, `FacebookProvider`, `InstagramProvider`, `GeneralYtDlpPlatformProvider`, `DirectMediaProvider`), embedded WebView HTML5 media sniffer with ad-host & popup blocking, and native Android Share Intent (`ACTION_SEND`) support.

## Android Permissions Rationale

| Permission | Purpose |
|---|---|
| `INTERNET` & `ACCESS_NETWORK_STATE` | Inspect URLs, browse pages, stream downloads, and enforce Wi-Fi-only preferences. |
| `POST_NOTIFICATIONS` (Android 13+, runtime) | Display optional download progress and completion notifications. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC` | Keep active background WorkManager transfers alive reliably on Android 14+. |
| `RUN_USER_INITIATED_JOBS` | Support Android 14+ User-Initiated Data Transfer jobs for long user-started transfers. |

No broad storage permissions (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_*`) are requested.

## Building & Testing

```bash
# Run JVM unit & Robolectric tests
./gradlew :app:testDebugUnitTest

# Build Debug APK (Media3 active; FFmpeg JNI probed cleanly at runtime)
./gradlew :app:assembleDebug

# Optional: Cross-compile LGPL FFmpeg libs and link via NDK/CMake
ANDROID_NDK_HOME=/path/to/ndk ./scripts/build_ffmpeg_android.sh 7.1
./gradlew :app:assembleDebug -PenableNativeFfmpeg=true
```

### Signed Release Builds via GitHub Actions

Configure repository secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) — never commit keystores or passwords to Git. See `.github/workflows/android-build.yml`.
