# Verification record and release gates

This document distinguishes implemented behavior from verified execution. Do not treat the checklist as an automatic production certification.

## Build environment

The authoring sandbox did not contain Java or an Android SDK and could not reach Google's/Maven's distribution hosts. A local `./gradlew assembleDebug` was attempted and failed at the missing-Java prerequisite. Actual Android compilation and tests are run by `.github/workflows/android.yml` on the session branch using JDK 17, SDK 36, Gradle 8.13 and an API 35 x86_64 emulator. CI artifacts retain APKs, reports and release mapping for 14 days.

## Evidence status

Final run links and pass/fail totals are recorded here after the implementation's CI verification completes. Earlier failed runs are intentionally retained in Actions as part of the incremental build history; they are not passing evidence.

## Automated coverage

- Domain: URL validation, credential/scheme rejection, filename traversal/Unicode safety, format compatibility and defaults, state transitions, progress JSON, error redaction/classification, network policy.
- Data JVM: real MockWebServer streaming, Range/If-Range, 200/206/416 behavior, validator and invalid-range restarts, cancellation; Room durable enqueue, compare-and-set, late-callback rejection, completion races, literal search and history cleanup; queue concurrency, cancellation and network recovery.
- Integration JVM: trusted local HTTPS → real direct-media probe → format planning → real buffered transfer → test filesystem publication → actual Room history. Android MediaStore itself is covered on-device, not pretended by this JVM test.
- App instrumentation: launch/main input, successful analysis through a test-only extractor, format selection, download empty state, Room history/missing files, settings and Activity recreation.
- Native instrumentation: actual packaged Python/yt-dlp invocation; locally generated DASH video/audio → actual yt-dlp extraction and stream downloads → actual FFmpeg stream-copy merge → actual MediaStore publication → Room completion; playable audio/video metadata; native process cancellation.
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
