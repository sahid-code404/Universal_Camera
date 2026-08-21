# Feature status — truth table

Legend: `IMPLEMENTED`, `FOUNDATION`, `PLANNED`, `CAPABILITY-DEPENDENT`, `DEVICE-GATE`.

| Feature | Status | Notes |
|---|---|---|
| Compose camera UI | IMPLEMENTED | Native reproduction/adaptation of supplied layout |
| Camera permission flow | IMPLEMENTED | Camera permission plus Android-9-only legacy MediaStore write permission |
| Camera2 static discovery | IMPLEMENTED | No ID assumptions |
| Logical/physical lens metadata | IMPLEMENTED | Uses CameraCharacteristics / physical IDs |
| Physical output routing | IMPLEMENTED / DEVICE-GATE | Preview and active probe use physical output routing where the logical HAL exposes it; device matrix must verify each lens |
| Runtime camera probing | IMPLEMENTED / DEVICE-GATE | Active YUV open/session/frame probe + single RAW still probe + build-fingerprint cache; real-device evidence still required |
| Processed preview | IMPLEMENTED / DEVICE-GATE | Camera2 TextureView preview with aspect-aware stream selection, rotation/mirroring transform, pinch zoom; device matrix must validate crop/orientation |
| RAW preview | CAPABILITY-DEPENDENT / PLANNED | Vulkan/native path not implemented |
| Tap-to-focus metering transform | IMPLEMENTED / DEVICE-GATE | Normalized preview taps map through front mirroring/display rotation into Camera2 AF/AE metering regions; physical-device accuracy gate remains |
| Single sensor RAW DNG | IMPLEMENTED / DEVICE-GATE | Maximum RAW_SENSOR size, exact image/result timestamp pairing, physical-result metadata, direct DngCreator output, atomic MediaStore publish; must be opened/validated on real devices before gate passes |
| Computational DNG | PLANNED | Requires validated multi-frame pipeline |
| RAW ring buffer/ZSL | PLANNED | Phase 4 sustained-throughput and memory/thermal gate |
| Frame scoring | API FOUNDATION | No production scoring kernel yet |
| Alignment/deghosting/fusion | API FOUNDATION | Native bridge only; algorithms not faked |
| HDR/HDR+ Auto | UI/API FOUNDATION | UI policy present; processing not yet implemented |
| Computational super-resolution | UI/API FOUNDATION | Never label ordinary upscale as SR |
| RAW video | CAPABILITY-DEPENDENT / PLANNED | No claim of universal support |
| OTA update check | IMPLEMENTED | GitHub Releases API |
| OTA integrity verification | IMPLEMENTED | SHA-256 + optional signing-cert fingerprint |
| Silent OTA install | NOT SUPPORTED | Normal Android security model requires user confirmation |
