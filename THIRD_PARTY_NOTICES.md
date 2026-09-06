# Third-party notices and corresponding source

Vidbox's original source code is GPL-3.0-or-later (see LICENSE). This is intentionally compatible with the bundled GPL Android runtime. It is not a proprietary-license sample.

| Component | Pinned source / license information |
| --- | --- |
| youtubedl-android library/common/ffmpeg 0.18.1 | [Source at tag 0.18.1](https://github.com/yausername/youtubedl-android/tree/0.18.1), GPL-3.0; includes Android packaging/build scripts |
| Bundled yt-dlp zipapp 2026.08.19 | [Pinned release and corresponding source](https://github.com/yt-dlp/yt-dlp/tree/2026.08.19), immutable artifact URL/SHA-256 in `engine.lock`; consult the bundled release's notices and dependency licenses, not just the top-level project license |
| Bundled FFmpeg and codec dependencies | [Android FFmpeg build instructions](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_FFMPEG.md), [FFmpeg license](https://ffmpeg.org/legal.html); effective license depends on the actual build configuration and linked codecs |
| Bundled Python, OpenSSL and runtime libraries | [Android Python build instructions](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_PYTHON.md), [Python license](https://docs.python.org/3/license.html); preserve embedded notices |
| QuickJS | [QuickJS source and license](https://bellard.org/quickjs/), MIT; Android package supplied by the runtime |
| Kotlin, Kotlin coroutines/serialization, AndroidX, Compose, Room, DataStore, WorkManager, Hilt, OkHttp, Coil, Jackson, Apache Commons | Apache-2.0; respective upstream notices continue to apply |
| Gradle wrapper | [Gradle 8.13 source](https://github.com/gradle/gradle/tree/v8.13.0), Apache-2.0 |

The runtime AARs are third-party artifacts, not native binaries built from Vidbox's Kotlin sources. Before distributing APKs, preserve all native/runtime license texts, record the bundled runtime versions and FFmpeg configuration, and make the **complete corresponding source** and required build material available through a GPL-compliant distribution method. Upstream links document provenance; a list of links alone may not satisfy every source-distribution obligation. Review that obligation for your distribution channel.

No media, thumbnails, or artwork from a third-party video is bundled in the production app. Instrumentation fixtures are generated locally as short solid-color video and a test tone; they are only test inputs and are deleted after tests.
