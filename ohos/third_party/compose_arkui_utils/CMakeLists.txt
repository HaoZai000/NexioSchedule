# 这份 CMakeLists 是我们补的：只编上游的 compose_arkui_utils **静态库**。
# 上游原文件（同目录 UPSTREAM.md 指向的位置）还会编 skikobridge 共享库与版本 banner，
# 那两个 HAR 已经带了，重复编会撞名，所以这里只保留静态库部分，其余逐行照抄上游。
# the minimum version of CMake.
cmake_minimum_required(VERSION 3.5.0)
project(compose_arkui_utils)

if(DEFINED INCLUDE_DIRS)
    foreach(include_path ${INCLUDE_DIRS})
        include_directories(${include_path})
    endforeach()
endif()

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

if(DEFINED PACKAGE_FIND_FILE)
    include(${PACKAGE_FIND_FILE})
endif()

add_definitions(-DDEBUG) # Add DEBUG definition
add_definitions(-std=c++17)

include_directories(${NATIVERENDER_ROOT_PATH}
                    ${NATIVERENDER_ROOT_PATH}/include)

# arkui_view_controller_wrapper.cpp 会 include "skikobridge/ComposeDispatcher.h"：
# 只引头文件即可，skikobridge 的 .cpp 不参与本静态库（共享库由 HAR 提供）。
set(SKIKOBRIDGE_ROOT ${NATIVERENDER_ROOT_PATH}/skikobridge)
include_directories(${SKIKOBRIDGE_ROOT} ${SKIKOBRIDGE_ROOT}/include
                    ${SKIKOBRIDGE_ROOT}/include/skikobridge ${SKIKOBRIDGE_ROOT}/src)

file(
GLOB_RECURSE SOURCES
${NATIVERENDER_ROOT_PATH}/compose/*
)

find_library(
    # Sets the name of the path variable.
    EGL-lib
    EGL
)

find_library(
    # Sets the name of the path variable.
    GLES-lib
    GLESv3
)

find_library(
    # Sets the name of the path variable.
    libace-lib
    ace_ndk.z
)

find_library(
    # Sets the name of the path variable.
    hilog-lib
    hilog_ndk.z
)

find_library(
    # Sets the name of the path variable.
    libnapi-lib
    ace_napi.z
)

find_library(
    # Sets the name of the path variable.
    libuv-lib
    uv
)

find_library(libnative_vsync-lib native_vsync)
find_library(libhitrace-lib hitrace_ndk.z)
find_library(libqos-lib qos)

find_library(OHOS_IMAGE_SOURCE_LIB image_source)
# links pixelmap (GetNativeBuffer); fall back to pixelmap_ndk.z if absent.
find_library(OHOS_PIXELMAP_LIB pixelmap)
if(NOT OHOS_PIXELMAP_LIB)
    find_library(OHOS_PIXELMAP_LIB pixelmap_ndk.z)
endif()
find_library(OHOS_NATIVE_BUFFER_LIB native_buffer)
find_library(OHOS_HIAPPEVENT_LIB hiappevent_ndk.z)
if(NOT OHOS_HIAPPEVENT_LIB)
    find_library(OHOS_HIAPPEVENT_LIB hiappevent_ndk)
endif()

# ------------------------------------------------------------------------------
# compose_arkui_utils (static, existing)
# ------------------------------------------------------------------------------
# 补上 HAR 版 libskikobridge.so 缺失的导出符号（我们自己的文件，见 nexio/ 里的注释）
# nexio/nexio_icu_shim.cpp：补 skiko 需要的 ICU 符号名（见该文件头注释）
add_library(compose_arkui_utils STATIC napi_init.cpp ${SOURCES} nexio/nexio_icu_shim.cpp)
target_link_libraries(compose_arkui_utils PUBLIC libace_napi.z.so)
target_link_libraries(compose_arkui_utils PUBLIC libdeviceinfo_ndk.z.so)
target_link_libraries(
    compose_arkui_utils PUBLIC
    ${EGL-lib}
    ${GLES-lib}
    ${hilog-lib}
    ${libace-lib}
    ${libnapi-lib}
    ${libuv-lib}
    ${OHOS_IMAGE_SOURCE_LIB}
    ${OHOS_PIXELMAP_LIB}
    ${OHOS_NATIVE_BUFFER_LIB}
    ${OHOS_HIAPPEVENT_LIB}
    ${libnative_vsync-lib}
    ${libqos-lib}
)

