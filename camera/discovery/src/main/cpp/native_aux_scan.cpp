#include <jni.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraMetadata.h>
#include <camera/NdkCameraMetadataTags.h>
#include <media/NdkImage.h>
#include <android/log.h>

#include <algorithm>
#include <cstdlib>
#include <memory>
#include <set>
#include <sstream>
#include <string>
#include <vector>

namespace {
constexpr const char* TAG = "AuxCameraDiscovery";

bool getEntry(const ACameraMetadata* metadata, uint32_t tag, ACameraMetadata_const_entry* entry) {
    return metadata != nullptr && ACameraMetadata_getConstEntry(metadata, tag, entry) == ACAMERA_OK;
}

bool hasPhotographicOutput(const ACameraMetadata* metadata) {
    ACameraMetadata_const_entry entry{};
    if (!getEntry(metadata, ACAMERA_SCALER_AVAILABLE_STREAM_CONFIGURATIONS, &entry)) return false;

    bool hasProcessedOutput = false;
    for (uint32_t i = 0; i + 3 < entry.count; i += 4) {
        const int format = entry.data.i32[i];
        const int width = entry.data.i32[i + 1];
        const int height = entry.data.i32[i + 2];
        const int input = entry.data.i32[i + 3];
        if (input != 0 || width <= 0 || height <= 0) continue;
        if (format == AIMAGE_FORMAT_PRIVATE || format == AIMAGE_FORMAT_YUV_420_888) {
            hasProcessedOutput = true;
            break;
        }
    }
    return hasProcessedOutput;
}

bool isPhotographicCamera(ACameraManager* manager, const std::string& cameraId) {
    ACameraMetadata* raw = nullptr;
    if (ACameraManager_getCameraCharacteristics(manager, cameraId.c_str(), &raw) != ACAMERA_OK || raw == nullptr) {
        return false;
    }
    std::unique_ptr<ACameraMetadata, decltype(&ACameraMetadata_free)> metadata(raw, ACameraMetadata_free);

    ACameraMetadata_const_entry facing{};
    if (!getEntry(metadata.get(), ACAMERA_LENS_FACING, &facing) || facing.count == 0) return false;

    return hasPhotographicOutput(metadata.get());
}

std::string enumerateIdsJson(bool deepScan) {
    ACameraManager* rawManager = ACameraManager_create();
    if (rawManager == nullptr) return "[]";
    std::unique_ptr<ACameraManager, decltype(&ACameraManager_delete)> manager(rawManager, ACameraManager_delete);

    ACameraIdList* rawList = nullptr;
    if (ACameraManager_getCameraIdList(manager.get(), &rawList) != ACAMERA_OK || rawList == nullptr) {
        return "[]";
    }
    std::unique_ptr<ACameraIdList, void(*)(ACameraIdList*)> idList(rawList, ACameraManager_deleteCameraIdList);

    std::vector<std::string> candidates;
    std::set<std::string> seen;
    int maxNumeric = -1;

    for (int i = 0; i < idList->numCameras; ++i) {
        const char* id = idList->cameraIds[i];
        if (id == nullptr || *id == '\0') continue;
        const std::string value(id);
        if (seen.insert(value).second) candidates.push_back(value);

        char* end = nullptr;
        const long numeric = std::strtol(value.c_str(), &end, 10);
        if (end != value.c_str() && end != nullptr && *end == '\0' && numeric >= 0 && numeric <= 128) {
            maxNumeric = std::max(maxNumeric, static_cast<int>(numeric));
        }
    }

    if (deepScan) {
        // Same bounded strategy used by the reference Camera project: metadata-only probing of a
        // small numeric namespace catches Qualcomm/vendor auxiliary IDs hidden from advertised lists
        // without hard-coding model-specific camera IDs or opening any device.
        const int upper = std::min(31, std::max(11, maxNumeric + 8));
        for (int id = 0; id <= upper; ++id) {
            const std::string value = std::to_string(id);
            if (seen.insert(value).second) candidates.push_back(value);
        }
    }

    std::ostringstream out;
    out << '[';
    bool first = true;
    int retained = 0;
    for (const auto& id : candidates) {
        if (!isPhotographicCamera(manager.get(), id)) continue;
        if (!first) out << ',';
        first = false;
        ++retained;
        out << '"' << id << '"';
    }
    out << ']';

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "NDK aux scan advertised=%d candidates=%zu deep=%d photographic=%d",
        idList->numCameras,
        candidates.size(),
        deepScan ? 1 : 0,
        retained);

    return out.str();
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_sahidcode404_camera_camera_discovery_NativeAuxCameraScanner_nativeEnumerateIdsJson(
    JNIEnv* env,
    jobject,
    jboolean deepScan
) {
    const std::string json = enumerateIdsJson(deepScan == JNI_TRUE);
    return env->NewStringUTF(json.c_str());
}
