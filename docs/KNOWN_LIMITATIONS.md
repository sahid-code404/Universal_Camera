# Known limitations of this repository snapshot

- This environment did not contain an Android SDK/NDK, so the generated project could not be compiled here. CI is configured to compile it in GitHub Actions with the Android toolchain.
- The Gradle wrapper JAR is not embedded in this archive; `./gradlew` securely bootstraps the official 9.5.1 wrapper and checks its published SHA-256 before execution.
- Camera discovery is characteristic-based at this stage. The required active probe/validation system is a Phase 1 gate before shipping broad auxiliary-camera support.
- Preview is real Camera2 output but transform/crop behavior still needs real-device validation for every rotation/aspect combination.
- Tap-to-focus sensor-coordinate mapping is intentionally not faked yet.
- HDR, multi-frame fusion, super-resolution, computational DNG and RAW video are not implemented kernels yet. Their APIs/native home exist so later phases can be added without rewriting the app.
- OTA installation follows the normal Android package installer and is not silent.
