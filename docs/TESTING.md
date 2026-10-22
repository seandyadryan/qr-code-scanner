# Verification

The application has 15 JVM tests covering safe website handling, QR payload validation, Wi-Fi escaping and password rules, CSV formula escaping, Unicode QR round trips, and luminance rotation for sideways barcodes.

An Android 15 / API 35 emulator was used to exercise QR creation through the actual prefilled Compose form, favorite changes, history persistence across app restarts, and dark appearance persistence. The captured phone, 7-inch tablet, 10-inch tablet, and desktop configurations use the actual adaptive Android layouts. Demonstration records and the staged camera image are explained in `store/ASSETS.md`.

The release workflow runs the same JVM tests, release lint, R8 optimization, signed APK packaging and signed AAB packaging. Its downloadable artifact includes the APK, AAB, mapping, test reports and lint XML. The handoff ZIP adds a `docs/verification.json` report with signature checks, hashes, 16 KB packaging checks and UI smoke results.

Physical camera optics, real device flashlight/zoom behavior, Android 8 runtime behavior and a real 16 KB Android device still require device testing before production. The emulator verifies UI and packaging behavior, not real camera image quality. See `docs/RELEASE.md` for the Play Console handoff.
