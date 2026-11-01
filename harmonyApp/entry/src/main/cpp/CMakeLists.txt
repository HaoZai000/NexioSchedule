cmake_minimum_required(VERSION 3.5.0)
project(nexio_entry)

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

# Kotlin/Native 编出来的产物：Compose 运行时 + 我们自己的 UI 都在这一个 .so 里。
# 路径相对 cpp/ 往上三层 = entry/libs，与 HarmonyOS 约定的 jniLibs 布局一致。
add_library(nexioohos SHARED IMPORTED)
set_target_properties(nexioohos PROPERTIES
    IMPORTED_LOCATION ${NATIVERENDER_ROOT_PATH}/../../../libs/arm64-v8a/libnexioohos.so)

add_library(entry SHARED napi_init.cpp)

# libace_napi.z.so 提供 napi_*，libhilog_ndk.z.so 提供 OH_LOG_Print；
# 其余系统库由 libnexioohos.so 自己 DT_NEEDED 带进来
target_link_libraries(entry PUBLIC libace_napi.z.so libhilog_ndk.z.so nexioohos)
