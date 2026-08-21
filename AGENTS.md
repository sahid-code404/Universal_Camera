# AGENTS.md — mandatory engineering rules

Any coding agent working in this repository must read, in order:

1. `docs/reference/MASTER_DEVELOPMENT_PROMPT.md`
2. `docs/reference/CAMERA_RESEARCH.txt`
3. `docs/ARCHITECTURE.md`
4. `docs/FEATURE_STATUS.md`
5. `ROADMAP.md`
6. `docs/UI_ADAPTATION.md`
7. `docs/OTA_UPDATES.md`

## Non-negotiable rules

- Never fake a camera, resolution, FPS, RAW capability, HDR result, or super-resolution result.
- Never use package-name spoofing as the auxiliary-camera architecture.
- Never convert JPEG/HEIF back into a `.dng` and call it RAW.
- Computational still output is one standards-compliant DNG when the mode can truthfully produce one.
- Keep expensive image work out of Kotlin/UI threads. Use native C++/Rust/Vulkan where measurement justifies it.
- Camera IDs are vendor-specific. Never assume `0=main`, `1=front`, etc.
- Capability detection and runtime probing determine what the UI exposes.
- A Camera2 ID is not automatically a user-visible lens.
- Never destabilize the Camera HAL to force an unavailable stream combination.
- The Compose UI is an overlay/control plane; preview frames live on a real Surface/Texture path.
- Preserve the supplied UI geometry and interaction language while using original assets.
- Do not reintroduce JPEG/RAW+JPEG, Storage Saver or Motion Photo into still settings.
- OTA artifacts must be signed with the same production signing certificate and verified before install.
- Implement one phase at a time; update `docs/FEATURE_STATUS.md` and `PROJECT_STATE.md` in the same PR.
