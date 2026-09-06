# Architecture and invariants

## Boundaries

`:domain` is a Kotlin/JVM module without Android imports. It defines `VideoExtractor`, `Downloader`, `MediaProcessor`, `DownloadRepository`, `SettingsRepository`, `MediaStorage`, `NetworkMonitor`, `DownloadScheduler`, the state machine, and use cases. `:data` implements those contracts. `:app` owns foreground execution, notification/picker/intent boundaries, and lifecycle-aware Compose presentation. Screens never instantiate Room, OkHttp, yt-dlp, or FFmpeg.

`HomeViewModel → AnalyzeVideo → CompositeVideoExtractor`

`HomeViewModel → DownloadActions → RoomDownloadRepository → ForegroundDownloadScheduler`

`BrowserViewModel → WebView (HTTPS, JS + DOM storage) → DownloadActions → same queue → storage`

`DownloadService → DownloadQueue → HybridDownloader → HttpRangeDownloader / NativeProcessRunner → FfmpegMediaProcessor (only for merges) → AndroidMediaStorage → Room completion transaction`

## Android-native engine packaging

The pinned `io.github.junkfood02.youtubedl-android` 0.18.1 library/ffmpeg AARs provide:

- ABI-specific `libpython.so`, a Python runtime archive, and the actual yt-dlp zipapp resource;
- `libqjs.so` for the public-page JavaScript extraction machinery supported by yt-dlp;
- `libffmpeg.so` and its dependency archive.

The upstream FFmpeg archive still contained five 4 KB-aligned WebP/sharpyuv shared libraries despite its top-level 16 KB support. Vidbox resolves that AAR as a **build input**, rebuilds those libraries from pinned libwebp 1.6.0 source with NDK 28.2 and `-z max-page-size=16384`, and packages the actual corrected native payload as its own generated JNI resources. It does not edit alignment headers or suppress the compatibility check. Original FFmpeg/ffprobe binaries and the other runtime libraries are preserved. CMake flags, source digest, scripts and required notices are in the repository; generated binaries stay out of Git.

The AAR's aging yt-dlp zipapp is overlaid by official **yt-dlp 2026.08.19**, fetched only during a build and verified against the SHA-256 and immutable URL in `engine.lock`. This keeps the extraction code current without introducing an on-device remote code updater. The device smoke test asserts the exact pinned version, so an ineffective resource overlay fails CI. The Python/FFmpeg/QuickJS native package is still the Android-compatible AAR, not a desktop executable.

AGP's `jniLibs.useLegacyPackaging = true` extracts executable libraries to `applicationInfo.nativeLibraryDir`. Executable code is **not** downloaded into a writable app directory and then chmod'ed, which would fail modern Android execution rules. The bundled zipapp is interpreted by the APK's executable Python runtime. `YoutubeDL.init` initializes these known resources lazily on `Dispatchers.IO`. FFmpeg archive initialization is deferred until processing is needed and uses a canonical-path-checked, streaming initializer that preserves the archive’s internal SONAME symlinks while rejecting links outside its private root. A version marker ensures a newer packaged engine replaces the previously installed zipapp after app upgrades.

We intentionally **do not use the wrapper's `execute`/`destroyProcessById` implementation**: its output buffers are unbounded and its child-process cleanup uses a shell pipeline. `NativeProcessRunner` uses `ProcessBuilder(List<String>)`, a static Python session-leader launcher, structured coroutine children for stdout/stderr, and TERM/KILL of the owned process group. A nonce-bound stdin handshake registers the group before any extraction code can spawn a descendant, and pipe-close exceptions during cancellation remain cancellation. User text is never interpolated into the launcher or passed as a shell command. Extraction JSON is capped at 8 MiB; stderr and progress lines are bounded. Exit failures become categorized, redacted user errors.

Requests disable config/cache, disallow playlists at the application boundary, use explicit format IDs, fixed app-owned output paths, finite retries/timeouts and a single fragment worker per task. No cookies, DRM flags, external plugin paths, arbitrary options, or bypass mechanisms are exposed. Native progress uses a version-controlled JSON `--progress-template`, not a translated percentage regex. Muxing uses explicit video/audio maps and `-c copy`. Local remux inputs have protocol/demuxer allowlists; downloaded playlists cannot direct FFmpeg to unrelated network endpoints. Muxed HLS and detected MPEG transport streams are also finalized with a stream-copy remux when the advertised output is not TS; disabling yt-dlp’s implicit FFmpeg fixups therefore does not leave TS bytes mislabeled as MP4.

Upstream references: [packaging/API](https://github.com/yausername/youtubedl-android/tree/0.18.1), [16 KB support](https://github.com/yausername/youtubedl-android/releases/tag/0.18.0), [QuickJS addition](https://github.com/yausername/youtubedl-android/releases/tag/0.18.1). Upstream README legacy-public-storage examples are deliberately not followed.

## Queue and progress

- Room rows persist the selection, destination snapshot, status, byte counts, speed, ETA, dates, error code, output/pending URI, resume capability, and attempt count.
- `DownloadQueue` has one mutex-protected owner and a structured child job per task; the default limit is two, configurable from one to four.
- Claims and state changes are transactional compare-and-set operations. A late callback cannot change a paused/cancelled/completed row. Active control decisions re-read the row rather than trusting a potentially stale Room invalidation snapshot.
- Direct progress is sampled at approximately 750 ms, native progress at one second, notifications at one second, and diagnostic progress events at five seconds. Unknown totals remain indeterminate. Processing is a distinct phase; byte totals are not used as fake processing percentages.
- Increasing concurrency starts new jobs; reducing it allows existing jobs to finish. Cancel closes writers before task cleanup. Paused/failed parts remain for retry. Completed/cancelled staging is cleaned with a durable flag.

## Direct transfer safety

Requests use a pooled OkHttp client with verified TLS, timeouts, connection retry and identity encoding. HTTPS-to-HTTP redirects are not followed by the direct client. Headers and signed resource links are not logged.

A `.part` file is associated with a small resume journal. Resume requires a stable strong ETag or Last-Modified validator; requests send both `Range` and `If-Range`. A 206 must have the expected starting offset, content length/range and matching validator. A 200 truncates the old file. A 416 is treated as complete only when size and validator prove it. Invalid ranges discard the incompatible partial before retry. Early EOF is not completion. Only a fully streamed/synced transfer is atomically promoted to the staging output. Reads use 64 KiB buffers.

## Storage and publication

Staging is in `noBackupFilesDir/transfers/<validated UUID>`, not a purgeable thumbnail cache. Names are Unicode-normalized, stripped of controls/traversal characters, bounded in UTF-8 bytes, and suffixed with the task ID. Extensions and yt-dlp format IDs are allowlisted.

MediaStore inserts an owned `IS_PENDING=1` Video/Audio row with an appropriate relative folder (Movies/Vidbox, Music/Vidbox, or Downloads/Vidbox for generic browser files). The pending URI is recorded before copying. After a successful streaming copy and sync, the item is made public, then committed as completed in Room. Cancellation/failure removes pending output; interrupted publications are deleted/retried on recovery instead of producing duplicate visible files. A cancelled row cannot win a completion race; a newly published file is removed if completion loses its compare-and-set.

`FormatPlanner` offers video only as MP4 and emits one option per actually reported quality (resolution + frame-rate bucket). Formats whose resolution is unknown are hidden when any labeled quality exists, and codec-unknown containers are offered as MP4 only when stream copy is safe (MP4-family or transport streams); everything else is honestly unavailable rather than labelled unknown.

SAF stores a persistable directory grant and creates a uniquely named temporary document. It streams the file and renames it only on success; a provider that cannot rename is rejected with a folder error. Documents providers are not a shared transaction participant: a provider dying precisely between a successful rename and its new URI being journaled can leave an orphan that requires user cleanup. MediaStore/SAF permission revocation, removable storage, cloud-provider errors and missing files are treated as user-visible errors, never broad-storage permission requests.

Known sizes are checked against free staging space plus a 24 MiB reserve. Merging and MediaStore publication budget extra copies. Unknown-size transfers check storage while progressing; ENOSPC from a destination provider is mapped to a low-storage error. FFmpeg failure deletes only its partial mux, retaining complete source streams for retry.

## Background and recovery

User actions start an explicit, non-exported `dataSync` foreground service. It calls `startForeground` promptly and owns the queue independent of Activity/Compose lifecycles. Notifications show individual filename, measured progress/speed and applicable pause/cancel actions. A user-paused task retains an ordinary notification with a working Resume PendingIntent even after its foreground service stops. A timed partial wake lock is renewed **only while a job is working**, not while waiting for a network.

- **Rotation / Activity recreation / home gesture / task swipe:** the service and Room queue remain independent of the UI.
- **Network loss / Data Saver block / disallowed mobile or metered connection:** active network work is paused, its process/call is cancelled, and partial data is preserved. The foreground service waits without a wake lock and resumes when network policy allows it. Local processing can finish offline.
- **Process death:** `START_STICKY` allows Android to recreate an active service. It reclaims interrupted rows and safe partials. This is subject to Android/OEM scheduling, not an immortality guarantee.
- **Reboot / app re-entry recovery:** a short WorkManager job reconciles interrupted state and shows a resume reminder. It does not start a forbidden dataSync foreground service from boot. Foreground app re-entry adopts queued/network-waiting work. User-paused work stays paused.
- **Android 15+ dataSync timeout:** `onTimeout` cancels jobs, marks them system-paused, stops promptly, and offers a user recovery reminder. The six-hour platform budget is not bypassed.
- **Force-stop / battery-restricted OEM:** no Android app can promise automatic execution after force-stop. The user must open Vidbox. A signed release still needs physical-device/OEM acceptance testing.

Settings persist in DataStore. Room source specifications containing potentially signed URLs are AES-GCM encrypted with a non-exportable Android Keystore key. Backups are disabled to avoid restoring encrypted rows without their device key. Logs accept only restricted structured identifiers and scalar fields; no raw URLs, filenames, titles, headers, cookies, tokens, or stack traces are logged by the app.

## Presentation invariants

- `VidboxIcons` is the single semantic icon map (`download`, `pause`, `resume`, `browser`, `settings`, …). Screens never reference a concrete Material glyph, so rebranding replaces one file; all glyphs are Apache-2.0 Material symbols.
- `DownloadButton` is the one call-to-action component for download states (`IDLE`, `ANALYZING`, `READY`, `DOWNLOADING`, `PAUSED`, `PROCESSING`, `COMPLETED`, `FAILED`). State decides label, icon, interactivity, and the polite live-region announcement; busy states are untappable and the only animation is a small indeterminate spinner.
- Browser address rules live in `:domain` (`BrowserLinks`): typed input becomes HTTPS pages or encoded searches only, download headers are parsed without interpolation, and only HTTPS qualifies as a stored homepage. The Browser screen owns the single WebView, applies JavaScript/cookies/desktop preferences with a reload on change, pauses timers with the Activity lifecycle, silently ignores internal schemes (`about:`, `data:`, `javascript:`, `blob:`), and performs engine-level data clearing through one shared `BrowsingData` helper used by both the browser menu and Settings.
- Browser session history is intentionally memory-only (bounded list, cleared with browsing data): the app's privacy model keeps user URLs out of plaintext persistence, so "browser history" never adds a new one.
- Settings validation is centralized in `SettingsEncoding`: offered quality/container/concurrency choices are enforced, insecure homepages (including `javascript:` strings) are rejected before storage, and unknown persisted values decode to safe defaults. `autoResume` gates network-paused recovery in `DownloadQueue`, the foreground service drain check, and foreground adoption; with it off, an explicit resume is required. `duplicatePolicy=SKIP` refuses a publish whose visible destination name already exists (MediaStore query or document-provider lookup) instead of silently renaming.
