# Release guide

## Build
Android Studio, Java 17+, Android SDK 36. `./gradlew testDebugUnitTest lintRelease assembleRelease bundleRelease`.

Package: `com.deploydulupulangnanti.qrcodescanner`. Version: 1.0.0 (1). Minimum Android 8.0 / API 26; target API 36.

Signing values come from the ignored `keystore.properties` file or environment variables: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The original upload key is at `D:\KEYSTORE\qrcodescanner.jks`, alias `qrcodescanner`. Keep an encrypted off-device backup. Never commit the keystore or credentials.

## GitHub Actions
The Android release workflow uses GitHub-hosted `ubuntu-latest`, Java 17, Android SDK 36, and Gradle caching. It runs only manually from Actions or when a `v*` tag is pushed. Normal commits and PRs do not trigger it. It tests, runs release lint, produces a signed APK/AAB, and uploads artifacts retained for 14 days. There is no Play Store upload step.

Manual runs also offer a `publish_privacy` checkbox (default on). This publishes only `index.html` to GitHub Pages using a separate job with Pages permissions; signing secrets remain scoped to the Android build job. Tag-triggered runs skip the privacy job. Pages must use GitHub Actions as its publishing source. Ordinary pushes do not publish Pages or run Android builds.

Repository Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Signing material is restored only during the job and removed in its final cleanup step. Only run trusted code with the release signing secrets.

## Play Console handoff
1. Create a **paid** app called QR Scanner Pro, developer PT Deploy Dulu Pulang Nanti. Set the price and country distribution yourself. This project has no ads, billing SDK, or subscriptions.
2. Enroll in Play App Signing and upload the signed AAB; retain the local key as the upload key.
3. Use `store/listing/en-US` for title and descriptions, `store/graphics` for icon and feature graphic, and the matching device folders under `store/screenshots`.
4. Set the privacy URL in Play Console to the verified GitHub Pages address: `https://seandyadryan.github.io/qr-code-scanner/`. The manual release workflow publishes the root `index.html` there when `publish_privacy` is enabled. The in-app policy is also available offline.
5. Complete content rating, target audience, app access, and Data safety in Play Console. No login is required. This build has no network permission, analytics, or ads; scans and selected images remain local. Review the declarations against the final uploaded build and any future SDK changes. User-initiated sharing and platform services are explained in the privacy policy.
6. Upload `store/video/qr-scanner-pro-promo.mp4` to YouTube as public or unlisted, turn off monetization/ads, and ensure it has no age restriction. Paste its URL into the listing.
7. Test on real phones before production, including camera focus, flash, rotation, non-Latin content, dim lighting, image import, exported PNG/CSV, accessibility, and tablet/desktop camera availability. Google Play may require closed testing and account verification; complete any requirements shown for your account.
8. Increase versionCode for every upload. Use manual workflow dispatch or a release tag when ready.

Sources checked October 22, 2026: [target API requirements](https://developer.android.com/google/play/requirements/target-sdk), [Google Play developer policies](https://support.google.com/googleplay/android-developer/answer/18258653?hl=en).

`tools/package_release.py` checks APK signing, AAB signing, release asset exclusion, unit/lint results, APK 16 KB ZIP alignment, and 64-bit native ELF load-segment alignment. These are packaging checks; real 16 KB device testing is still recommended. See [Android page-size guidance](https://developer.android.com/guide/practices/page-sizes).
