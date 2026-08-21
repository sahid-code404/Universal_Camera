# Project State

## Baseline

Repository foundation generated for the empty `sahid-code404/Universal_Camera` repository.

## Current phase

**Phase 3 — single full-resolution sensor RAW capture and DNG output (implementation in progress; device gate pending)**

Merged foundations:
- project/module/build setup
- native Pixel-like UI shell
- Camera2 static discovery + logical/physical lens metadata
- active YUV preview validation: camera open → session creation → non-empty advancing frames
- single-frame RAW still validation for lenses advertising RAW
- conservative duplicate/depth/unusable-camera filtering
- build-fingerprint + lens-fingerprint probe cache
- runtime device/lens quirk registry with repeated-RAW-failure suppression
- Camera2 TextureView preview with orientation-aware transform
- real AF/AE tap metering coordinate mapping
- pinch-to-zoom with HAL-advertised zoom limits
- DataStore preferences
- OTA updater
- native processing bridge scaffold

Phase-3 implementation now adds:
- maximum-size RAW_SENSOR still capture on the selected validated lens
- exclusive single-RAW session to avoid assuming unsupported preview+RAW combinations
- exact RAW Image timestamp / SENSOR_TIMESTAMP pairing
- physical-camera CaptureResult selection for routed physical lenses
- direct Android DngCreator output without JPEG/HEIF conversion
- atomic/pending MediaStore DNG publication under Pictures/Camera
- shutter/timer integration and automatic preview restoration
- Android 9 legacy storage permission handling while keeping scoped MediaStore behavior on newer Android versions

Hardware gates still require real-device evidence:
- verify every displayed lens maps to a real usable optic
- verify hidden/inaccessible auxiliary lenses are not falsely exposed
- verify preview crop/orientation/focus mapping on front and rear lenses
- verify repeated open/close and RAW session reconfiguration do not wedge the vendor HAL
- verify each saved DNG opens in standards-based RAW editors
- verify saved DNG dimensions match the selected lens's maximum advertised RAW size
- record device quirks in `docs/DEVICE_TEST_MATRIX.md`

Not yet production-complete:
- seamless multi-lens zoom switching
- combined preview + sustained RAW ring buffer / ZSL
- frame quality scoring in production
- multi-frame alignment/fusion/HDR/SR
- computational DNG finalization
- portrait/night/panorama processing engines
- RAW/video capture engines

Do not mark a hardware capability complete without device evidence.
