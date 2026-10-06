# Motores de IA nativos do YASE

```text
Kotlin (AiSession / NativeDiffusion / NativeVision)   app/src/main/kotlin/.../ai/LocalInference.kt
   |  JNI (NativeAiException para erros reais)
C++  sd/yumeka_sd_jni.cpp      vlm/yumeka_vlm_jni.cpp
   |                              |
stable-diffusion.cpp           llama.cpp + libmtmd
   |                              |
DreamShaper XL GGUF            Moondream2 GGUF (texto + mmproj)
```

- `libyumeka_sd.so` e `libyumeka_vlm.so`: cada uma contem seu proprio ggml estatico e exporta apenas
  simbolos `Java_*` (exports.map), evitando conflito entre as duas copias do ggml.
- Arquitetura: `arm64-v8a`, API 26+, backend CPU (NEON/dotprod/fp16). Vulkan/NPU nao habilitados.
- Versoes oficiais fixadas em `versions.env` (tag + commit conferido). Nenhum binario pronto e baixado.

## Compilar localmente
```bash
android/native-ai/scripts/fetch-sources.sh
ANDROID_NDK=/caminho/ndk android/native-ai/scripts/build-android.sh
# gera android/app/src/main/jniLibs/arm64-v8a/*.so, empacotadas pelo Gradle
```

## Testes
- JVM: `gradle :app:testDebugUnitTest`
- Aparelho (real): instale os modelos pelo app e rode `gradle :app:connectedDebugAndroidTest`.

## Atualizar motores
Troque tag e commit em `versions.env`; se a API C mudar, ajuste apenas os arquivos `*_jni.cpp`.
