
# Motores de IA nativos: classes/metodos acessados pelo JNI nao podem ser renomeados.
-keep class com.yumeka.anime.engine.ai.NativeAiException { <init>(int, java.lang.String); }
-keep interface com.yumeka.anime.engine.ai.ProgressCallback { void onProgress(int, int); }
-keepclasseswithmembernames class com.yumeka.anime.engine.ai.** { native <methods>; }
