// Utilitarios compartilhados pelas pontes JNI dos motores de IA do YASE.
#pragma once

#include <jni.h>
#include <android/log.h>
#include <string>

#define YLOG_TAG "YumekaAI"
#define YLOGI(...) __android_log_print(ANDROID_LOG_INFO, YLOG_TAG, __VA_ARGS__)
#define YLOGW(...) __android_log_print(ANDROID_LOG_WARN, YLOG_TAG, __VA_ARGS__)
#define YLOGE(...) __android_log_print(ANDROID_LOG_ERROR, YLOG_TAG, __VA_ARGS__)

namespace yumeka {

// Codigos de erro espelhados em NativeAiException.kt (mantenha sincronizado).
enum ErrorCode : int {
    ERR_RUNTIME = 1,       // falha interna do motor
    ERR_MODEL = 2,         // arquivo de modelo ausente/corrompido/incompativel
    ERR_OUT_OF_MEMORY = 3, // alocacao falhou
    ERR_CANCELLED = 4,     // cancelado pelo usuario
    ERR_INVALID_ARG = 5,   // parametros invalidos
};

inline void throwAi(JNIEnv* env, int code, const std::string& msg) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass("com/yumeka/anime/engine/ai/NativeAiException");
    if (!cls) return; // ClassNotFoundError ja pendente
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;)V");
    if (!ctor) return;
    jstring jmsg = env->NewStringUTF(msg.c_str());
    auto ex = static_cast<jthrowable>(env->NewObject(cls, ctor, (jint) code, jmsg));
    if (ex) env->Throw(ex);
    YLOGE("[%d] %s", code, msg.c_str());
}

inline std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

inline bool fileReadable(const std::string& path) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return false;
    fclose(f);
    return true;
}

} // namespace yumeka
