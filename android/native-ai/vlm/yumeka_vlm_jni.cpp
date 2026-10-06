// Ponte JNI: Kotlin (NativeVision) -> llama.cpp + libmtmd (Moondream2)
// Biblioteca gerada: libyumeka_vlm.so (arm64-v8a)
//
// A resposta retornada e sempre o texto gerado pelo modelo; nada e simulado.

#include "../common/yumeka_jni_common.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#include <atomic>
#include <mutex>
#include <new>
#include <string>
#include <vector>

using namespace yumeka;

namespace {

struct VlmHandle {
    llama_model* model = nullptr;
    llama_context* lctx = nullptr;
    mtmd_context* mctx = nullptr;
    std::atomic<bool> cancelled{false};
    std::mutex askMutex;
    int nBatch = 512;
};

std::mutex g_logMutex;
std::string g_lastError;

void logCb(enum ggml_log_level level, const char* text, void*) {
    if (!text) return;
    if (level == GGML_LOG_LEVEL_ERROR) {
        YLOGE("llama: %s", text);
        std::lock_guard<std::mutex> lk(g_logMutex);
        g_lastError = text;
        while (!g_lastError.empty() && (g_lastError.back() == '\n' || g_lastError.back() == '\r')) g_lastError.pop_back();
    } else if (level == GGML_LOG_LEVEL_WARN) {
        YLOGW("llama: %s", text);
    }
}

std::string takeLastError() {
    std::lock_guard<std::mutex> lk(g_logMutex);
    std::string e = g_lastError;
    g_lastError.clear();
    return e;
}

std::once_flag g_initOnce;
void globalInit() {
    std::call_once(g_initOnce, [] {
        llama_log_set(logCb, nullptr);
        mtmd_log_set(logCb, nullptr);
        mtmd_helper_log_set(logCb, nullptr);
        llama_backend_init();
    });
}

bool abortCb(void* data) {
    auto* h = static_cast<VlmHandle*>(data);
    return h && h->cancelled.load();
}

void destroy(VlmHandle* h) {
    if (!h) return;
    if (h->mctx) mtmd_free(h->mctx);
    if (h->lctx) llama_free(h->lctx);
    if (h->model) llama_model_free(h->model);
    delete h;
}

VlmHandle* fromJ(jlong h) { return reinterpret_cast<VlmHandle*>(h); }

std::string suffix(const std::string& why) { return why.empty() ? std::string(".") : ": " + why; }

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_yumeka_anime_engine_ai_NativeVision_nativeSystemInfo(JNIEnv* env, jclass) {
    globalInit();
    std::string info = std::string("llama.cpp + mtmd\n") + llama_print_system_info();
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_yumeka_anime_engine_ai_NativeVision_nativeLoad(JNIEnv* env, jclass, jstring jText, jstring jProj, jint threads) {
    globalInit();
    const std::string textPath = jstr(env, jText);
    const std::string projPath = jstr(env, jProj);
    if (!fileReadable(textPath)) { throwAi(env, ERR_MODEL, "Modelo de texto do Moondream2 nao encontrado: " + textPath); return 0; }
    if (!fileReadable(projPath)) { throwAi(env, ERR_MODEL, "Projetor de visao (mmproj) do Moondream2 nao encontrado: " + projPath); return 0; }
    takeLastError();

    auto* h = new (std::nothrow) VlmHandle();
    if (!h) { throwAi(env, ERR_OUT_OF_MEMORY, "Memoria insuficiente para iniciar o motor."); return 0; }
    const int nThreads = threads > 0 ? threads : 4;
    try {
        llama_model_params mp = llama_model_default_params();
        mp.n_gpu_layers = 0; // CPU (nenhum backend de GPU compilado)
        mp.use_mmap = true;
        h->model = llama_model_load_from_file(textPath.c_str(), mp);
        if (!h->model) {
            std::string why = takeLastError();
            destroy(h);
            throwAi(env, ERR_MODEL, "O llama.cpp nao conseguiu carregar o modelo de texto" + suffix(why));
            return 0;
        }

        llama_context_params cp = llama_context_default_params();
        cp.n_ctx = 2048;
        cp.n_batch = h->nBatch;
        cp.n_threads = nThreads;
        cp.n_threads_batch = nThreads;
        h->lctx = llama_init_from_model(h->model, cp);
        if (!h->lctx) {
            std::string why = takeLastError();
            destroy(h);
            throwAi(env, ERR_OUT_OF_MEMORY, "Nao foi possivel criar o contexto do modelo (memoria insuficiente?)" + suffix(why));
            return 0;
        }
        llama_set_abort_callback(h->lctx, abortCb, h);

        mtmd_context_params mcp = mtmd_context_params_default();
        mcp.use_gpu = false;
        mcp.n_threads = nThreads;
        mcp.print_timings = false;
        mcp.warmup = false;
        h->mctx = mtmd_init_from_file(projPath.c_str(), h->model, mcp);
        if (!h->mctx) {
            std::string why = takeLastError();
            destroy(h);
            throwAi(env, ERR_MODEL, "Falha ao carregar o projetor de visao (mmproj)" + suffix(why));
            return 0;
        }
        if (!mtmd_support_vision(h->mctx)) {
            destroy(h);
            throwAi(env, ERR_MODEL, "O arquivo mmproj carregado nao oferece suporte a imagens.");
            return 0;
        }
    } catch (const std::bad_alloc&) {
        destroy(h);
        throwAi(env, ERR_OUT_OF_MEMORY, "Memoria insuficiente ao carregar o Moondream2. Feche outros apps e tente novamente.");
        return 0;
    } catch (const std::exception& e) {
        destroy(h);
        throwAi(env, ERR_RUNTIME, std::string("Erro do motor ao carregar o Moondream2: ") + e.what());
        return 0;
    }
    YLOGI("vlm: Moondream2 carregado");
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT jstring JNICALL
Java_com_yumeka_anime_engine_ai_NativeVision_nativeAsk(JNIEnv* env, jclass, jlong jh, jintArray jPixels, jint width, jint height,
                                                       jstring jQuestion, jint maxTokens) {
    VlmHandle* h = fromJ(jh);
    if (!h || !h->lctx || !h->mctx) { throwAi(env, ERR_INVALID_ARG, "Moondream2 nao carregado."); return nullptr; }
    if (!jPixels || width <= 0 || height <= 0 || env->GetArrayLength(jPixels) != width * height) {
        throwAi(env, ERR_INVALID_ARG, "Imagem invalida enviada para analise.");
        return nullptr;
    }
    std::lock_guard<std::mutex> lk(h->askMutex);
    h->cancelled.store(false);
    takeLastError();

    // ARGB (Android) -> RGB (mtmd)
    const size_t count = (size_t) width * height;
    std::vector<unsigned char> rgb;
    {
        std::vector<jint> argb;
        try { argb.resize(count); rgb.resize(count * 3); } catch (const std::bad_alloc&) {
            throwAi(env, ERR_OUT_OF_MEMORY, "Memoria insuficiente para preparar a imagem.");
            return nullptr;
        }
        env->GetIntArrayRegion(jPixels, 0, (jsize) count, argb.data());
        for (size_t i = 0; i < count; ++i) {
            uint32_t c = (uint32_t) argb[i];
            rgb[i * 3 + 0] = (c >> 16) & 0xFF;
            rgb[i * 3 + 1] = (c >> 8) & 0xFF;
            rgb[i * 3 + 2] = c & 0xFF;
        }
    }

    const std::string question = jstr(env, jQuestion);
    // Formato de prompt do Moondream2 (vicuna-like) usado pelo llama.cpp/mtmd.
    const std::string prompt = std::string(mtmd_default_marker()) + "\n\nQuestion: " + question + "\n\nAnswer:";

    mtmd_bitmap* bmp = mtmd_bitmap_init((uint32_t) width, (uint32_t) height, rgb.data());
    if (!bmp) { throwAi(env, ERR_OUT_OF_MEMORY, "Falha ao criar o bitmap para o modelo."); return nullptr; }
    mtmd_input_chunks* chunks = mtmd_input_chunks_init();

    llama_sampler* smpl = nullptr;
    std::string answer;
    int err = 0;
    std::string errMsg;
    try {
        mtmd_input_text txt{prompt.c_str(), prompt.size(), true, true};
        const mtmd_bitmap* bitmaps[] = {bmp};
        int32_t rc = mtmd_tokenize(h->mctx, chunks, &txt, bitmaps, 1);
        if (rc != 0) {
            err = ERR_RUNTIME; errMsg = "Falha ao preparar imagem/prompt para o Moondream2 (codigo " + std::to_string(rc) + ")";
        } else {
            llama_memory_clear(llama_get_memory(h->lctx), true);
            llama_pos nPast = 0;
            rc = mtmd_helper_eval_chunks(h->mctx, h->lctx, chunks, 0, 0, h->nBatch, true, &nPast);
            if (h->cancelled.load()) { err = ERR_CANCELLED; errMsg = "Analise cancelada."; }
            else if (rc != 0) { err = ERR_RUNTIME; errMsg = "Falha ao processar a imagem no Moondream2" + suffix(takeLastError()); }
            else {
                const llama_vocab* vocab = llama_model_get_vocab(h->model);
                smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
                llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
                const int limit = maxTokens > 0 ? maxTokens : 256;
                char piece[256];
                for (int i = 0; i < limit; ++i) {
                    if (h->cancelled.load()) { err = ERR_CANCELLED; errMsg = "Analise cancelada."; break; }
                    llama_token tok = llama_sampler_sample(smpl, h->lctx, -1);
                    if (llama_vocab_is_eog(vocab, tok)) break;
                    int n = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, false);
                    if (n > 0) answer.append(piece, n);
                    // Moondream costuma iniciar outra pergunta; para no primeiro marcador.
                    if (answer.find("\n\nQuestion:") != std::string::npos) { answer.resize(answer.find("\n\nQuestion:")); break; }
                    int32_t dr = llama_decode(h->lctx, llama_batch_get_one(&tok, 1));
                    if (dr == 2 || h->cancelled.load()) { err = ERR_CANCELLED; errMsg = "Analise cancelada."; break; }
                    if (dr != 0) { err = ERR_RUNTIME; errMsg = "Falha ao gerar texto (llama_decode=" + std::to_string(dr) + ")" + suffix(takeLastError()); break; }
                }
            }
        }
    } catch (const std::bad_alloc&) {
        err = ERR_OUT_OF_MEMORY; errMsg = "Memoria insuficiente durante a analise.";
    } catch (const std::exception& e) {
        err = ERR_RUNTIME; errMsg = std::string("Erro do motor durante a analise: ") + e.what();
    }
    if (smpl) llama_sampler_free(smpl);
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(bmp);

    if (err) { throwAi(env, err, errMsg); return nullptr; }
    return env->NewStringUTF(answer.c_str());
}

JNIEXPORT void JNICALL
Java_com_yumeka_anime_engine_ai_NativeVision_nativeCancel(JNIEnv*, jclass, jlong jh) {
    VlmHandle* h = fromJ(jh);
    if (h) h->cancelled.store(true);
}

JNIEXPORT void JNICALL
Java_com_yumeka_anime_engine_ai_NativeVision_nativeFree(JNIEnv*, jclass, jlong jh) {
    VlmHandle* h = fromJ(jh);
    if (!h) return;
    { std::lock_guard<std::mutex> lk(h->askMutex); }
    destroy(h);
    YLOGI("vlm: Moondream2 descarregado");
}

} // extern "C"
