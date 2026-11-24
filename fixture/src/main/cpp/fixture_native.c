#include <jni.h>

#if defined(__aarch64__)
#define FIXTURE_ABI "arm64-v8a"
#elif defined(__arm__)
#define FIXTURE_ABI "armeabi-v7a"
#elif defined(__x86_64__)
#define FIXTURE_ABI "x86_64"
#elif defined(__i386__)
#define FIXTURE_ABI "x86"
#else
#define FIXTURE_ABI "unknown"
#endif

JNIEXPORT jstring JNICALL
Java_com_clonapps_fixture_NativeBridge_hello(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, "native OK (" FIXTURE_ABI ")");
}
