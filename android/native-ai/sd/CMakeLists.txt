# libyumeka_sd.so = ponte JNI + stable-diffusion.cpp (com seu ggml) em uma unica .so.
cmake_minimum_required(VERSION 3.22)
project(yumeka_sd C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(SD_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/stable-diffusion.cpp" CACHE PATH "stable-diffusion.cpp source")
if (NOT EXISTS "${SD_SOURCE_DIR}/CMakeLists.txt" OR NOT EXISTS "${SD_SOURCE_DIR}/ggml/CMakeLists.txt")
    message(FATAL_ERROR "Codigo do stable-diffusion.cpp ausente em ${SD_SOURCE_DIR}. Rode android/native-ai/scripts/fetch-sources.sh")
endif()

# Somente a biblioteca, estatica, backend CPU (NEON/dotprod do ARMv8.2 detectados pelo ggml).
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_WEBP OFF CACHE BOOL "" FORCE)
set(SD_WEBM OFF CACHE BOOL "" FORCE)
set(SD_VULKAN OFF CACHE BOOL "" FORCE)
set(SD_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(${SD_SOURCE_DIR} sd-build EXCLUDE_FROM_ALL)

add_library(yumeka_sd SHARED yumeka_sd_jni.cpp)
target_link_libraries(yumeka_sd PRIVATE stable-diffusion log android)
# Exporta apenas os simbolos JNI: evita conflito entre os dois ggml (sd e llama) no mesmo processo.
target_link_options(yumeka_sd PRIVATE
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/../common/exports.map"
    "-Wl,--exclude-libs,ALL"
    "-Wl,--gc-sections"
    "-Wl,-z,max-page-size=16384")
