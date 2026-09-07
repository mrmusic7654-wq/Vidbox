# Verification record and release gates

This document distinguishes implemented behavior from verified execution. Do not treat the checklist as an automatic production certification.

## Build environment

The authoring sandbox did not contain Java or an Android SDK and could not reach Google's/Maven's distribution hosts. A local `./gradlew assembleDebug` was attempted and failed at the missing-Java prerequisite. Actual Android compilation and tests are run by `.github/workflows/android.yml` on the session branch using JDK 17, SDK 36, Gradle 8.13 and an API 35 x86_64 emulator. CI artifacts retain APKs, reports and release mapping for 14 days.

## Evidence status

Verified application-code revision: [`3e3cd643c0941c5c1c037dce5980b7cd0842988a`](https://github.com/mrmusic7654-wq/Vidbox/commit/3e3cd643c0941c5c1c037dce5980b7cd0842988a).

[Android verification run 34018751575](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34018751575) completed successfully on September 6, 2026. Both the build and device-test jobs passed.

| Check | Verified result |
| --- | --- |
| `assembleDebug` | Passed; signed debug APK produced |
| `assembleRelease` / `bundleRelease` | Passed with R8/resource shrinking; unsigned release outputs produced |
| `lintDebug` | Passed with no errors; advisory warnings remain in the reports |
| `:domain:test` / `testDebugUnitTest` | 44 tests passed, zero failures/errors/skips |
| `:app:connectedDebugAndroidTest` / `:data:connectedDebugAndroidTest` | 10 tests passed, zero failures/errors/skips, on API 35 x86_64 |
| Production application launch | Installed the debug APK and launched the real `VidboxApplication`/`MainActivity`, separately from the Hilt test application |
| Native packaging audit | Required executables present; 518 packaged 64-bit ELF payloads verified for 16 KB LOAD alignment |

Download the [APK/AAB artifact](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34018751575/artifacts/9984841601), [JVM/lint reports](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34018751575/artifacts/9984837387), or [device reports and launch screenshot](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34018751575/artifacts/9984818050). Artifacts expire after the workflow's 14-day retention period; rerun the workflow to regenerate them. Release signing credentials were not supplied and no production signing claim is made.

This evidence covers the application code at the revision above. This verification-record update changes documentation only. Earlier failed runs remain in Actions as part of the incremental build history; they are not passing evidence. Emulator and ELF-audit results do not replace the physical-device and distribution checks below.

### Verification of the browser/settings/icon pass (2026-09-06, PR #5)

The same sandbox constraints apply (no local JDK/SDK, no egress to Google/Maven/Gradle hosts), so verification runs through GitHub Actions. The pass was developed incrementally against CI; the failing intermediate runs ([34036513233](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34036513233), [34037548925](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34037548925), [34038161562](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34038161562)) surfaced four compile errors and three test defects that were each fixed in the branch (`ad6143d`, `b587e9b`, `d351f22`).

Final evidence at revision [`d351f22`](https://github.com/mrmusic7654-wq/Vidbox/commit/d351f22beac2a1a4f8aa6d8d4010ee5b4b90e8ce):

[Android verification run 34038769492](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34038769492) (and the identical push-triggered run [34038766631](https://github.com/mrmusic7654-wq/Vidbox/actions/runs/34038766631)) completed successfully on September 6, 2026. Both jobs passed.

| Check | Verified result |
| --- | --- |
| `assembleDebug` | Passed; signed debug APK produced |
| `assembleRelease` / `bundleRelease` | Passed with R8/resource shrinking; unsigned release outputs produced |
| `lintDebug` | Passed with no errors |
| JVM tests (`:domain:test`, `testDebugUnitTest`) | 75 tests passed (domain 46, data 29), zero failures/errors/skips |
| Device tests on API 35 x86_64 | 14 cases passed, zero failures/errors/skips (10 pre-existing + 4 new `BrowserViewModelTest`) |
| Production application launch | Debug APK installed; real `VidboxApplication`/`MainActivity` launched on the emulator |
| Native packaging audit | Required executables present; 518 packaged 64-bit ELF payloads verified for 16 KB LOAD alignment |

New automated coverage in this pass: browser address normalization (https-only, search fallback, IDN/punycode, RFC 5987 download filenames, homepage validation), DataStore settings encoding (defaults, clamps, insecure-homepage rejection), queue auto-resume gating, and on-device browser→DownloadManager pipelines (media vs. generic file) against real Room. Release signing credentials were not supplied and no production signing claim is made; the physical-device acceptance checklist below still applies.

## Automated coverage

- Domain: URL validation, credential/scheme rejection, filename traversal/Unicode safety, format compatibility and defaults, state transitions, progress JSON, error redaction/classification, network policy.
- Data JVM: real MockWebServer streaming, Range/If-Range, 200/206/416 behavior, validator and invalid-range restarts, cancellation; Room durable enqueue, compare-and-set, late-callback rejection, completion races, literal search and history cleanup; queue concurrency, cancellation and network recovery.
- Integration JVM: trusted local HTTPS → real direct-media probe → format planning → real buffered transfer → test filesystem publication → actual Room history. Android MediaStore itself is covered on-device, not pretended by this JVM test.
- App instrumentation: launch/main input, successful analysis through a test-only extractor, format selection, download empty state, Room history/missing files, settings and Activity recreation.
- Background instrumentation: actual native-generated MP4, real streaming HTTP, Activity backgrounding, required foreground notification, pause/resume PendingIntents, byte-range continuation and MediaStore file equality. Loopback cleartext is permitted only in debug/test resources; production user input stays HTTPS-only.
- Native instrumentation: actual packaged Python/yt-dlp invocation; locally generated DASH video/audio → actual yt-dlp extraction and stream downloads → actual FFmpeg stream-copy merge → actual MediaStore publication → Room completion; playable audio/video metadata; native process cancellation; muxed HLS → real MP4 remux.
- Packaging: required native executables and all shipped 64-bit ELF LOAD alignment, including libraries inside runtime archives, checked for 16 KB compatibility.

Test doubles are isolated to `src/test`/`src/androidTest`. Production progress, formats and downloads are not simulated.

## Physical-device / distribution acceptance (not certified by a JVM build)

Run the following on at least one ARM64 physical device and an OEM with aggressive background restrictions, including Android 15/16 and a 16 KB page-size device where available:

- [ ] Install a release-signed build; launch and paste a public, authorized HTTPS source.
- [ ] Exercise each provider/site you intend to advertise, including a source with separate video/audio. Site support is not universal.
- [ ] Download a large (>1 GiB) media file; confirm measured progress, low memory use, open/share and gallery visibility.
- [ ] Swipe away the Activity, lock the screen, rotate, recreate the process, and verify foreground controls/recovery within the OS policy.
- [ ] Pause/resume with and without stable ranges; cancel and retry; switch Wi-Fi/mobile/metered/Data Saver mid-transfer.
- [ ] Test notification permission denial, channel disablement and lock-screen privacy.
- [ ] Test Android's dataSync timeout and a force-stop/reboot. A force-stop must not be represented as automatically recoverable.
- [ ] Test low storage during download, merge and final publication on internal and removable storage.
- [ ] Test MediaStore plus writable local SAF, removable SAF and your supported cloud providers; revoke grants, remove storage and reject renames.
- [ ] Move/delete a saved file outside Vidbox and verify Library reports it unavailable.
- [ ] Restart the app/device and verify settings/history. Test an upgrade preserving encrypted history and adopting the new bundled engine.
- [ ] Review native dependency security advisories, GPL corresponding-source distribution, signing, app-store policies and privacy disclosures.

There are no credentials, signing keys, access-control bypasses or external test-account cookies supplied by this repository.
