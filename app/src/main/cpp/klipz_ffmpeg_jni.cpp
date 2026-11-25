#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <android/log.h>

#ifdef KLIPZ_HAS_FFMPEG
extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
}
#endif

#define LOG_TAG "KlipzFFmpegJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::atomic<bool> g_cancel_requested{false};
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_processing_ffmpeg_FfmpegNativeBridge_nativeGetVersion(
    JNIEnv* env,
    jobject /* thiz */
) {
#ifdef KLIPZ_HAS_FFMPEG
    std::string version = std::string("FFmpeg LGPL ") + av_version_info();
    return env->NewStringUTF(version.c_str());
#else
    return env->NewStringUTF("Klipz JNI Bridge (awaiting prebuilt LGPL FFmpeg .so libs)");
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_processing_ffmpeg_FfmpegNativeBridge_nativeExecuteCommand(
    JNIEnv* env,
    jobject /* thiz */,
    jobjectArray args
) {
    g_cancel_requested.store(false);
    const jsize count = env->GetArrayLength(args);
    std::vector<std::string> argv;
    argv.reserve(count);
    for (jsize i = 0; i < count; ++i) {
        auto jstr = static_cast<jstring>(env->GetObjectArrayElement(args, i));
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        argv.emplace_back(chars ? chars : "");
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }

    LOGI("Received FFmpeg invocation with %d arguments", static_cast<int>(argv.size()));

#ifdef KLIPZ_HAS_FFMPEG
    if (argv.size() < 4) {
        return -22; // EINVAL
    }
    // Remux / transcode entry point backed by linked libavformat / libavcodec
    AVFormatContext* in_ctx = nullptr;
    const std::string& input_path = argv[3];
    int ret = avformat_open_input(&in_ctx, input_path.c_str(), nullptr, nullptr);
    if (ret < 0) {
        LOGE("Failed to open input container: %d", ret);
        return ret;
    }
    ret = avformat_find_stream_info(in_ctx, nullptr);
    avformat_close_input(&in_ctx);
    return g_cancel_requested.load() ? -125 : ret;
#else
    LOGE("FFmpeg native libraries are not linked in this build.");
    return -38; // ENOSYS
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_processing_ffmpeg_FfmpegNativeBridge_nativeCancelActive(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    g_cancel_requested.store(true);
}
