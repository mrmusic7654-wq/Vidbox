# Security policy

Report security problems privately to the repository owner using GitHub's private vulnerability reporting when available. Do not post signed source URLs, cookies, tokens, keystores, or private videos in public issues. Reproduce problems with a public, authorized test source or local fixture.

## Intentional safeguards

- HTTPS-only user input; no embedded URL credentials or arbitrary shell/config/options input.
- Argument-list native execution, fixed app-owned paths, format/extension validation, no shell interpolation.
- TLS verification remains enabled. Test-only TLS trust anchors and loopback HTTP fixtures are confined to test source sets.
- Bounded process output, streaming media I/O, verified HTTP resume boundaries and finite retry limits.
- Encrypted URL-bearing Room specifications; non-exportable device key; backups disabled.
- Non-exported download service/action receiver, immutable explicit PendingIntents and content-URI read grants.
- No authentication/DRM/geo/paywall bypass, broad storage permission, cookie import, trackers or remote executable updater.

## Release maintenance

Keep the native yt-dlp/Python/QuickJS/FFmpeg artifacts and JVM dependencies updated, rerun unit and device tests, inspect 16 KB ABI compatibility, and review upstream security advisories. Static pins make a build repeatable; they do not make third-party parsers invulnerable. Review source compatibility and GPL notices when upgrading runtime artifacts.

Do not release builds with a debug key. Manage signing keys outside Git. Provide the applicable corresponding source and third-party notices with binary distribution. Complete the physical-device and SAF-provider matrix in `docs/VERIFICATION.md` before making production-distribution claims.
