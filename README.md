# Camera — Universal Computational RAW Android Camera

This repository is the production foundation for **Camera**, a universal Android camera focused on a RAW-first computational-photography pipeline.

## Product contract

For computational still modes, the intended pipeline is:

`Sensor RAW -> RAW burst -> quality scoring -> alignment -> motion/deghosting -> fusion/HDR/SR -> computational RAW -> one standards-compliant DNG`

The app must **never** route a still through JPEG/HEIF and then relabel it as RAW. It must also never claim unsupported hardware features. The current source tree is deliberately phase-gated: real camera discovery/preview infrastructure and the UI/OTA foundation are present, while unimplemented computational stages are explicit interfaces rather than fake algorithms.

## UI direction

The native UI follows the supplied polished Pixel-like prototype: rounded preview, top controls, lens/zoom rail, scrollable mode rail, large shutter, gallery and camera switch. It is an original implementation inspired by those usability principles; it does not copy proprietary Google assets or branding.

Removed from the supplied prototype because they conflict with this product:

- RAW + JPEG / JPEG-only controls
- RAW/JPEG control toggle
- Storage Saver
- Motion Photo
- generic “Store videos efficiently” duplicate toggle

Added/changed:

- **DNG** status/control instead of JPEG/RAW switching
- **Normal / HDR / HDR+ Auto** computational mode
- **Processed / RAW preview** preference
- per-lens **Native / Computational SR / 1.5x / 2x** upscaling policy
- capability-driven lens controls
- built-in **GitHub OTA updater** with SHA-256 and signing-certificate checks

## What is implemented now

- modern multi-module Android project
- Jetpack Compose overlay UI + `TextureView` camera surface
- Camera2 discovery without assuming camera IDs
- logical/physical camera enumeration and capability metadata
- physical-lens routing through `OutputConfiguration.setPhysicalCameraId()` where exposed
- real preview session state machine and clean lifecycle handling
- persistent app settings with DataStore
- native C++/NDK processing bridge scaffold
- MediaStore DNG output helper for later DNG writer integration
- GitHub Releases OTA check/download/verify/install flow
- CI and signed-release workflow templates
- complete architecture, phase plan, release/OTA docs and copied source references

See [`docs/FEATURE_STATUS.md`](docs/FEATURE_STATUS.md) before treating any computational feature as complete.

## Requirements

- JDK 17+
- Android Studio with Android SDK 37
- NDK 28.2.13676358
- CMake 3.22.1+

The project uses AGP 9.3.0, Gradle 9.5.1 and Compose BOM 2026.08.00.

## First build

The wrapper JAR is bootstrapped from Gradle's official distribution and SHA-256 verified on first use:

```bash
./gradlew :app:assembleDebug
```

Or bootstrap explicitly:

```bash
./scripts/bootstrap-gradle-wrapper.sh
```

The debug APK will be under `app/build/outputs/apk/debug/`.

## GitHub OTA releases

The updater is preconfigured for:

- owner: `sahid-code404`
- repository: `Universal_Camera`

A release should contain:

- `Camera-<version>.apk`
- `release-manifest.json`

Use the included GitHub Actions release workflow and read [`docs/OTA_UPDATES.md`](docs/OTA_UPDATES.md). A normal Android app cannot silently replace itself: Android may require the user to grant “Install unknown apps” and confirm installation. Signature continuity is mandatory for in-place updates.

## Development order

Do not jump straight into HDR/SR. Follow [`ROADMAP.md`](ROADMAP.md) and require every phase to compile, install, launch, pass relevant tests, survive lifecycle changes and be validated on real hardware.

## Package identity

`com.sahidcode404.camera`

The project intentionally does **not** spoof `com.android.camera`, `org.codeaurora.snapcam`, or any OEM package name.

## License

Apache-2.0. See [`LICENSE`](LICENSE).
