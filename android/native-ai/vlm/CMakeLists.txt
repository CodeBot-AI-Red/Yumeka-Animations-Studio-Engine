# libyumeka_vlm.so = ponte JNI + llama.cpp + libmtmd (com seu ggml) em uma unica .so.
cmake_minimum_required(VERSION 3.22)
project(yumeka_vlm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(LLAMA_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/llama.cpp" CACHE PATH "llama.cpp source")
if (NOT EXISTS "${LLAMA_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Codigo do llama.cpp ausente em ${LLAMA_SOURCE_DIR}. Rode android/native-ai/scripts/fetch-sources.sh")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SOURCE_DIR} llama-build EXCLUDE_FROM_ALL)

add_library(yumeka_vlm SHARED yumeka_vlm_jni.cpp)
target_link_libraries(yumeka_vlm PRIVATE mtmd llama ggml log android)
target_link_options(yumeka_vlm PRIVATE
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/../common/exports.map"
    "-Wl,--exclude-libs,ALL"
    "-Wl,--gc-sections"
    "-Wl,-z,max-page-size=16384")
