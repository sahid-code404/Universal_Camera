# Feature status — truth table

Legend: `IMPLEMENTED`, `FOUNDATION`, `PLANNED`, `CAPABILITY-DEPENDENT`.

| Feature | Status | Notes |
|---|---|---|
| Compose camera UI | IMPLEMENTED | Native reproduction/adaptation of supplied layout |
| Camera permission flow | IMPLEMENTED | Contextual camera permission |
| Camera2 static discovery | IMPLEMENTED | No ID assumptions |
| Logical/physical lens metadata | IMPLEMENTED | Uses CameraCharacteristics / physical IDs |
| Physical output routing | FOUNDATION | Preview route supported where HAL exposes it; full probe still required |
| Runtime camera probing | PLANNED | Must test open/session/frame delivery and cache results |
| Processed preview | FOUNDATION | Camera2 TextureView preview present; Phase 2 transform matrix must be device-tested |
| RAW preview | CAPABILITY-DEPENDENT / PLANNED | Vulkan/native path not implemented |
| Tap-to-focus metering transform | PLANNED | Do not fake sensor coordinate mapping |
| Single RAW DNG | PLANNED | Phase 3 |
| Computational DNG | PLANNED | Requires validated multi-frame pipeline |
| RAW ring buffer/ZSL | PLANNED | Phase 4 |
| Frame scoring | API FOUNDATION | No production scoring kernel yet |
| Alignment/deghosting/fusion | API FOUNDATION | Native bridge only; algorithms not faked |
| HDR/HDR+ Auto | UI/API FOUNDATION | UI policy present; processing not yet implemented |
| Computational super-resolution | UI/API FOUNDATION | Never label ordinary upscale as SR |
| RAW video | CAPABILITY-DEPENDENT / PLANNED | No claim of universal support |
| OTA update check | IMPLEMENTED | GitHub Releases API |
| OTA integrity verification | IMPLEMENTED | SHA-256 + optional signing-cert fingerprint |
| Silent OTA install | NOT SUPPORTED | Normal Android security model requires user confirmation |
