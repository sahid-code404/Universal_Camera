# Project State

## Baseline

Repository foundation generated for the empty `sahid-code404/Universal_Camera` repository.

## Current phase

**Phase 1 — universal camera discovery and active probing (implementation complete; device gate pending)**

Implemented in the Phase-1 branch:
- project/module/build setup
- native Pixel-like UI shell
- Camera2 static discovery + logical/physical lens metadata
- active YUV preview validation: camera open → session creation → non-empty advancing frames
- single-frame RAW still validation for lenses advertising RAW
- conservative duplicate/depth/unusable-camera filtering
- build-fingerprint + lens-fingerprint probe cache
- runtime device/lens quirk registry with repeated-RAW-failure suppression
- validated capability state surfaced to the app model
- Camera2 preview controller
- DataStore preferences
- OTA updater
- native processing bridge scaffold

Phase-1 gate still requires real-device evidence:
- verify every displayed lens maps to a real usable optic
- verify hidden/inaccessible auxiliary lenses are not falsely exposed
- verify repeated open/close does not wedge the vendor HAL
- verify RAW probe on each RAW-capable lens
- record device quirks in `docs/DEVICE_TEST_MATRIX.md`

Not yet production-complete:
- fully correct tap-to-focus sensor mapping
- seamless multi-lens zoom switching
- single RAW capture/DNG writer integration
- RAW ring buffer/ZSL
- multi-frame alignment/fusion/HDR/SR
- portrait/night/panorama processing engines
- RAW video engine

Do not mark a hardware capability complete without device evidence.
