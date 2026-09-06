# Vidbox

An Android video and audio downloader built with Kotlin, Compose and Material 3. Extraction and processing run **on the device**; there is no Vidbox relay server or account.

**Android 10+ · target API 36 · ARM64 / ARMv7 / x86_64 · GPL-3.0-or-later**

> Download only media you own or have permission to save. Vidbox does not bypass DRM, authentication, geographic restrictions, paywalls, or other access controls. Source availability and terms still apply.

## Use

1. Browse in the app's **Browser** tab (HTTPS pages only), or paste/share a supported **HTTPS** video link.
2. On a video page tap **Download from this page**; direct links to media or files (`.mp4`, `.pdf`, `.zip`, …) ask once and go straight to the queue.
3. Tap **Analyze link** for pasted links. Results come from a direct-media HTTP probe or the bundled yt-dlp engine, not a fixed list of sample formats.
4. Video is offered **only as MP4**, one option per quality the source actually reports — no invented or "unknown" qualities. Separate audio/video options are clearly marked.
5. Tap **Download media**. Manage the actual queue in Downloads or its foreground notifications.
6. Completed media appears in Library; generic browser files (PDF, archives, images, …) land in **Downloads/Vidbox**. Open, share, search, sort, inspect, or delete them there.

Files default to **Movies/Vidbox** (video) or **Music/Vidbox** (audio) using MediaStore. A folder selected in Settings uses Android's Storage Access Framework. Folder changes apply to new tasks; an in-flight task retains its destination.

### Supported paths and limits

- **In-app browser** (HTTPS): JavaScript pages, search, download links, and a *Download from this page* action that analyzes the current page. WebView cookies/session state are never exported; yt-dlp still analyzes the page without sign-in or cookies.
- Direct HTTPS media and generic files (PDF, archives, images, …): streamed with OkHttp and saved under **Downloads/Vidbox**. Stable ETag/Last-Modified validators plus byte ranges enable safe resume. Otherwise interruption restarts the transfer instead of splicing incompatible bytes.
- Public, non-DRM VOD pages supported by the **bundled yt-dlp release**: native HTTP/fragment downloads; resume is best effort, and expired links/formats can require re-analysis.
- Separate video/audio: FFmpeg **stream copy**, with container/codec compatibility checked before offering a merge. Video is offered as **MP4 only**, with exactly one option per quality (resolution + frame rate) the source reports; a source that only ships WebM/VP9 is reported as unavailable in MP4 rather than mislabelled. No re-encoding or fabricated qualities.
- Playlists, live broadcasts, sign-in/cookie import, encrypted credential entry, DRM-protected media, arbitrary command/options input, and transcoding presets are intentionally not supported.
- A source can reject a request or require verification even if yt-dlp has an extractor for the site. Vidbox reports that; it does not try to defeat the restriction.
- Notifications require permission on Android 13+. Refusing it does not grant an exemption from foreground-service rules; Android may hide controls from the notification drawer.
- Force-stop prevents automatic restart until you open the app. Reboot recovery offers a notification rather than illegally starting a dataSync service from boot. Android 15+'s six-hour dataSync budget is respected. See [background behavior](docs/ARCHITECTURE.md#background-and-recovery).

## Build

Install **JDK 17**, **Python 3.12+** (host build tooling), Android SDK platform **36**, build-tools **36.0.0**, NDK **28.2.13676358**, and CMake **3.22.1**. Android Studio can install the SDK; alternatively use `sdkmanager` and set `ANDROID_HOME` or an untracked `local.properties`.

```sh
./gradlew assembleDebug
./gradlew :domain:test testDebugUnitTest
./gradlew assembleRelease bundleRelease lintDebug
# Optional: additionally emit per-ABI release APKs (~half the size of the universal APK; the flag
# cannot be combined with bundleRelease in the same invocation, see app/build.gradle.kts):
./gradlew -Pvidbox.apkSplits=true assembleRelease
# Connected device or emulator (API 29+, x86_64 or ARM):
./gradlew :app:connectedDebugAndroidTest :data:connectedDebugAndroidTest
python3 scripts/check-native-packaging.py
```

The build recompiles five WebP libraries from checksum-pinned source with the Android NDK to correct 4 KB-only dependencies inside the upstream FFmpeg package. `native-deps.lock` and `data/src/main/cpp/CMakeLists.txt` describe that reproducible build. Native payloads are generated under `data/build`, not checked into Git.

The Gradle wrapper has a pinned distribution SHA-256. Dependencies are centrally versioned in `gradle/libs.versions.toml`. Native packages are resolved from Maven Central. A current, immutable yt-dlp **2026.08.19** zipapp is fetched at build time from its official release and checked against `engine.lock` before being bundled; no untracked executable, user-installed on-device Python, shell package manager, or executable download at first launch is required.

### APKs and CI

[Android verification](https://github.com/mrmusic7654-wq/Vidbox/actions/workflows/android.yml) builds debug/release APKs and a release bundle, runs JVM/Room/HTTP tests, lint, and API 35 device tests, and checks embedded 64-bit ELF page alignment. Successful runs publish the `vidbox-apks` artifact; reports are retained separately. Debug APKs are signed with the build environment's debug key. Release outputs are **unsigned unless you provide your own signing configuration**.

Local output paths:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/outputs/bundle/release/app-release.aab`

For a signed release, supply `VIDBOX_KEYSTORE`, `VIDBOX_STORE_PASSWORD`, `VIDBOX_KEY_ALIAS`, and `VIDBOX_KEY_PASSWORD` as environment variables. Never put signing secrets in source control. A fresh CI runner's debug certificate may differ from a previous run; uninstall an old debug installation if Android reports a signing mismatch (this also removes app data).

**Verification evidence and remaining physical-device gates:** [docs/VERIFICATION.md](docs/VERIFICATION.md). A build passing is not a promise that every external provider, document provider, OEM battery policy, or store-distribution requirement has been certified.

## Structure

```text
domain/   Pure Kotlin models, contracts, state machine, format planning, use cases
  model/ repository/ usecase/ util/
data/     Android implementations and bounded, cancellable media I/O
  database/ network/ extractor/ downloader/ storage/ repository/ di/
app/      Compose screens, ViewModels, WebView browser, foreground service and notifications
  presentation/{home,browser,formats,downloads,history,settings,components,theme}/
  service/ worker/ di/ util/
```

Room is the source of truth, including pending publication URIs. Original and thumbnail URLs are AES-GCM encrypted with an Android Keystore key because links can contain bearer tokens. App backups are disabled. Downloads use app-private staging directories and are never buffered entirely into memory.

Read the [architecture](docs/ARCHITECTURE.md), [privacy policy](docs/PRIVACY.md), [security policy](SECURITY.md), and [third-party notices](THIRD_PARTY_NOTICES.md) for implementation and distribution details.

## License

Vidbox source is licensed under **GPL-3.0-or-later**. The packaged Android runtime and FFmpeg dependencies have their own GPL and third-party obligations. Distributors must provide the applicable corresponding source and notices; APK availability alone is not license compliance. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
