cmake_minimum_required(VERSION 3.5.0)
project(nexio_entry)

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

# 项目根（cpp → main → src → entry → harmonyApp → NexioSchedule）
set(NEXIO_ROOT ${NATIVERENDER_ROOT_PATH}/../../../../..)

# Kotlin/Native 编出来的产物：Compose 运行时 + 我们自己的 UI 都在这一个 .so 里。
# 路径相对 cpp/ 往上三层 = entry/libs，与 HarmonyOS 约定的 jniLibs 布局一致。
add_library(nexioohos SHARED IMPORTED)
set_target_properties(nexioohos PROPERTIES
    IMPORTED_LOCATION ${NATIVERENDER_ROOT_PATH}/../../../libs/arm64-v8a/libnexioohos.so)

add_library(entry SHARED napi_init.cpp ai_crypto.cpp)

# Compose 的 XComponent 桥头文件：`XComponentHolder::InitXComponent` 必须由本模块调用，
# 否则系统建出来的 XComponent 拿不到 surface 回调（表现：进程活着、画面全白）。
target_include_directories(entry PRIVATE
    ${NEXIO_ROOT}/ohos/third_party/compose_arkui_utils)

target_link_libraries(entry PUBLIC
    libace_napi.z.so          # napi_*
    libace_ndk.z.so           # OH_NativeXComponent_*
    libhilog_ndk.z.so         # OH_LOG_Print
    libohcrypto.so            # CryptoArchitectureKit（AI 会话加密）
    nexioohos)                # Compose 运行时（含 XComponentHolder 的实现）
