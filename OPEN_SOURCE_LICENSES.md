# Open-Source License Notices — Klipz

Klipz uses the following open-source libraries and build-time components:

1. **AndroidX & Jetpack Libraries** (Compose, Material 3, Navigation, Room, WorkManager, DataStore, Lifecycle, Media3 ExoPlayer & Transformer)
   - License: Apache License 2.0
   - Copyright: The Android Open Source Project

2. **OkHttp**
   - License: Apache License 2.0
   - Copyright: Square, Inc.

3. **Coil (Coroutine Image Loader)**
   - License: Apache License 2.0
   - Copyright: Coil Contributors

4. **Kotlin Standard Library & Kotlinx Coroutines**
   - License: Apache License 2.0
   - Copyright: JetBrains s.r.o.

5. **FFmpeg (Optional NDK Build Profile)**
   - Configured strictly with `--disable-gpl --disable-nonfree --enable-shared` in `scripts/build_ffmpeg_android.sh`.
   - License: GNU Lesser General Public License (LGPL) version 2.1 or later.
   - Redistribution Notice: When distributing APKs linked with FFmpeg shared libraries, comply with LGPL v2.1+ obligations (include LGPL text, corresponding FFmpeg source version, and dynamic relinking compliance).

6. **yt-dlp (Optional Build-Time Embedded Python Profile)**
   - License: The Unlicense (Public Domain)

7. **Google Fonts (`Space Grotesk`, `Inter`, `JetBrains Mono`)**
   - License: SIL Open Font License 1.1
