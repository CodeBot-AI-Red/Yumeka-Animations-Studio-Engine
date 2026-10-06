# Motores de IA locais (integracao nativa)

O app ja inclui: download/instalacao/exclusao dos modelos, verificacao de compatibilidade,
painel de geracao, analise, galeria e insercao na linha do tempo. A execucao em si precisa
de duas bibliotecas nativas que **ainda nao estao empacotadas**. Sem elas o app mostra
claramente que o recurso nao e compativel e nunca simula resultados.

| Biblioteca          | Motor                                              | Modelo                                            |
|---------------------|----------------------------------------------------|---------------------------------------------------|
| `libyumeka_sd.so`   | [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) (Vulkan/CPU) | `offgrid-ai/dreamshaper-xl-v2-turbo-GGUF` (Q4_K, 2,6 GB) |
| `libyumeka_vlm.so`  | [llama.cpp](https://github.com/ggml-org/llama.cpp) `mtmd`                           | `ggml-org/moondream2-20250414-GGUF` (texto + mmproj, 3,5 GB) |

## Funcoes JNI esperadas

```
// com.yumeka.anime.engine.ai.NativeDiffusion
jlong     nativeLoad(String modelPath, int threads, boolean preferGpu)
jintArray nativeGenerate(long h, String prompt, String negative, int w, int h, int steps, float cfg, long seed, ProgressCallback cb)  // ARGB
void      nativeFree(long h)

// com.yumeka.anime.engine.ai.NativeVision
jlong   nativeLoad(String textModel, String mmproj, int threads)
jstring nativeAsk(long h, int[] argb, int w, int h, String question, int maxTokens)
void    nativeFree(long h)
```

Recomendado para o Moto Edge 30 Neo (8 GB): Q4_K, sampler `euler`, CFG 1–1.5, 2–4 steps,
256–384 px, `threads = nucleos - 2`, um modelo carregado por vez (o app ja garante isso via `AiSession`).
Observacao: o DreamShaper XL e um modelo SDXL; abaixo de 512 px a qualidade cai bastante.
