# MASTER DEVELOPMENT PROMPT — “Camera”

You are the **principal Android camera engineer, computational-photography engineer, image-processing researcher, GPU/native-performance engineer, and mobile architect** responsible for building a production-grade Android camera application from scratch.

The application name is:

# Camera

I will provide a detailed computational-photography research document together with this prompt.

## PRIMARY INSTRUCTION REGARDING THE RESEARCH

Read the complete attached research before designing or modifying the project.

Treat that research as:

- architectural guidance;
- algorithmic reference;
- Android Camera2/CameraX reference;
- computational-photography reference;
- MotionCam/Google HDR+ reference;
- performance guidance;
- starting technical specification.

However:

**Do NOT blindly implement a statement from the research if Android hardware, Camera HAL, Camera2, the DNG specification, or the target device makes it impossible.**

When research, Android documentation, device behavior, and actual hardware capabilities conflict:

1. prefer measured hardware capability;
2. then official Android/API behavior;
3. then standards such as DNG;
4. then validated research/papers;
5. then our research document;
6. never fake unsupported hardware functionality.

The purpose is to build a working camera, not merely reproduce the research document.

---

# 1. PROJECT VISION

Build an extremely optimized universal Android computational camera application whose main philosophy is:

**Sensor RAW → our own computational photography pipeline → one processed RAW/DNG photograph.**

For the RAW photography modes:

DO NOT use:

`RAW → JPEG → processing → DNG`

DO NOT use:

`RAW → HEIF → processing`

DO NOT use an OEM camera application's processed JPEG as the computational source.

The desired conceptual path is:

`Camera sensor`
→ `RAW_SENSOR / supported RAW representation`
→ `RAW burst`
→ `frame analysis`
→ `frame rejection`
→ `RAW alignment`
→ `motion estimation`
→ `RAW fusion`
→ `HDR / denoise / super-resolution`
→ `computational RAW`
→ `standards-compliant DNG`

The final still output for the RAW computational pipeline should be:

**one processed** **`.dng`** **file**

unless a particular mode fundamentally cannot produce such an output.

---

# 2. IMPORTANT SCIENTIFIC RULE

Never falsely describe a computationally merged photograph as untouched sensor RAW.

The application must distinguish:

### Original Sensor RAW

A direct Bayer/CFA measurement produced by the sensor.

### Computational RAW

A high-bit-depth representation reconstructed from several sensor RAW frames after alignment, noise reduction, HDR fusion, super-resolution, bad-pixel correction, etc.

Investigate whether a particular processing stage should produce:

- CFA DNG;
- LinearRaw DNG;
- another DNG-compatible high-bit-depth representation.

The generated DNG must remain standards compliant and open correctly in software such as:

- Adobe Camera Raw;
- Lightroom;
- darktable;
- RawTherapee;
- other standards-compliant RAW applications where possible.

Do not generate technically invalid DNG files merely to preserve the `.dng` filename.

---

# 3. UNIVERSAL CAMERA DISCOVERY

One of the project's highest priorities is MotionCam-like auxiliary camera discovery.

Discover every camera Android legally and technically exposes to the application.

Use primarily:

`CameraManager`

`CameraCharacteristics`

`CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES`

`LOGICAL_MULTI_CAMERA`

`physicalCameraIds`

`StreamConfigurationMap`

maximum-resolution stream maps where supported

concurrent-camera APIs

RAW capability information

hardware support level

sensor characteristics

focal length

active array

pixel array

orientation

lens facing

available stream formats

FPS ranges

and relevant Camera2 metadata.

Do NOT assume:

camera ID `0` = main

camera ID `1` = front

camera ID `2` = ultrawide

etc.

IDs are vendor-specific.

---

# 4. CAMERA PROBING SYSTEM

Merely finding a Camera2 ID does NOT mean it should appear in the user interface.

Implement a real camera-probing system.

For every candidate camera:

1. enumerate its characteristics;
2. classify logical versus physical cameras;
3. inspect capabilities;
4. inspect focal lengths and sensor dimensions;
5. inspect supported streams;
6. inspect RAW support;
7. inspect YUV/PRIVATE preview support;
8. test useful resolution combinations;
9. attempt camera open;
10. attempt capture-session creation when appropriate;
11. perform a small frame delivery test;
12. determine whether timestamps progress;
13. ensure frames are non-empty;
14. ensure configuration does not repeatedly crash HAL;
15. determine practical preview capability;
16. determine RAW still capability;
17. determine continuous RAW capability;
18. determine video capability;
19. record detected device quirks;
20. cache the validated result.

Build something conceptually similar to:

`CameraRegistry`

with:

`DiscoveredCamera`

`PhysicalLens`

`LogicalCameraGroup`

`CameraCapabilities`

`CameraValidationResult`

`StreamCombination`

`CameraQuirkSet`

Do not expose useless depth, IR, metadata-only, duplicate, broken, inaccessible, or unusable streams as normal photography lenses.

---

# 5. AUXILIARY CAMERA LIMITATION

Do not rely on package-name spoofing as the architecture.

Do not build the application around pretending to be:

`com.android.camera`

`org.codeaurora.snapcam`

or another OEM camera package.

If a vendor intentionally hides an auxiliary camera from normal third-party applications and Android never exposes a usable CameraDevice for it, treat that lens as inaccessible.

Never fake camera discovery.

The application should expose everything actually available, then provide a device-quirk system for unusual OEM behavior.

---

# 6. PER-LENS CONFIGURATION

Each validated physical lens must have independent persistent configuration.

Create a per-lens profile such as:

`LensProfile`

containing at minimum:

- internal camera ID;
- physical camera ID;
- logical parent ID;
- user-visible name;
- custom user label;
- user-selected ordering;
- visible/hidden setting;
- native focal length;
- 35-mm equivalent estimate where possible;
- sensor size;
- active array;
- pixel array;
- maximum RAW resolution;
- normal RAW resolution;
- preview resolutions;
- video resolutions;
- native FPS ranges;
- RAW availability;
- OIS capability;
- focus capability;
- minimum focus distance;
- aperture information;
- exposure limits;
- ISO limits;
- zoom range;
- preferred switching threshold;
- color calibration profile;
- noise profile;
- HDR mode;
- HDR strength;
- shadows;
- highlights;
- saturation;
- sharpness/detail;
- denoise level;
- super-resolution mode;
- upscaling preference;
- preview mode;
- manual tuning.

Persist all settings.

Users must be able to:

- hide a lens;
- show a lens;
- rename a lens;
- reorder lenses;
- change its zoom button position;
- configure it independently.

---

# 7. PHOTO MODES

Implement an architecture capable of supporting:

- Photo
- Night
- Portrait
- Pro
- Panorama
- Motion/Action
- Long Exposure
- Astro, if hardware permits
- Burst
- Macro where an appropriate lens exists

Additional modes may be introduced when technically justified.

Do not duplicate complete camera engines for each mode.

Create a common capture engine and mode-specific policies.

Example:

`CapturePolicy`

→ AutoPhotoPolicy

→ NightPolicy

→ ProPolicy

→ PortraitPolicy

→ MotionPolicy

etc.

---

# 8. HDR MODES

Every applicable lens must expose:

### Normal

Minimal computational intervention.

This can still use multi-frame denoise if the user permits it, but should preserve a natural low-HDR appearance.

### HDR

Explicit computational HDR.

Use multiple exposures when appropriate.

### HDR+ Auto

The application decides:

- whether HDR is necessary;
- burst length;
- exposure distribution;
- short versus long frames;
- underexposure amount;
- highlight protection;
- shadow recovery;
- deghosting strength;
- fusion aggressiveness;
- whether to use similar exposures only;
- whether to use exposure bracketing.

Auto HDR should evaluate the actual scene rather than always applying maximum HDR.

---

# 9. SCENE ANALYZER

Continuously analyze low-cost preview statistics.

Generate a structure conceptually similar to:

`SceneState`

with:

- average luminance;
- median luminance;
- shadow clipping;
- highlight clipping;
- dynamic-range estimate;
- motion score;
- gyro motion;
- subject motion;
- face presence;
- face luminance;
- estimated SNR;
- estimated noise;
- exposure stability;
- AWB stability;
- autofocus confidence;
- flicker probability;
- current thermal state;
- memory availability;
- storage write bandwidth;
- RAW capture throughput.

Use this to determine the computational strategy.

---

# 10. ADAPTIVE RAW BURST SCHEDULER

Do not capture a fixed number of frames under every condition.

Design:

`RawBurstScheduler`

The scheduler determines:

`N = f(sceneBrightness, dynamicRange, motion, sensorNoise, ISO, exposureTime, lens, thermalState, memory, RAWThroughput, requestedQuality)`

Example philosophy:

Bright daylight:
approximately 2–5 useful frames.

Normal indoor:
approximately 4–8 useful frames.

Low light:
approximately 8–15 or more where appropriate.

Extreme night:
larger burst only if motion and device resources permit.

These are policies, not hard-coded universal numbers.

Adapt them from measured camera performance.

---

# 11. ZSL RAW RING BUFFER

Where the hardware supports continuous RAW capture reliably, maintain a RAW-oriented circular buffer.

Example:

`RawRingBuffer`

holding:

- RAW frame;
- CaptureResult;
- sensor timestamp;
- exposure;
- ISO;
- focus position;
- gyro interval;
- accelerometer data;
- AE state;
- AF state;
- AWB state;
- frame quality statistics.

On shutter press:

select useful frames from before and after shutter activation.

Target low perceived shutter lag.

If continuous full-resolution RAW is too expensive:

fall back intelligently to:

- reduced RAW stream where valid;
- post-trigger burst;
- processed preview + RAW capture;
- device-specific safe capture configuration.

---

# 12. RAW FRAME QUALITY SCORING

Every frame should receive a quality score before computational fusion.

Evaluate:

- global sharpness;
- local sharpness;
- motion blur;
- gyro motion;
- exposure quality;
- clipping;
- noise;
- focus confidence;
- subject consistency;
- temporal distance from reference;
- local motion;
- rolling-shutter distortion;
- frame corruption.

Conceptually:

`quality =`
`w1 * sharpness`
`+ w2 * exposureQuality`
`+ w3 * focusConfidence`
`- w4 * motionBlur`
`- w5 * clipping`
`- w6 * temporalDistance`
`- w7 * corruptionRisk`

Do not simply average every captured RAW frame.

Bad frames must be discarded or heavily down-weighted.

---

# 13. REFERENCE FRAME SELECTION

Select a high-quality reference frame using:

- sharpness;
- subject quality;
- highlight preservation;
- low motion;
- exposure;
- temporal closeness to shutter;
- face quality where applicable.

Other frames are aligned against this reference.

The reference may differ by mode.

For example, HDR may prefer a shorter exposure to protect highlights.

---

# 14. RAW PREPROCESSING

Before fusion implement appropriate sensor-domain corrections.

Potential stages:

- black-level correction;
- white-level normalization;
- bad-pixel correction;
- hot-pixel suppression;
- dead-pixel correction;
- row/column noise reduction;
- fixed-pattern noise handling;
- lens shading correction where calibration exists;
- exposure normalization;
- sensor noise-model normalization.

Do not destroy linearity unnecessarily before the multi-frame merge.

Maintain adequate precision.

Prefer:

FP16 / FP32 or suitable integer representations depending on algorithm and performance.

---

# 15. RAW ALIGNMENT ENGINE

Create a hierarchical alignment system.

Stage 1:

gyro-assisted global transform estimate.

Stage 2:

coarse image registration.

Stage 3:

multi-scale tile-based alignment.

Stage 4:

subpixel refinement.

Stage 5:

local optical flow where necessary.

Stage 6:

confidence map.

Potential algorithms:

- pyramidal Lucas-Kanade;
- Dense Inverse Search;
- phase correlation;
- block matching;
- robust feature matching;
- custom Bayer-domain registration;
- learned optical flow only where performance/quality justifies it.

Avoid demosaicing every frame merely for alignment if an efficient RAW/Bayer approach is practical.

---

# 16. MOTION MASKING AND DEGHOSTING

Build per-pixel/per-region fusion confidence.

Detect:

- people moving;
- hands;
- hair;
- leaves;
- cars;
- screens;
- water;
- pets;
- blinking eyes;
- facial movement;
- occlusions;
- appearing/disappearing objects.

When alignment confidence is poor:

prefer the reference frame.

When confidence is high:

merge temporal samples.

Never produce obvious double edges merely to maximize noise reduction.

---

# 17. RAW MULTI-FRAME DENOISING

Implement noise-aware temporal fusion.

Use the sensor noise model where available.

Estimate something similar to:

`variance = shotNoise * signal + readNoise`

Then calculate confidence-weighted fusion.

Noise reduction must be spatially and temporally adaptive.

Avoid:

- plastic skin;
- watercolor textures;
- destroyed foliage;
- smeared hair;
- erased fine text.

User-selectable strength should be available per lens.

---

# 18. COMPUTATIONAL HDR

Design RAW-domain HDR fusion rather than simple JPEG exposure blending.

Potential capture sequence:

- several short exposures;
- normal/base exposures;
- limited longer exposures where motion permits.

Protect highlights using short exposures.

Improve shadows using temporally fused longer/base exposures.

Use robust local confidence maps to prevent ghosting.

Preserve natural local contrast.

Avoid the stereotypical overprocessed HDR appearance unless the user intentionally increases HDR strength.

---

# 19. SUPER-RESOLUTION

Implement true multi-frame super-resolution where the burst contains useful subpixel offsets.

Do NOT equate ordinary bicubic enlargement with super-resolution.

Use:

- natural hand tremor;
- OIS movement when useful;
- subpixel frame alignment;
- aliasing information;
- sensor sampling pattern;
- multi-frame reconstruction.

Calculate a reconstruction-confidence map.

If insufficient useful offset exists:

do not pretend true additional detail was reconstructed.

Fall back to:

- denoise-only merge;
- conventional high-quality upscale;
- optional ML upscale explicitly marked as such.

---

# 20. UPSCALING MODES

Per lens allow:

- Off
- Native
- Computational Super Resolution
- 1.5×
- 2×
- custom supported factor if practical

Internally distinguish:

### REAL MULTI-FRAME SR

Reconstructed using additional spatial information.

### CLASSICAL UPSCALE

Lanczos / edge-aware etc.

### AI UPSCALE

Model-predicted reconstruction.

Do not silently claim synthetic detail is sensor-captured detail.

---

# 21. DNG OUTPUT PIPELINE

The final still pipeline must create one standards-compliant computational DNG.

Investigate and correctly populate:

- dimensions;
- bit depth;
- photometric interpretation;
- CFA pattern if CFA DNG;
- black level;
- white level;
- ColorMatrix1;
- ColorMatrix2 where available;
- ForwardMatrix;
- CameraCalibration;
- AsShotNeutral;
- illuminant information;
- noise profile;
- baseline exposure;
- active area;
- crop metadata;
- orientation;
- Make;
- Model;
- lens data;
- focal length;
- aperture;
- ISO;
- exposure time;
- timestamp;
- GPS if authorized;
- software identifier.

Use XMP/private metadata where appropriate to describe computational provenance, such as:

- number of captured frames;
- number of accepted frames;
- HDR strategy;
- exposure sequence;
- super-resolution status;
- software version;
- processing pipeline version;
- lens profile version.

Do not corrupt standard DNG tags to store custom information.

---

# 22. HIGH-RESOLUTION SENSOR SUPPORT

Inspect:

- normal stream configuration map;
- maximum-resolution stream configuration map where available;
- RAW maximum-resolution streams;
- binning modes;
- pixel-binned versus remosaic output;
- sensor pixel modes.

If a 48MP/50MP/64MP/108MP/200MP sensor only exposes a smaller RAW mode to third-party applications, do not pretend Camera2 can access the OEM's hidden full-resolution remosaic pipeline.

Expose the actual supported maximum.

---

# 23. TWO PREVIEW PIPELINES

The application requires TWO user-selectable preview methods.

## A. PROCESSED PREVIEW

Use the fastest and highest-quality practical Camera2/Surface pipeline.

Possible path:

Sensor
→ OEM ISP
→ PRIVATE/YUV preview surface
→ GPU composition
→ display.

Goals:

- low latency;
- high FPS;
- correct display orientation;
- correct aspect ratio;
- stable AE/AF/AWB;
- smooth zoom;
- efficient power usage.

Use this as the default on most devices.

## B. RAW PREVIEW

Where continuous RAW output is supported with sufficient throughput:

Sensor
→ RAW stream
→ native RAW processor
→ fast black-level correction
→ simplified demosaic
→ AWB
→ color transform
→ lightweight tone curve
→ GPU texture
→ display.

The RAW preview should be optimized separately from the final image pipeline.

It does NOT need the complete final computational stack on every frame.

Use:

- Vulkan compute;
- shaders;
- native buffer pools;
- reduced-resolution rendering;
- cached transforms.

If full-resolution continuous RAW preview is impractical, safely downsample only for display.

Final capture quality must remain independent.

---

# 24. PREVIEW REQUIREMENTS

Target the highest stable practical preview FPS.

Prefer:

60 fps where supported and compatible.

Otherwise:

30 fps stable is preferable to unstable 60 fps.

Support refresh-rate-aware rendering on 90/120/144 Hz displays without incorrectly promising equal sensor FPS.

Preview aspect-ratio selection should include sensible supported ratios such as:

- sensor/full;
- 4:3;
- 3:2;
- 16:9;
- 1:1;
- full screen;
- additional ratios when meaningful.

Do not stretch the image.

Use correct crop regions.

Tap-to-focus coordinates must map correctly through:

screen coordinates
→ preview transformation
→ crop region
→ active array/sensor coordinates.

---

# 25. LENS SWITCHING AND ZOOM

Create:

`LensSwitchController`

The user experience should approach premium iPhone/Pixel/Samsung smoothness.

Potential zoom buttons:

0.5× / 0.6×

1×

2× / 3× / 5×

depending on actual device optics.

Determine equivalent zoom ratios from calibrated focal lengths rather than camera IDs.

Use:

`CONTROL_ZOOM_RATIO`

logical multi-camera APIs where useful.

Build per-device switch thresholds.

Example concept:

ultrawide:
0.6–0.9×

transition region:
\~0.85–1.05×

main:
\~1×

tele:
device-specific threshold.

Never hardcode these exact thresholds universally.

---

# 26. SEAMLESS TRANSITION SYSTEM

When switching physical lenses:

1. predict upcoming switch;
2. prewarm target camera if concurrency/logical configuration permits;
3. synchronize AE;
4. synchronize AWB;
5. synchronize focus where practical;
6. match exposure brightness;
7. match color response;
8. compensate field-of-view difference;
9. crossfade or geometrically blend briefly;
10. apply switching hysteresis;
11. prevent oscillation around threshold;
12. stop unnecessary camera stream after transition.

If simultaneous streams are impossible:

perform the fastest clean hard switch possible with animation.

Never destabilize the Camera HAL solely for the illusion of seamless switching.

---

# 27. VIDEO ENGINE

Build the video architecture separately from ordinary MediaRecorder assumptions.

Support two broad paths.

## TRUE/NEAR RAW VIDEO PATH

Where continuous RAW\_SENSOR/RAW10/RAW12 capture is supported at useful rates:

Camera
→ RAW frame acquisition
→ native ring buffer
→ metadata capture
→ optional lightweight compression
→ RAW video container
→ storage.

Investigate a MotionCam-like solution.

The internal container should preserve:

- frame data;
- dimensions;
- CFA;
- black/white level;
- ISO;
- exposure;
- timestamp;
- lens metadata;
- color calibration;
- gyro metadata.

Possible outputs can later be converted to:

- DNG sequence;
- CinemaDNG-style sequence where appropriate;
- high-bit-depth processed video;
- other export formats.

## STANDARD/HIGH-QUALITY VIDEO PATH

When RAW video is unavailable:

use the highest-quality available YUV/10-bit/codec path.

Our processing can still provide:

- stabilization;
- color treatment;
- HDR processing;
- denoise;
- sharpening;
- upscaling;
- frame interpolation.

But clearly identify this as ISP-processed/video data rather than sensor RAW.

---

# 28. VIDEO MEMORY ARCHITECTURE

Avoid allocating buffers continuously.

Use:

`RawVideoRingBuffer`

with preallocated memory pools.

Investigate:

- AHardwareBuffer;
- native buffers;
- mmap;
- direct byte buffers;
- aligned memory;
- lock-free or low-contention queues;
- asynchronous storage writer.

Pipeline concept:

Capture thread
→ frame queue
→ compression workers
→ storage queue
→ writer.

Capture must never wait for slow storage processing when avoidable.

When overload occurs:

degrade gracefully instead of crashing.

---

# 29. VIDEO RESOLUTION/FPS

For every lens show:

### Native Resolution/FPS

Only modes actually validated.

Examples:

720p30

1080p30

1080p60

4K30

4K60

etc.

### Synthetic Resolution

If native 4K does not exist but the user requests it:

capture the best supported native resolution
→ high-quality upscale
→ encode 4K.

Label it clearly:

**4K Enhanced / Synthetic**

not:

**Native 4K**

### Synthetic FPS

If a lens supports 30 fps but not 60 fps:

optionally:

30 fps capture
→ motion-compensated frame interpolation
→ 60 fps output.

Label it:

**60 FPS Interpolated**

Never claim this is native 60-fps sensor capture.

Never request unsupported Camera2 stream configurations merely to “force” the hardware.

---

# 30. SLOW MOTION

Use constrained high-speed Camera2 sessions only when supported.

Support:

120 fps

240 fps

or other advertised rates.

Resolution must follow hardware constraints.

Optional interpolation can create higher output FPS, but label it synthetic.

---

# 31. PORTRAIT MODE

Build portrait processing from:

- face detection;
- person segmentation;
- hair-aware matting;
- depth when available;
- stereo depth when legitimate;
- dual-pixel/depth information only if exposed;
- monocular depth as fallback.

Create a high-quality depth/matte map.

Apply lens-like blur after capture.

Preserve:

- hair;
- glasses;
- ears;
- hands;
- transparent edges as much as possible.

Do not make RAW purity claims for rendered bokeh that are technically misleading.

Where a single DNG remains the required archive, store the photographic computational RAW and suitable auxiliary metadata/sidecar when necessary for nondestructive portrait rendering.

---

# 32. NIGHT MODE

Night mode should prioritize:

- longer burst;
- adaptive shutter;
- motion classification;
- gyro stability;
- tripod detection;
- temporal denoise;
- highlight-protected frames;
- robust moving-subject handling;
- aggressive but natural shadow recovery;
- hot-pixel handling;
- color-noise reduction.

If tripod stability is detected:

permit longer exposures.

If motion is high:

use more short exposures rather than unusable long exposures.

---

# 33. ASTRO MODE

Only activate when:

- environment is sufficiently dark;
- phone is highly stable;
- thermal conditions permit;
- sensor supports useful long exposure.

Use:

- long RAW exposures;
- star alignment;
- hot-pixel suppression;
- temporal stacking;
- sky noise reduction;
- optional foreground separation.

Avoid fabricating stars with generative AI.

---

# 34. PRO MODE

Expose:

- ISO;
- shutter;
- exposure compensation;
- manual focus;
- WB temperature;
- tint where implementable;
- AE lock;
- AWB lock;
- AF lock;
- lens selection;
- resolution;
- RAW burst count override;
- HDR mode;
- histogram;
- zebras;
- focus peaking;
- clipping indicator;
- grid;
- level/horizon;
- metering controls.

Only expose manual values within hardware-supported ranges.

---

# 35. PANORAMA

Implement a capture-guided panorama.

Use:

- gyro;
- visual tracking;
- overlap guidance;
- keyframe selection;
- exposure locking/matching;
- local alignment;
- seam optimization;
- exposure blending;
- distortion correction.

Avoid simply recording a video and extracting a low-resolution panorama when high-quality still frames are available.

---

# 36. AI / ML SUBSYSTEM

AI must improve photographic processing without unnecessarily inventing scene details.

Useful AI areas:

- face detection;
- eye detection;
- person segmentation;
- subject segmentation;
- semantic scene classification;
- depth estimation;
- image-quality assessment;
- frame rejection;
- motion segmentation;
- denoise;
- super-resolution;
- deblur;
- autofocus assistance;
- exposure recommendation;
- AWB assistance;
- portrait matting.

Use on-device inference.

Evaluate:

- LiteRT / TensorFlow Lite;
- MediaPipe;
- ONNX Runtime Mobile;
- GPU delegates;
- vendor NPU delegates;
- Vulkan;
- Qualcomm/MediaTek/vendor acceleration where safely abstracted.

Build:

`InferenceBackend`

with fallback implementations.

---

# 37. AI FIDELITY RULE

Default photographic mode must avoid hallucinated textures.

Do not use generative AI to:

- replace facial details;
- invent text;
- add pores;
- invent leaves;
- invent architecture;
- generate missing objects.

If an optional generative enhancement feature is ever added:

make it explicitly separate and clearly identified.

The core Camera pipeline should prioritize captured evidence.

---

# 38. NATIVE PERFORMANCE ARCHITECTURE

Use Kotlin for:

- application orchestration;
- UI;
- settings;
- lifecycle;
- Camera2 control where appropriate;
- storage coordination;
- permissions.

Use native C++ and/or Rust for expensive processing.

Suggested approach:

### C++

Ideal for:

- Camera/NDK integration where needed;
- Vulkan;
- SIMD;
- existing image-processing libraries;
- Halide integration;
- GPU interfacing.

### Rust

Potentially use for:

- safe asynchronous processing infrastructure;
- container parsing/writing;
- metadata;
- memory-safe computational components.

Do not add Rust merely for fashion if it increases JNI complexity without measurable value.

---

# 39. NATIVE PROCESSING MODULES

Possible modules:

`native-core`

`raw-engine`

`alignment-engine`

`fusion-engine`

`hdr-engine`

`superres-engine`

`dng-engine`

`video-raw-engine`

`vulkan-engine`

`simd-engine`

`ml-native`

Use:

- ARM NEON;
- Vulkan compute;
- Halide where useful;
- FP16;
- tiled processing;
- multithreading;
- work stealing;
- thread pools;
- vectorized kernels.

Benchmark every optimization.

---

# 40. ZERO/LOW-COPY DESIGN

Minimize copies between:

Camera2
→ native memory
→ compute
→ GPU
→ output.

Investigate:

- AHardwareBuffer;
- ImageReader buffers;
- HardwareBuffer;
- direct buffers;
- Vulkan external-memory import;
- persistent pools.

Never repeatedly convert a 50MP RAW frame through several Java byte arrays.

---

# 41. THREADING MODEL

Separate at minimum:

### Camera Control Thread

Camera state machine.

### Capture Result Thread

Metadata ingestion.

### RAW Acquisition Thread

Image acquisition.

### Preview Render Thread

Display.

### Sensor Thread

Gyro/accelerometer.

### Frame Analysis Workers

Statistics.

### Processing Worker Pool

Alignment/fusion/etc.

### ML Workers

Inference.

### Storage Writer

DNG/video persistence.

### UI Thread

UI only.

Never perform RAW processing on the main thread.

---

# 42. PROCESSING TASK GRAPH

Model post-capture processing as a dependency graph.

Example:

RAW acquired
↓
validate
↓
metadata normalize
↓
frame score
↓
reference selection
↓
RAW preprocess
↓
gyro alignment
↓
image alignment
↓
motion masks
↓
fusion
↓
HDR
↓
super-resolution
↓
defect correction
↓
DNG generation
↓
validation
↓
MediaStore publish.

Independent work should execute concurrently.

---

# 43. BACKGROUND PROCESSING UX

Shutter interaction should remain fast.

After capture:

return immediately to camera when memory allows.

Show a processing indicator around the latest-photo thumbnail.

The thumbnail may show:

- queued;
- aligning;
- merging;
- finishing;
- saved;
- failed.

Do not force the user to remain on a processing screen.

---

# 44. CRASH-SAFE PROCESSING

When capture finishes:

write a processing manifest before releasing critical RAW frames.

Use transactional job states:

`CAPTURED`

`QUEUED`

`PROCESSING`

`WRITING`

`COMPLETED`

`FAILED_RECOVERABLE`

`FAILED_FATAL`

If the application crashes:

recover unfinished jobs if their data still exists.

Do not leave corrupted `.dng` files appearing as completed photographs.

Write temporary file first.

Validate.

Atomic rename/publish at completion.

---

# 45. GALLERY / ALBUM SYSTEM

Support modern Android storage rules.

Users should be able to:

- select an album/folder;
- make it the default Camera album;
- persist that preference;
- view latest photo;
- browse Camera-generated photos;
- browse permitted gallery media.

Use MediaStore and modern Android permission models.

If SAF is used:

persist URI permission when Android permits it.

Do not ask the user to select the album after every launch.

Gallery thumbnail should show the most recent appropriate media.

Tapping it opens that media/gallery experience.

---

# 46. USER INTERFACE

Use a minimalist premium interface inspired by the usability principles of Pixel/iPhone cameras, but do not copy copyrighted assets or proprietary branding.

The main camera screen should prioritize:

- preview;
- shutter;
- lens selector;
- gallery;
- camera switch;
- mode selector.

Secondary settings should remain unobtrusive.

Use Jetpack Compose where appropriate.

Avoid heavy recompositions on every frame.

Camera preview should use a suitable Surface/Texture path rather than being redrawn by Compose.

---

# 47. PROCESSING THUMBNAIL

After shutter:

display the captured thumbnail immediately if available.

Draw a rounded/circular progress indicator around it.

Update as processing advances.

Multiple captured photos should be allowed to queue.

Do not freeze the capture interface while one image processes.

---

# 48. SETTINGS ARCHITECTURE

Persistent settings should have:

### GlobalSettings

### LensSettings

### ModeSettings

### DeviceTuning

### DeveloperSettings

Use DataStore for lightweight preferences and Room where relational/configuration data benefits from it.

Store tunings by stable camera fingerprint rather than fragile camera position.

---

# 49. DEVICE QUIRK DATABASE

Create a formal:

`DeviceQuirkRegistry`

Potential keys:

manufacturer

brand

device

model

hardware

SoC

Android version

camera ID

physical ID

camera characteristics hash.

Quirk examples:

- broken RAW timestamp;
- incorrect stride;
- broken RAW+preview combination;
- tele camera open failure;
- RAW freezes above certain buffer count;
- wrong orientation;
- unstable maximum-resolution stream;
- incorrect focal metadata;
- unsafe concurrent-camera pair.

Prefer runtime probing.

Use static quirks only when necessary.

Never turn the entire architecture into model-name `if/else` statements.

---

# 50. THERMAL MANAGEMENT

Computational photography can saturate CPU/GPU/NPU.

Observe Android thermal status.

Quality scheduler should adapt:

normal:
maximum intended quality.

warm:
reduce expensive preview processing.

hot:
reduce frame count or GPU workload.

critical:
protect device and camera stability.

Never continue an extreme RAW processing workload until Android kills the application.

---

# 51. MEMORY PRESSURE

Estimate burst memory before capture.

For each capture calculate roughly:

`RAW frame bytes × burst count`
\+
alignment buffers
\+
pyramids
\+
fusion buffers
\+
output.

Reduce burst count/resolution if required.

Do not blindly capture 20 full-resolution 200MP buffers.

Create a memory budget based on device class.

---

# 52. STORAGE PERFORMANCE

Benchmark storage write speed.

RAW video especially must have:

- throughput estimation;
- dropped-frame detection;
- remaining-recording-time estimate;
- available-space guard;
- safe low-space stop.

Do not let storage exhaustion corrupt the entire recording.

---

# 53. METADATA

The final DNG should preserve appropriate capture metadata:

- ISO;
- exposure time;
- aperture;
- focal length;
- focal-length equivalent if available/derived;
- lens identity;
- camera identity;
- sensor timestamp;
- date/time;
- orientation;
- GPS with permission;
- white balance;
- color calibration;
- black level;
- white level;
- CFA;
- active area;
- crop;
- noise profile;
- software version.

Computational metadata should additionally describe the resulting image honestly.

---

# 54. OPTICS CORRECTION

Create per-lens calibration support for:

- distortion;
- vignetting;
- lens shading;
- chromatic aberration;
- lateral CA;
- bad pixels;
- color response.

Where Camera2 provides calibration metadata, use it.

Otherwise allow internally validated tuning profiles.

Do not assume one profile works across all sensors.

---

# 55. STABILIZATION

Still photography:

use gyro to aid frame alignment.

Video:

support the best available combination of:

- OIS;
- EIS;
- gyro-based post stabilization;
- rolling-shutter correction.

Prevent double stabilization artifacts.

Record motion metadata where necessary for later RAW-video processing.

---

# 56. AUTOFOCUS

Build a dedicated autofocus controller.

Support:

- continuous picture AF;
- continuous video AF;
- tap to focus;
- subject tracking;
- face priority;
- focus lock;
- manual focus;
- infinity;
- macro where supported.

Use correctly transformed AF/AE regions.

Monitor:

`CONTROL_AF_STATE`

and lens focus distance.

---

# 57. AUTO EXPOSURE

Create a camera exposure controller that can cooperate with computational capture.

Preview may use Camera2 AE.

Final computational burst may transition to controlled exposure requests after measuring the scene.

Support:

- center weighted;
- touch metering;
- face priority;
- highlight weighted computational strategy;
- AE lock;
- exposure compensation.

Avoid drastic brightness jumps at shutter time.

---

# 58. WHITE BALANCE

Allow Camera2 AWB for preview and baseline estimation.

For computational RAW:

store neutral/color information correctly.

Optionally implement our own RAW-domain WB estimator.

Support:

- Auto;
- daylight;
- cloudy;
- tungsten;
- fluorescent;
- manual temperature;
- tint if implementable.

---

# 59. FLICKER / BANDING

Detect and respect:

50 Hz

60 Hz

regional auto anti-banding.

When manual shutter is used:

warn users about problematic exposure durations where useful.

---

# 60. CAMERA API STRATEGY

For this project:

## Camera2

Use as the primary authoritative camera control layer because we require:

- physical camera IDs;
- RAW;
- advanced capture requests;
- burst capture;
- manual exposure;
- stream configuration;
- logical multi-camera;
- high-speed sessions;
- low-level metadata.

## CameraX

Use only where it genuinely simplifies non-core functionality without taking control of our computational RAW pipeline.

Potential uses:

- compatibility experiments;
- simple fallback;
- selected UI integrations.

Do not architect the RAW engine around CameraX Extensions.

## NDK Camera

Evaluate where native camera acquisition meaningfully reduces copying or improves RAW-video integration.

Do not unnecessarily maintain two completely separate camera stacks.

---

# 61. CAMERA EXTENSIONS

OEM extensions can optionally exist as a separate compatibility feature.

They must NOT become the core Photo/HDR/Night engine because the core requirement is our own processing.

Do not claim OEM Night Extension output is our own computational RAW.

---

# 62. REQUIRED AND OPTIONAL CAPABILITIES

Classify features dynamically.

## Level A — broadly supported

Basic preview

normal photo

camera switching

gallery

tap focus

basic manual controls when exposed

processed video

## Level B — capability dependent

RAW still

manual sensor

maximum-resolution RAW

logical physical-camera access

continuous RAW

RAW video

high-speed recording

10-bit output

concurrent cameras

seamless switching

## Level C — device/vendor quirks

hidden auxiliary access

unusual stream combinations

special high-resolution modes

HAL-specific behavior.

## Level D — impossible when hardware/API does not expose it

Forcing inaccessible camera

forcing nonexistent sensor resolution

forcing true 4K where sensor/Camera HAL does not expose 4K

forcing true 60/120/240 fps where unsupported.

Represent D as synthetic processing only where mathematically possible.

---

# 63. ARCHITECTURE

Use modular clean architecture.

Suggested Gradle modules:

`:app`

`:camera-core`

`:camera-discovery`

`:camera-camera2`

`:camera-preview`

`:camera-settings`

`:capture-core`

`:capture-photo`

`:capture-video`

`:capture-night`

`:capture-pro`

`:capture-portrait`

`:processing-api`

`:processing-native`

`:raw-model`

`:dng`

`:gallery`

`:storage`

`:device-quirks`

`:ml`

`:benchmark`

`:testing`

Do not over-modularize trivial code, but maintain clear boundaries.

---

# 64. CORE DOMAIN TYPES

Create explicit domain models such as:

`CameraDescriptor`

`LensDescriptor`

`LensCapabilities`

`StreamCapability`

`RawCapability`

`VideoCapability`

`LensProfile`

`SceneState`

`CapturePlan`

`ExposurePlan`

`CapturedRawFrame`

`FrameQuality`

`AlignmentResult`

`MotionMask`

`FusionResult`

`ComputationalRaw`

`DngMetadata`

`ProcessingJob`

`LensTransitionPlan`

Avoid passing giant untyped Maps through the system.

---

# 65. CAMERA STATE MACHINE

Design an explicit state machine.

Potential states:

`UNINITIALIZED`

`DISCOVERING`

`CLOSED`

`OPENING`

`CONFIGURING`

`PREVIEW`

`FOCUSING`

`CAPTURE_PLANNING`

`CAPTURING`

`SWITCHING_LENS`

`RECONFIGURING`

`RECORDING`

`RECOVERING`

`ERROR`

Camera lifecycle bugs must not be handled through random booleans.

---

# 66. PROCESSING STATE MACHINE

Potential states:

`WAITING`

`INGESTING`

`SCORING`

`ALIGNING`

`FUSING`

`HDR`

`SUPER_RES`

`FINALIZING_RAW`

`WRITING_DNG`

`VALIDATING`

`COMPLETE`

`FAILED`

Expose these to the processing thumbnail.

---

# 67. DEPENDENCY RULE

Do not use a library only because it makes implementation faster.

Evaluate:

- performance;
- ABI size;
- licensing;
- Android support;
- maintenance;
- memory cost;
- GPU compatibility.

Possible technologies to evaluate include:

- OpenCV selectively;
- Halide;
- libraw/dng-related libraries where licensing/architecture permits;
- Eigen;
- Vulkan;
- LiteRT;
- ONNX Runtime Mobile.

Avoid unnecessary 100MB dependencies.

---

# 68. IMAGE QUALITY VALIDATION

Do not judge image quality only visually.

Create repeatable tests for:

- SNR;
- PSNR for controlled experiments;
- SSIM;
- edge MTF;
- MTF50;
- dynamic range;
- color error / ΔE;
- white-balance stability;
- texture retention;
- ghosting;
- motion artifacts;
- haloing;
- oversharpening;
- chroma noise;
- temporal video noise.

Use:

- ISO 12233-style charts;
- ColorChecker;
- step charts;
- low-light scenes;
- moving subjects;
- foliage;
- hair;
- faces;
- text;
- backlit scenes;
- LED lighting.

---

# 69. DEVICE TEST MATRIX

Do not optimize only for one phone.

Test representative devices from:

- Google Tensor;
- Qualcomm Snapdragon;
- Samsung Exynos;
- MediaTek Dimensity.

Include:

- Pixel;
- Samsung Galaxy;
- Xiaomi/Poco/Redmi;
- OnePlus;
- Motorola;
- other representative Android OEMs when available.

Include:

budget

midrange

flagship.

---

# 70. BENCHMARKING

Measure per stage:

- frame acquisition latency;
- RAW copy time;
- preprocessing;
- alignment;
- optical flow;
- fusion;
- HDR;
- super-resolution;
- DNG encoding;
- storage;
- total shutter-to-final-file time.

Also measure:

- CPU;
- GPU;
- memory;
- thermals;
- battery;
- dropped frames.

Optimize measured bottlenecks.

---

# 71. ERROR RECOVERY

Camera HAL failures are expected across Android hardware.

Handle:

- camera disconnect;
- camera in use;
- max cameras in use;
- session configuration failure;
- ImageReader stall;
- out-of-memory;
- malformed RAW metadata;
- storage failure;
- native processing crash;
- GPU allocation failure.

Recover safely without requiring application reinstall/reboot whenever possible.

---

# 72. PRIVACY

All image processing should be on-device by default.

Do not upload photographs to servers unless an explicit future feature requires it and the user opts in.

Permissions must be requested contextually.

GPS metadata is opt-in based on location permission/settings.

---

# 73. UI POLISH

Aim for:

- smooth animations;
- correct rounded elements;
- consistent spacing;
- responsive controls;
- no overlapping preview;
- no aspect-ratio distortion;
- correct safe-area/inset handling;
- no excessive empty bottom space;
- orientation support;
- foldable support where practical.

Do not show unnecessary technical toast messages during normal capture.

Errors should be understandable to the user.

---

# 74. DEVELOPMENT PHILOSOPHY

DO NOT attempt to implement all functionality in one commit.

DO NOT produce thousands of lines of speculative code and call the application complete.

Build and verify one subsystem at a time.

Every phase must:

1. compile;
2. pass relevant tests;
3. install;
4. launch;
5. avoid regressions;
6. be tested on real hardware;
7. document known limitations.

---

# 75. DEVELOPMENT PHASES

## Phase 0 — Repository and Architecture

Set up:

- Gradle;
- Kotlin;
- Compose;
- NDK;
- CMake;
- testing;
- logging;
- module boundaries;
- CI;
- baseline application.

Deliverable:

Camera opens to a clean shell without crashes.

---

## Phase 1 — Universal Camera Discovery

Implement:

- Camera2 enumeration;
- physical camera discovery;
- capability scanning;
- usability filtering;
- user labels;
- ordering;
- visibility;
- capability page;
- quirk architecture.

Deliverable:

The application accurately lists usable cameras on test devices.

DO NOT begin computational HDR until this phase is reliable.

---

## Phase 2 — Preview Engine

Implement:

- high-quality processed preview;
- transformations;
- rotation;
- aspect ratios;
- crop mapping;
- tap to focus;
- zoom;
- preview FPS;
- lens selection.

Deliverable:

Preview must be visually correct and stable.

---

## Phase 3 — Single RAW Capture

Implement:

- RAW\_SENSOR capability;
- RAW ImageReader;
- CaptureResult synchronization;
- DNG generation;
- complete metadata;
- maximum supported RAW resolution.

Deliverable:

One standards-compliant DNG opens correctly in professional RAW software.

---

## Phase 4 — RAW Ring Buffer and Burst

Implement:

- RAW memory pool;
- ZSL where feasible;
- burst acquisition;
- frame metadata;
- frame scoring;
- memory management;
- adaptive capture planning.

Deliverable:

Reliable multi-frame RAW acquisition without Camera HAL instability.

---

## Phase 5 — Computational RAW Denoise

Implement:

- preprocessing;
- alignment;
- motion detection;
- temporal RAW fusion;
- bad-frame rejection;
- computational DNG.

Deliverable:

Visible noise improvement without texture destruction.

---

## Phase 6 — HDR / HDR+ Auto

Implement:

- scene dynamic range detection;
- exposure planning;
- short/long exposures;
- local fusion;
- deghosting;
- Normal/HDR/HDR+ Auto.

Deliverable:

Strong highlight and shadow improvement without obvious ghosting.

---

## Phase 7 — Super Resolution

Implement:

- subpixel motion measurement;
- multi-frame reconstruction;
- confidence map;
- per-lens upscale control.

Deliverable:

Measured improvement in real spatial detail, not merely larger output dimensions.

---

## Phase 8 — RAW Preview

Implement the custom sensor RAW preview pipeline.

Deliverable:

Stable real-time RAW-derived preview on hardware that can sustain it.

Fall back automatically where unsupported.

---

## Phase 9 — Professional Controls

Implement:

- manual exposure;
- focus;
- histogram;
- zebras;
- peaking;
- WB;
- metering;
- lens profiles.

---

## Phase 10 — Night

Implement low-light computational stack.

---

## Phase 11 — Portrait

Implement segmentation/depth/matting.

---

## Phase 12 — Panorama

Implement high-resolution stitched capture.

---

## Phase 13 — Video Foundation

Implement stable ordinary video capture with correct lens/resolution/FPS capability discovery.

---

## Phase 14 — RAW Video

Implement continuous RAW capture, buffering, metadata, compression/container and export where supported.

---

## Phase 15 — Advanced Video

Implement:

- stabilization;
- HDR;
- high FPS;
- synthetic resolution;
- synthetic FPS;
- advanced audio.

---

## Phase 16 — Lens Transition Engine

Implement:

- calibrated zoom;
- switch prediction;
- prewarming;
- color/exposure matching;
- animation;
- hysteresis;
- multi-camera transition where supported.

---

## Phase 17 — AI Computational Assistance

Implement ML components only after the deterministic imaging pipeline works.

---

## Phase 18 — Device Optimization

Profile and tune Qualcomm/Tensor/Exynos/MediaTek devices.

---

## Phase 19 — Production Hardening

Implement:

- crash recovery;
- migrations;
- performance telemetry locally;
- validation;
- accessibility;
- privacy;
- thermal handling;
- battery optimization;
- low-storage behavior.

---

# 76. DEVELOPMENT OUTPUT REQUIRED FROM YOU

For EACH phase, before writing major code:

provide:

### 1. Goal

### 2. Existing architecture affected

### 3. Android APIs required

### 4. Native components required

### 5. Data flow

### 6. Files/modules being added or modified

### 7. Known device limitations

### 8. Implementation

### 9. Unit tests

### 10. Instrumentation tests

### 11. Real-device test procedure

### 12. Expected results

### 13. Failure/recovery behavior

### 14. Performance measurements

### 15. Git commit

Do not proceed on an unverified broken foundation.

---

# 77. CODE QUALITY

Code must be:

- production quality;
- modular;
- testable;
- documented where necessary;
- thread safe;
- lifecycle safe;
- crash resistant;
- memory aware.

Avoid:

- giant classes;
- global mutable state;
- magic camera IDs;
- magic resolutions;
- device-specific hardcoding in core algorithms;
- blocking UI calls;
- uncontrolled coroutines;
- excessive allocations;
- unnecessary image-format conversion.

---

# 78. REAL HARDWARE IS AUTHORITATIVE

Emulators are insufficient for major Camera2 validation.

Create a diagnostics screen capable of exporting:

- device information;
- Camera2 IDs;
- CameraCharacteristics;
- physical-camera groups;
- RAW formats;
- stream sizes;
- FPS ranges;
- hardware level;
- capabilities;
- failed probes;
- concurrent sets;
- lens metadata;
- benchmark results.

These diagnostics will be used to tune real devices.

---

# 79. NO FAKE FEATURES

If the hardware cannot perform something:

say so.

Examples:

If a camera cannot output native 4K:

offer synthetic upscale if desired.

If it cannot output 60 fps:

offer interpolation if desired.

If RAW is unavailable:

disable RAW capture for that lens.

If continuous RAW is too slow:

disable RAW preview/video but retain still RAW if available.

If a hidden auxiliary camera is inaccessible:

do not fabricate it.

If two cameras cannot run concurrently:

perform a controlled switch.

This rule is mandatory.

---

# 80. QUALITY TARGET

The target is to approach flagship computational-camera quality by combining:

- correct sensor capture;
- excellent multi-frame fusion;
- strong RAW alignment;
- effective denoising;
- intelligent HDR;
- accurate color;
- real multi-frame super-resolution;
- reliable autofocus/exposure;
- good lens calibration;
- premium preview;
- smooth lens switching;
- low shutter lag;
- predictable performance.

Use publicly documented principles from:

- Google HDR+;
- HDR+ with bracketing;
- Handheld Multi-Frame Super Resolution;
- MotionCam;
- modern computational-photography literature;
- Android Camera2;
- Adobe DNG;
- publicly available Pixel/iPhone/Samsung imaging research.

Do NOT claim to duplicate proprietary Apple, Google or Samsung algorithms exactly.

Our pipeline should be independently engineered.

---

# 81. PRIORITY ORDER

When engineering tradeoffs occur, prioritize:

1. correctness;
2. camera stability;
3. photographic quality;
4. valid RAW/DNG output;
5. hardware compatibility;
6. memory safety;
7. shutter responsiveness;
8. processing speed;
9. battery;
10. cosmetic features.

Do not sacrifice RAW integrity merely to make a benchmark faster.

---

# 82. INITIAL TASK

Do NOT start by implementing HDR, AI, video, portrait, or super-resolution.

First:

1. inspect the entire attached research;
2. inspect the existing repository if one exists;
3. describe the final proposed architecture;
4. create the module dependency graph;
5. create the Camera2 device-discovery strategy;
6. create the capability/probing model;
7. create the core camera state machine;
8. define native-processing boundaries;
9. define DNG strategy;
10. define the test strategy;
11. identify research assumptions that are impossible or device dependent;
12. establish the development phases;
13. then implement **Phase 0 / Phase 1 only**.

Do not destroy working functionality from previous phases.

If the repository already contains working camera functionality:

inspect it first and modify it incrementally instead of replacing the entire project.

---

# 83. REQUIRED RESPONSE BEFORE CODING

Before changing code, return:

## Architecture Summary

## Repository Assessment

## Research Findings Being Applied

## Assumptions Rejected or Modified

## Camera API Strategy

## RAW/DNG Strategy

## Native Processing Strategy

## Module Graph

## State Machines

## Phase Roadmap

## Phase 0/1 Implementation Plan

Then begin implementation.

The end goal is not merely a camera application that takes photographs.

The end goal is a **universal Android computational RAW camera platform** with a custom imaging pipeline capable of progressively approaching premium flagship computational photography while remaining technically honest about Android hardware and Camera HAL limitations.