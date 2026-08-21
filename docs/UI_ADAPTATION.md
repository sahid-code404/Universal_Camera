# Native UI adaptation from the supplied prototype

The supplied HTML is kept verbatim under `docs/reference/`. The Android UI translates its geometry and behavior into Compose + a real `TextureView` preview.

## Preserve

- rounded camera preview
- black lower control deck
- top translucent pill controls
- zoom/lens pills near bottom of preview
- scrollable mode rail with centered active mode
- gallery thumbnail / large shutter / camera switch geometry
- controls/settings sheets
- Pro manual controls
- ratio, timer, macro, grid, framing, location and volume-key settings
- Video resolution/FPS/HDR/stabilization controls when hardware supports them
- subtle press/selection animations
- no noisy focus/zoom toast spam

## Remove

- `RAW + JPEG`
- `JPEG only`
- `RAW/JPEG control`
- `Storage saver`
- `Motion photo`
- generic `Store videos efficiently` duplicate setting

## Replace with product-native controls

### Top bar
- `DNG` status chip: computational still contract; not a JPEG toggle
- HDR pill: cycles `Normal -> HDR -> HDR+ Auto`
- flash
- settings

### Photo settings
- Timer
- Macro policy when a usable lens supports close focus
- aspect ratio
- lens selection Auto/Manual
- preview pipeline Processed/RAW
- upscaling mode Native/Computational SR/1.5x/2x

### Pro settings
- Focus
- Shutter
- ISO
- exposure compensation
- white balance
- capability-aware resolution

### More settings
- location metadata
- sounds
- selfie mirroring
- quick-access controls
- launch mode
- volume key action
- framing/grid
- manual lens selection
- remember settings
- update channel / check for updates
- device capability diagnostics

## Capability rule

Never hardcode 50 MP, 4K60, telephoto, RAW or high-speed modes into the visible UI. Display only values validated on the selected lens. If a future synthetic mode exists, label it explicitly as enhanced/interpolated rather than native.
