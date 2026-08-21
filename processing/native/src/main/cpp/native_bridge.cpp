#include <jni.h>
#include <string>

#if defined(__aarch64__) || defined(__ARM_NEON)
static constexpr bool kHasNeon = true;
#else
static constexpr bool kHasNeon = false;
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_com_sahidcode404_camera_processing_nativebridge_NativeProcessingBridge_nativeVersion(
        JNIEnv* env, jobject) {
    return env->NewStringUTF("camera-processing-foundation/0.1");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sahidcode404_camera_processing_nativebridge_NativeProcessingBridge_hasNeon(
        JNIEnv*, jobject) {
    return kHasNeon ? JNI_TRUE : JNI_FALSE;
}
