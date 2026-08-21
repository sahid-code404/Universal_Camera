# Camera Development Roadmap

## Phase 0 — Repository and architecture
- Gradle/Kotlin/Compose/NDK/CMake
- module boundaries
- logging and error model
- baseline Pixel-like native shell
- OTA/release infrastructure
- CI

**Gate:** app builds, installs, launches and settings persist.

## Phase 1 — Universal camera discovery and probing
- enumerate Camera2 IDs
- classify logical vs physical
- inspect stream maps/capabilities
- build runtime probe sessions
- filter broken/depth/IR/duplicate/unusable entries
- cache validated lens fingerprints
- per-device quirk registry

**Gate:** only usable user-facing lenses appear across test devices.

## Phase 2 — Preview engine
- processed preview
- correct rotation/aspect/crop mapping
- tap-to-focus coordinate mapping
- zoom ratio and physical-lens switching
- 30/60 FPS policy
- RAW preview path where sustainable
- histogram/focus peaking/grid

**Gate:** preview is stable, not stretched, no overlap, focus mapping accurate.

## Phase 3 — Single RAW capture
- RAW_SENSOR ImageReader
- CaptureResult/Image timestamp synchronization
- maximum supported RAW size
- DNG Creator / custom DNG metadata validation
- MediaStore publish

**Gate:** one standards-compliant sensor DNG opens in Lightroom/darktable/RawTherapee.

## Phase 4 — RAW burst and ZSL
- preallocated RAW buffers
- burst scheduler
- frame metadata
- frame quality scoring
- memory/thermal backpressure

## Phase 5 — Native preprocessing/alignment
- black/white normalization
- defect handling
- gyro global alignment
- Bayer/tile registration
- subpixel refinement
- confidence maps

## Phase 6 — Fusion/denoise/deghosting
- sensor noise model
- motion masks
- robust temporal fusion
- texture preservation

## Phase 7 — Computational HDR / HDR+ Auto
- scene analyzer
- exposure planning/bracketing
- highlight protection
- motion-aware shadow fusion
- per-lens HDR tuning

## Phase 8 — Multi-frame super resolution
- subpixel sample analysis
- reconstruction confidence
- truthful fallback to denoise/classical upscale

## Phase 9 — Computational DNG
- CFA vs LinearRaw decision per pipeline
- complete DNG tags
- XMP processing provenance
- validation suite

## Phase 10+ — Night, Pro, Portrait, Panorama, Video, RAW video, slow motion, synthetic modes
Each feature gets an independent device-capability gate and benchmark before exposure in the UI.
