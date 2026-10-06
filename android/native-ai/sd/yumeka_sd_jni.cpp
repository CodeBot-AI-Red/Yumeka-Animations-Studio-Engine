// Ponte JNI: Kotlin (NativeDiffusion) -> stable-diffusion.cpp
// Biblioteca gerada: libyumeka_sd.so (arm64-v8a)
//
// Nada aqui simula resultados: toda imagem retornada vem de generate_image().
// Erros sao lancados como NativeAiException(code, message) com a causa real.

#include "../common/yumeka_jni_common.h"
#include "stable-diffusion.h"

#include <atomic>
#include <cstdlib>
#include <mutex>
#include <new>
#include <string>
#include <vector>

using namespace yumeka;

namespace {

struct SdHandle {
    sd_ctx_t* ctx = nullptr;
    std::atomic<bool> cancelled{false};
    std::mutex genMutex; // uma geracao por vez por handle
};

std::mutex g_logMutex;
std::string g_lastError;

void logCb(enum sd_log_level_t level, const char* text, void*) {
    if (!text) return;
    switch (level) {
        case SD_LOG_ERROR: {
            YLOGE("sd: %s", text);
            std::lock_guard<std::mutex> lk(g_logMutex);
            g_lastError = text;
            while (!g_lastError.empty() && (g_lastError.back() == '\n' || g_lastError.back() == '\r')) g_lastError.pop_back();
            break;
        }
        case SD_LOG_WARN: YLOGW("sd: %s", text); break;
        case SD_LOG_INFO: YLOGI("sd: %s", text); break;
        default: break;
    }
}

std::string takeLastError() {
    std::lock_guard<std::mutex> lk(g_logMutex);
    std::string e = g_lastError;
    g_lastError.clear();
    return e;
}

// Progresso: o callback do sd.cpp e global e chamado na mesma thread de generate_image().
struct ProgressCtx {
    JNIEnv* env;
    jobject cb;
    jmethodID onProgress;
};
thread_local ProgressCtx* t_progress = nullptr;

void progressCb(int step, int steps, float, void*) {
    ProgressCtx* p = t_progress;
    if (!p || !p->cb || !p->onProgress) return;
    p->env->CallVoidMethod(p->cb, p->onProgress, (jint) step, (jint) steps);
    if (p->env->ExceptionCheck()) p->env->ExceptionClear();
}

std::once_flag g_initOnce;
void globalInit() {
    std::call_once(g_initOnce, [] {
        sd_set_log_callback(logCb, nullptr);
        sd_set_progress_callback(progressCb, nullptr);
    });
}

SdHandle* fromJ(jlong h) { return reinterpret_cast<SdHandle*>(h); }

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_yumeka_anime_engine_ai_NativeDiffusion_nativeSystemInfo(JNIEnv* env, jclass) {
    globalInit();
    std::string info = std::string("stable-diffusion.cpp ") + sd_version() + " (" + sd_commit() + ")\n" + sd_get_system_info();
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_yumeka_anime_engine_ai_NativeDiffusion_nativeLoad(JNIEnv* env, jclass, jstring jModel, jint threads, jboolean /*preferGpu*/) {
    globalInit();
    const std::string model = jstr(env, jModel);
    if (model.empty() || !fileReadable(model)) {
        throwAi(env, ERR_MODEL, "Arquivo do modelo nao encontrado ou sem permissao de leitura: " + model);
        return 0;
    }
    takeLastError();
    auto* h = new (std::nothrow) SdHandle();
    if (!h) { throwAi(env, ERR_OUT_OF_MEMORY, "Memoria insuficiente para iniciar o motor."); return 0; }
    try {
        sd_ctx_params_t p;
        sd_ctx_params_init(&p);
        p.model_path = model.c_str();
        p.n_threads = threads > 0 ? threads : sd_get_num_physical_cores();
        p.enable_mmap = true;     // pesos mapeados do arquivo: menos pressao de RAM
        p.flash_attn = true;      // reduz memoria de atencao na CPU
        p.diffusion_flash_attn = true;
        h->ctx = new_sd_ctx(&p);
    } catch (const std::bad_alloc&) {
        delete h;
        throwAi(env, ERR_OUT_OF_MEMORY, "Memoria insuficiente ao carregar o modelo. Feche outros apps e tente novamente.");
        return 0;
    } catch (const std::exception& e) {
        delete h;
        throwAi(env, ERR_RUNTIME, std::string("Erro do motor ao carregar o modelo: ") + e.what());
        return 0;
    }
    if (!h->ctx) {
        delete h;
        std::string why = takeLastError();
        throwAi(env, ERR_MODEL, "O stable-diffusion.cpp nao conseguiu carregar o modelo" + (why.empty() ? std::string(".") : ": " + why));
        return 0;
    }
    if (!sd_ctx_supports_image_generation(h->ctx)) {
        free_sd_ctx(h->ctx);
        delete h;
        throwAi(env, ERR_MODEL, "O arquivo carregado nao e um modelo de geracao de imagens suportado.");
        return 0;
    }
    YLOGI("sd: modelo carregado (%s)", sd_get_model_version_name(h->ctx));
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT jintArray JNICALL
Java_com_yumeka_anime_engine_ai_NativeDiffusion_nativeGenerate(JNIEnv* env, jclass, jlong jh, jstring jPrompt, jstring jNeg,
                                                               jint width, jint height, jint steps, jfloat guidance, jlong seed,
                                                               jobject progress) {
    SdHandle* h = fromJ(jh);
    if (!h || !h->ctx) { throwAi(env, ERR_INVALID_ARG, "Motor de geracao nao carregado."); return nullptr; }
    if (width < 64 || height < 64 || width % 64 != 0 || height % 64 != 0 || width > 1024 || height > 1024) {
        throwAi(env, ERR_INVALID_ARG, "Resolucao invalida: use multiplos de 64 entre 64 e 1024.");
        return nullptr;
    }
    if (steps < 1 || steps > 50) { throwAi(env, ERR_INVALID_ARG, "Numero de passos invalido (1-50)."); return nullptr; }

    std::lock_guard<std::mutex> lk(h->genMutex);
    const std::string prompt = jstr(env, jPrompt);
    const std::string neg = jstr(env, jNeg);

    ProgressCtx pctx{env, progress, nullptr};
    if (progress) {
        jclass pc = env->GetObjectClass(progress);
        pctx.onProgress = env->GetMethodID(pc, "onProgress", "(II)V");
    }
    t_progress = &pctx;

    h->cancelled.store(false);
    sd_cancel_generation(h->ctx, SD_CANCEL_RESET);
    takeLastError();

    sd_image_t* images = nullptr;
    int n = 0;
    bool ok = false;
    try {
        sd_img_gen_params_t g;
        sd_img_gen_params_init(&g);
        g.prompt = prompt.c_str();
        g.negative_prompt = neg.c_str();
        g.width = width;
        g.height = height;
        g.seed = seed;
        g.batch_count = 1;
        g.sample_params.sample_method = EULER_SAMPLE_METHOD;
        g.sample_params.scheduler = sd_get_default_scheduler(h->ctx, EULER_SAMPLE_METHOD);
        g.sample_params.sample_steps = steps;
        g.sample_params.guidance.txt_cfg = guidance;
        g.vae_tiling_params.enabled = true; // decodificacao em blocos: menos pico de RAM
        ok = generate_image(h->ctx, &g, &images, &n);
    } catch (const std::bad_alloc&) {
        t_progress = nullptr;
        if (images) free_sd_images(images, n);
        throwAi(env, ERR_OUT_OF_MEMORY, "Memoria insuficiente durante a geracao. Tente 256x256 e feche outros apps.");
        return nullptr;
    } catch (const std::exception& e) {
        t_progress = nullptr;
        if (images) free_sd_images(images, n);
        throwAi(env, ERR_RUNTIME, std::string("Erro do motor durante a geracao: ") + e.what());
        return nullptr;
    }
    t_progress = nullptr;

    if (h->cancelled.load()) {
        if (images) free_sd_images(images, n);
        throwAi(env, ERR_CANCELLED, "Geracao cancelada.");
        return nullptr;
    }
    if (!ok || !images || n < 1 || !images[0].data) {
        if (images) free_sd_images(images, n);
        std::string why = takeLastError();
        throwAi(env, ERR_RUNTIME, "A geracao falhou" + (why.empty() ? std::string(".") : ": " + why));
        return nullptr;
    }

    const sd_image_t& img = images[0];
    if ((int) img.width != width || (int) img.height != height || img.channel < 3) {
        free_sd_images(images, n);
        throwAi(env, ERR_RUNTIME, "O motor retornou uma imagem com formato inesperado.");
        return nullptr;
    }
    const size_t count = (size_t) width * height;
    std::vector<jint> argb;
    try { argb.resize(count); } catch (const std::bad_alloc&) {
        free_sd_images(images, n);
        throwAi(env, ERR_OUT_OF_MEMORY, "Memoria insuficiente para converter a imagem.");
        return nullptr;
    }
    const int ch = (int) img.channel;
    for (size_t i = 0; i < count; ++i) {
        const uint8_t* px = img.data + i * ch;
        argb[i] = (jint) (0xFF000000u | ((uint32_t) px[0] << 16) | ((uint32_t) px[1] << 8) | (uint32_t) px[2]);
    }
    free_sd_images(images, n);

    jintArray out = env->NewIntArray((jsize) count);
    if (!out) return nullptr; // OutOfMemoryError ja pendente na JVM
    env->SetIntArrayRegion(out, 0, (jsize) count, argb.data());
    return out;
}

JNIEXPORT void JNICALL
Java_com_yumeka_anime_engine_ai_NativeDiffusion_nativeCancel(JNIEnv*, jclass, jlong jh) {
    SdHandle* h = fromJ(jh);
    if (!h || !h->ctx) return;
    h->cancelled.store(true);
    sd_cancel_generation(h->ctx, SD_CANCEL_ALL);
}

JNIEXPORT void JNICALL
Java_com_yumeka_anime_engine_ai_NativeDiffusion_nativeFree(JNIEnv*, jclass, jlong jh) {
    SdHandle* h = fromJ(jh);
    if (!h) return;
    {
        std::lock_guard<std::mutex> lk(h->genMutex); // espera a geracao em curso terminar
        if (h->ctx) free_sd_ctx(h->ctx);
        h->ctx = nullptr;
    }
    delete h;
    YLOGI("sd: modelo descarregado");
}

} // extern "C"
