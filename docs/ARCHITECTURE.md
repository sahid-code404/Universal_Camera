# Architecture

## Layering

`app` owns lifecycle and UI composition only. Camera capabilities and domain state are typed models, Camera2 code is isolated, settings are persistent, processing is behind interfaces, and the updater is independent of capture.

### Modules

- `:app` — Compose UI, activity, view-model orchestration
- `:core:model` — camera/lens/capture/processing domain types
- `:core:settings` — DataStore settings contracts
- `:camera:discovery` — CameraManager/CameraCharacteristics discovery
- `:camera:camera2` — TextureView preview controller and Camera2 lifecycle
- `:processing:api` — phase-safe processing interfaces/state machine
- `:processing:native` — C++/JNI/NDK high-cost processing home
- `:storage` — MediaStore output transactions
- `:updater` — GitHub release OTA, integrity/signature verification, installer handoff

## Camera state

The preview controller uses an explicit state flow:

`CLOSED -> OPENING -> CONFIGURING -> PREVIEW -> CLOSING/ERROR`

Do not add random booleans for camera state.

## Camera identity

A `LensTarget` contains:
- logical camera ID to open
- optional physical camera ID to route output to

The UI never assumes numeric ID meaning. Human labels are derived from lens-facing/focal/capability data and later persisted by stable camera fingerprints.

## Preview

`TextureView` is used as the real Camera2 output. Compose draws controls over it; Compose never receives and redraws each camera frame. This avoids per-frame recomposition and keeps the door open for a future Vulkan/RAW preview surface.

## RAW computation

The eventual processing graph is:

1. ingest/validate RAW frames
2. normalize metadata and sensor levels
3. score/reject frames
4. choose reference
5. gyro-assisted global registration
6. hierarchical Bayer/tile alignment
7. local motion/deghost confidence
8. noise-aware fusion
9. HDR merge where required
10. super-resolution only when real subpixel samples exist
11. defect/final linear corrections
12. generate standards-compliant computational DNG
13. validate
14. atomic MediaStore publish

No stage is allowed to secretly use a JPEG as its computational source.

## Threading target

Separate camera control, result ingestion, RAW acquisition, sensors, preview rendering, frame analysis, processing workers, ML workers and storage. High-cost operations must move to C++/Vulkan/NEON only after benchmark evidence.

## Crash safety

Future processing jobs must write a durable manifest before raw buffers are released. Output is written to a temporary/pending entry, validated, and published atomically. Incomplete or corrupt DNGs must not appear as completed photos.
