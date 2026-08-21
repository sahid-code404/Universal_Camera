#include <jni.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraMetadata.h>
#include <camera/NdkCameraMetadataTags.h>
#include <media/NdkImage.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <memory>
#include <set>
#include <sstream>
#include <string>
#include <vector>

namespace {
constexpr const char* TAG = "AuxCameraDiscovery";

struct SizePair {
    int width = 0;
    int height = 0;
    int format = 0;
    long long area() const { return static_cast<long long>(width) * height; }
};

bool getEntry(const ACameraMetadata* metadata, uint32_t tag, ACameraMetadata_const_entry* entry) {
    return metadata != nullptr && ACameraMetadata_getConstEntry(metadata, tag, entry) == ACAMERA_OK;
}

std::vector<SizePair> streamSizes(const ACameraMetadata* metadata, int wantedFormat) {
    std::vector<SizePair> result;
    ACameraMetadata_const_entry entry{};
    if (!getEntry(metadata, ACAMERA_SCALER_AVAILABLE_STREAM_CONFIGURATIONS, &entry)) return result;
    for (uint32_t i = 0; i + 3 < entry.count; i += 4) {
        const int format = entry.data.i32[i];
        const int width = entry.data.i32[i + 1];
        const int height = entry.data.i32[i + 2];
        const int input = entry.data.i32[i + 3];
        if (input == 0 && format == wantedFormat && width > 0 && height > 0) {
            result.push_back({width, height, format});
        }
    }
    std::sort(result.begin(), result.end(), [](const SizePair& a, const SizePair& b) {
        return a.area() > b.area();
    });
    return result;
}

SizePair chooseRaw(const ACameraMetadata* metadata) {
    // Keep the reference Camera project's policy: RAW10/RAW12/RAW16 are genuine Bayer outputs.
    for (const int format : {AIMAGE_FORMAT_RAW10, AIMAGE_FORMAT_RAW16, AIMAGE_FORMAT_RAW12}) {
        const auto sizes = streamSizes(metadata, format);
        if (!sizes.empty()) return sizes.front();
    }
    return {};
}

SizePair choosePreview(const ACameraMetadata* metadata, const SizePair& raw) {
    auto previews = streamSizes(metadata, AIMAGE_FORMAT_PRIVATE);
    if (previews.empty()) previews = streamSizes(metadata, AIMAGE_FORMAT_YUV_420_888);
    if (previews.empty()) return {};
    const double rawRatio = raw.height > 0 ? static_cast<double>(raw.width) / raw.height : 4.0 / 3.0;
    SizePair best{};
    long long bestArea = 0;
    for (const auto& size : previews) {
        if (size.area() > 2560LL * 1440LL) continue;
        const double ratio = static_cast<double>(size.width) / size.height;
        if (std::abs(ratio - rawRatio) > 0.08) continue;
        if (size.area() > bestArea) {
            best = size;
            bestArea = size.area();
        }
    }
    if (bestArea == 0) {
        for (const auto& size : previews) {
            if (size.area() <= 2560LL * 1440LL && size.area() > bestArea) {
                best = size;
                bestArea = size.area();
            }
        }
    }
    return bestArea > 0 ? best : previews.front();
}

bool isUsableRawCamera(ACameraManager* manager, const std::string& cameraId) {
    ACameraMetadata* rawMetadata = nullptr;
    if (ACameraManager_getCameraCharacteristics(manager, cameraId.c_str(), &rawMetadata) != ACAMERA_OK ||
        rawMetadata == nullptr) {
        return false;
    }
    std::unique_ptr<ACameraMetadata, decltype(&ACameraMetadata_free)> metadata(rawMetadata, ACameraMetadata_free);

    ACameraMetadata_const_entry facing{};
    if (!getEntry(metadata.get(), ACAMERA_LENS_FACING, &facing) || facing.count == 0) return false;

    const SizePair raw = chooseRaw(metadata.get());
    if (raw.width <= 0 || raw.height <= 0) return false;
    const SizePair preview = choosePreview(metadata.get(), raw);
    return preview.width > 0 && preview.height > 0;
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
        // Exact bounded metadata-only strategy from the reference branch. This happens only after
        // first preview, never on the critical startup path, and never opens candidate cameras.
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
        if (!isUsableRawCamera(manager.get(), id)) continue;
        if (!first) out << ',';
        first = false;
        ++retained;
        out << '"' << id << '"';
    }
    out << ']';

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "NDK discovery advertised=%d candidates=%zu deep=%d retainedRAW=%d",
        idList->numCameras,
        candidates.size(),
        deepScan ? 1 : 0,
        retained);
    return out.str();
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_sahidcode404_camera_camera_discovery_NativeAuxCameraScanner_nativeEnumerateIdsJson(
    JNIEnv* env,
    jobject,
    jboolean deepScan
) {
    const std::string json = enumerateIdsJson(deepScan == JNI_TRUE);
    return env->NewStringUTF(json.c_str());
}
