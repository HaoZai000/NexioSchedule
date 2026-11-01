/*
 * Tencent is pleased to support the open source community by making ovCompose available.
 * Copyright (C) 2025 Tencent. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "arkui_utils_export.h"
#include "arkui_view_controller_wrapper.h"
#include "ohos_hiappevent_xperf_bridge.h"
#include "page_dpi_notifier.h"
#include "napi/native_api.h"
#include "xcomponent_common.h"
#include "xcomponent_holder.h"
#include "xcomponent_log.h"
#include "xcomponent_render.h"
#include "deviceinfo.h"
#include <ace/xcomponent/native_interface_xcomponent.h>
#include <arkui/native_type.h>
#include <rawfile/raw_file.h>
#include <rawfile/raw_file_manager.h>
#include <cstdint>
#include <dlfcn.h>
#include <mutex>
#include <qos/qos.h>
#include <string>
#include <unordered_map>

using ArkUiAccessibilityProvider = void;

#define ARKUI_UTILS_EXPORT(name) androidx_compose_ui_arkui_utils_##name

extern "C" __attribute__((weak)) ArkUI_NodeHandle org_jetbrains_skia_RenderNode__1nRenderNodeGetNodeHandle(
    void *ptr);

namespace {
std::mutex g_nodeHandlesMutex;

enum NodeHandleSource {
    NODE_HANDLE_SOURCE_ARKTS_FRAME_NODE = 1,
    NODE_HANDLE_SOURCE_FUSION_RENDER_NODE = 2,
};

struct NodeHandleEntry {
    void *handle = nullptr;
    int source = 0;
};

std::unordered_map<std::string, NodeHandleEntry> g_nodeHandles;
std::unordered_map<std::string, ArkUiAccessibilityProvider *> g_fallbackProviders;

using GetNativeAccessibilityProviderFn = int32_t (*)(ArkUI_NodeHandle *, ArkUiAccessibilityProvider **);
using CreateAccessibilityProviderFn = ArkUiAccessibilityProvider *(*)(ArkUI_NodeHandle);
using DisposeAccessibilityProviderFn = void (*)(ArkUiAccessibilityProvider *);
using GetRenderNodeHandleFn = ArkUI_NodeHandle (*)(void *);

void *ResolveAceLibHandle() {
    static void *handle = []() -> void* {
        void *resolved = dlopen("libace_" "n" "dk.z.so", RTLD_LAZY);
        if (resolved == nullptr) {
            LOGW("ResolveAceLibHandle dlopen failed");
        }
        return resolved;
    }();
    return handle;
}

template <typename T>
T ResolveAceLibSymbol(const char *symbolName) {
    void *handle = ResolveAceLibHandle();
    if (handle == nullptr) {
        return nullptr;
    }
    auto symbol = dlsym(handle, symbolName);
    if (symbol == nullptr) {
        LOGD("ResolveAceLibSymbol dlsym failed symbol=%{public}s", symbolName);
        return nullptr;
    }
    return reinterpret_cast<T>(symbol);
}

GetNativeAccessibilityProviderFn ResolveGetNativeAccessibilityProvider() {
    static GetNativeAccessibilityProviderFn fn = []() -> GetNativeAccessibilityProviderFn {
        return ResolveAceLibSymbol<GetNativeAccessibilityProviderFn>(
            "OH_ArkUI_NativeModule_GetNativeAccessibilityProvider");
    }();
    return fn;
}

CreateAccessibilityProviderFn ResolveCreateAccessibilityProvider() {
    static CreateAccessibilityProviderFn fn = []() -> CreateAccessibilityProviderFn {
        return ResolveAceLibSymbol<CreateAccessibilityProviderFn>("OH_ArkUI_AccessibilityProvider_Create");
    }();
    return fn;
}

DisposeAccessibilityProviderFn ResolveDisposeAccessibilityProvider() {
    static DisposeAccessibilityProviderFn fn = []() -> DisposeAccessibilityProviderFn {
        return ResolveAceLibSymbol<DisposeAccessibilityProviderFn>("OH_ArkUI_AccessibilityProvider_Dispose");
    }();
    return fn;
}

GetRenderNodeHandleFn ResolveGetRenderNodeHandle() {
    return org_jetbrains_skia_RenderNode__1nRenderNodeGetNodeHandle;
}

const char *NodeHandleSourceName(int source) {
    switch (source) {
        case NODE_HANDLE_SOURCE_ARKTS_FRAME_NODE:
            return "ArkTSFrameNode";
        case NODE_HANDLE_SOURCE_FUSION_RENDER_NODE:
            return "FusionRenderNode";
        default:
            return "Unknown";
    }
}

bool ShouldReuseNodeHandle(const char *composeId, void *nodeHandle, const NodeHandleEntry &entry, int source) {
    if (entry.handle != nodeHandle || entry.source != source) {
        return false;
    }
    LOGD(
        "registerNodeHandle reuse composeId=%{public}s nodeHandle=%{public}p source=%{public}s",
        composeId,
        nodeHandle,
        NodeHandleSourceName(source));
    return true;
}

bool ShouldKeepHigherPriorityNodeHandle(
    const char *composeId,
    void *nodeHandle,
    const NodeHandleEntry &entry,
    int source) {
    if (entry.source <= source) {
        return false;
    }
    LOGW(
        "registerNodeHandle skip low priority composeId=%{public}s old=%{public}p oldSource=%{public}s",
        composeId,
        entry.handle,
        NodeHandleSourceName(entry.source));
    LOGW(
        "registerNodeHandle skip low priority new=%{public}p newSource=%{public}s",
        nodeHandle,
        NodeHandleSourceName(source));
    return true;
}

void TakeFallbackProviderLocked(const char *composeId, ArkUiAccessibilityProvider **provider) {
    auto providerIterator = g_fallbackProviders.find(composeId);
    if (providerIterator == g_fallbackProviders.end()) {
        return;
    }
    *provider = providerIterator->second;
    g_fallbackProviders.erase(providerIterator);
}

void DisposeFallbackProvider(const char *composeId, ArkUiAccessibilityProvider *provider) {
    if (provider == nullptr) {
        return;
    }
    auto disposeFn = ResolveDisposeAccessibilityProvider();
    if (disposeFn != nullptr) {
        disposeFn(provider);
        return;
    }
    LOGW(
        "registerNodeHandle fallback dispose missing composeId=%{public}s provider=%{public}p",
        composeId,
        provider);
}

void LogNodeHandleOverwrite(const char *composeId, const NodeHandleEntry &entry, void *nodeHandle, int source) {
    LOGD(
        "registerNodeHandle overwrite composeId=%{public}s old=%{public}p oldSource=%{public}s",
        composeId,
        entry.handle,
        NodeHandleSourceName(entry.source));
    LOGD(
        "registerNodeHandle overwrite new=%{public}p newSource=%{public}s",
        nodeHandle,
        NodeHandleSourceName(source));
}

bool TryUpdateNodeHandleLocked(
    const char *composeId,
    void *nodeHandle,
    int source,
    ArkUiAccessibilityProvider **staleProvider) {
    auto oldEntry = g_nodeHandles.find(composeId);
    if (oldEntry != g_nodeHandles.end()) {
        const auto &entry = oldEntry->second;
        if (ShouldReuseNodeHandle(composeId, nodeHandle, entry, source)) {
            return false;
        }
        if (ShouldKeepHigherPriorityNodeHandle(composeId, nodeHandle, entry, source)) {
            return false;
        }
        TakeFallbackProviderLocked(composeId, staleProvider);
        LogNodeHandleOverwrite(composeId, entry, nodeHandle, source);
    }
    g_nodeHandles[composeId] = {nodeHandle, source};
    return true;
}

void RegisterNodeHandleWithSource(const char *composeId, void *nodeHandle, int source) {
    if (composeId == nullptr || nodeHandle == nullptr) {
        LOGE("registerNodeHandle invalid composeId=%{public}p nodeHandle=%{public}p", composeId, nodeHandle);
        return;
    }
    ArkUiAccessibilityProvider *staleProvider = nullptr;
    bool updated = false;
    {
        std::lock_guard<std::mutex> lock(g_nodeHandlesMutex);
        updated = TryUpdateNodeHandleLocked(composeId, nodeHandle, source, &staleProvider);
    }
    DisposeFallbackProvider(composeId, staleProvider);
    if (!updated) {
        return;
    }
    LOGD(
        "registerNodeHandle composeId=%{public}s nodeHandle=%{public}p source=%{public}s",
        composeId,
        nodeHandle,
        NodeHandleSourceName(source));
}

void *FindNodeHandle(const char *composeId) {
    std::lock_guard<std::mutex> lock(g_nodeHandlesMutex);
    auto iterator = g_nodeHandles.find(composeId);
    if (iterator == g_nodeHandles.end()) {
        return nullptr;
    }
    return iterator->second.handle;
}

ArkUiAccessibilityProvider *FindFallbackProvider(const char *composeId) {
    std::lock_guard<std::mutex> lock(g_nodeHandlesMutex);
    auto providerIterator = g_fallbackProviders.find(composeId);
    if (providerIterator == g_fallbackProviders.end()) {
        return nullptr;
    }
    LOGD(
        "providerFromComposeId reuse fallback composeId=%{public}s provider=%{public}p",
        composeId,
        providerIterator->second);
    return providerIterator->second;
}

ArkUiAccessibilityProvider *GetNativeProvider(const char *composeId, ArkUI_NodeHandle handle) {
    auto fn = ResolveGetNativeAccessibilityProvider();
    if (fn == nullptr) {
        LOGD("providerFromComposeId native provider unavailable composeId=%{public}s", composeId);
        return nullptr;
    }
    ArkUiAccessibilityProvider *provider = nullptr;
    int32_t result = fn(&handle, &provider);
    LOGD(
        "providerFromComposeId composeId=%{public}s result=%{public}d provider=%{public}p",
        composeId,
        result,
        provider);
    return provider;
}

ArkUiAccessibilityProvider *CreateFallbackProvider(const char *composeId, ArkUI_NodeHandle handle) {
    auto createFn = ResolveCreateAccessibilityProvider();
    if (createFn == nullptr) {
        LOGW("providerFromComposeId create function unavailable composeId=%{public}s", composeId);
        return nullptr;
    }
    auto provider = createFn(handle);
    LOGD(
        "providerFromComposeId create fallback composeId=%{public}s provider=%{public}p",
        composeId,
        provider);
    if (provider != nullptr) {
        std::lock_guard<std::mutex> lock(g_nodeHandlesMutex);
        g_fallbackProviders[composeId] = provider;
    }
    return provider;
}
}

#include <multimedia/image_framework/image/image_source_native.h>
#include <multimedia/image_framework/image/pixelmap_native.h>
#include <multimedia/image_framework/image_mdk_common.h>
#include <native_buffer/native_buffer.h>
#include <hiappevent/hiappevent.h>

#include <cstring>
#include <cerrno>
#include <fcntl.h>
#include <new>
#include <unistd.h>
#include <vector>

namespace {

struct DecodeBufferHandle {
    OH_PixelmapNative *pixelmap = nullptr;
    OH_NativeBuffer *nativeBuffer = nullptr;
};

struct DecodedPixelmapResult {
    OH_PixelmapNative *pixelmap = nullptr;
    OH_NativeBuffer *nativeBuffer = nullptr;
    uint32_t width = 0U;
    uint32_t height = 0U;
    int isYuv = 0;
    int requestedNv21 = 0;
    int requestedPixelFormat = -1;
    int hasDecodedPixelFormat = 0;
    int decodedPixelFormat = -1;
};

struct DmaDecodeSource {
    const uint8_t *data = nullptr;
    size_t size = 0U;
    NativeResourceManager *mgr = nullptr;
    const char *rawPath = nullptr;
};

// Optional: explicitly set native buffer colorspace for actual YUV outputs.
static constexpr bool K_DMA_YUV_DIAG_FORCE_COLOR_SPACE = true;
static constexpr OH_NativeBuffer_ColorSpace K_DMA_YUV_DIAG_FORCED_COLOR_SPACE = OH_COLORSPACE_BT601_EBU_FULL;
// Last-resort: alias layout -> RGBA. Off by default (kills YUV memory win).
static constexpr bool K_DMA_YUV_HOTFIX_FALLBACK_RGBA_ON_ALIAS_LAYOUT = false;
// Header peek size for alpha sniff before SetPixelFormat(NV21).
static constexpr size_t K_SOURCE_ALPHA_PEEK_BYTES = 4096U;
static constexpr size_t K_U32_BYTES = 4U;
static constexpr size_t K_U32_SHIFT_HI = 24U;
static constexpr size_t K_U32_SHIFT_MID_HI = 16U;
static constexpr size_t K_U32_SHIFT_MID_LO = 8U;
static constexpr size_t K_JPEG_SOI_BYTES = 3U;
static constexpr size_t K_PNG_SIG_BYTES = 8U;
static constexpr size_t K_RIFF_WEBP_BYTES = 12U;
static constexpr size_t K_WEBP_FOURCC_OFF = 8U;
static constexpr size_t K_GIF_SIG_BYTES = 6U;
static constexpr size_t K_HEIF_FTYP_BYTES = 12U;
static constexpr size_t K_HEIF_BRAND_OFF = 8U;
static constexpr size_t K_FOURCC_LEN = 4U;
static constexpr size_t K_WEBP_VP8X_MIN_BYTES = 21U;
static constexpr size_t K_WEBP_VP8X_FOURCC_OFF = 12U;
static constexpr size_t K_WEBP_VP8X_FLAGS_OFF = 20U;
static constexpr uint8_t K_WEBP_VP8X_ALPHA_BIT = 0x10U;
static constexpr size_t K_PNG_CHUNK_HDR_BYTES = 12U;
static constexpr size_t K_PNG_CHUNK_TYPE_OFF = 4U;
static constexpr size_t K_PNG_IHDR_DATA_OFF = 8U;
static constexpr size_t K_PNG_IHDR_MIN_LEN = 13U;
static constexpr size_t K_PNG_COLOR_TYPE_OFF = 9U;
static constexpr uint8_t K_PNG_COLOR_GRAY_ALPHA = 4U;
static constexpr uint8_t K_PNG_COLOR_RGBA = 6U;
static constexpr uint8_t K_PNG_COLOR_PALETTE = 3U;
static constexpr size_t K_MIN_CONTAINER_HEADER_BYTES = 12U;
static constexpr size_t K_ALIAS_UV_PLANE_COUNT = 3U;
static constexpr size_t K_ALIAS_UV_OFFSET_DELTA = 1U;

// Returns requested ImageKit pixel format, or -1 when caller leaves decoder default (no SetPixelFormat).
static int32_t RequestedYuvPixelFormatOrDefault(bool setNv21) {
    return setNv21 ? PIXEL_FORMAT_NV21 : -1;
}

static bool IsDecodedYuv420Sp(int32_t pixelFormat) {
    return pixelFormat == PIXEL_FORMAT_NV21 || pixelFormat == PIXEL_FORMAT_NV12;
}

static constexpr size_t K_PNG_SIG_BYTE_4 = 4U;
static constexpr size_t K_PNG_SIG_BYTE_5 = 5U;
static constexpr size_t K_PNG_SIG_BYTE_6 = 6U;
static constexpr size_t K_PNG_SIG_BYTE_7 = 7U;
static constexpr size_t K_GIF_VERSION_OFF = 4U;
static constexpr size_t K_GIF_TRAILER_OFF = 5U;
static constexpr size_t K_BYTE_INDEX_1 = 1U;
static constexpr size_t K_BYTE_INDEX_2 = 2U;
static constexpr size_t K_BYTE_INDEX_3 = 3U;
static constexpr size_t K_ALIAS_UV_PLANE_U = 1U;
static constexpr size_t K_ALIAS_UV_PLANE_V = 2U;

static bool FourCcEquals(const uint8_t *bytes, char a, char b, char c, char d) {
    return bytes != nullptr &&
        bytes[0] == static_cast<uint8_t>(a) &&
        bytes[K_BYTE_INDEX_1] == static_cast<uint8_t>(b) &&
        bytes[K_BYTE_INDEX_2] == static_cast<uint8_t>(c) &&
        bytes[K_BYTE_INDEX_3] == static_cast<uint8_t>(d);
}

static uint32_t ReadUInt32Be(const uint8_t *data, size_t size, size_t offset) {
    if (data == nullptr || offset + K_U32_BYTES > size) {
        return 0U;
    }
    return (static_cast<uint32_t>(data[offset]) << K_U32_SHIFT_HI) |
        (static_cast<uint32_t>(data[offset + K_BYTE_INDEX_1]) << K_U32_SHIFT_MID_HI) |
        (static_cast<uint32_t>(data[offset + K_BYTE_INDEX_2]) << K_U32_SHIFT_MID_LO) |
        static_cast<uint32_t>(data[offset + K_BYTE_INDEX_3]);
}

static bool HeaderIsJpeg(const uint8_t *data, size_t size) {
    return size >= K_JPEG_SOI_BYTES &&
        data[0] == 0xFFU &&
        data[K_BYTE_INDEX_1] == 0xD8U &&
        data[K_BYTE_INDEX_2] == 0xFFU;
}

static bool HeaderIsPng(const uint8_t *data, size_t size) {
    return size >= K_PNG_SIG_BYTES &&
        data[0] == 0x89U && data[K_BYTE_INDEX_1] == 0x50U &&
        data[K_BYTE_INDEX_2] == 0x4EU && data[K_BYTE_INDEX_3] == 0x47U &&
        data[K_PNG_SIG_BYTE_4] == 0x0DU && data[K_PNG_SIG_BYTE_5] == 0x0AU &&
        data[K_PNG_SIG_BYTE_6] == 0x1AU && data[K_PNG_SIG_BYTE_7] == 0x0AU;
}

static bool HeaderIsWebp(const uint8_t *data, size_t size) {
    return size >= K_RIFF_WEBP_BYTES &&
        FourCcEquals(data, 'R', 'I', 'F', 'F') &&
        FourCcEquals(data + K_WEBP_FOURCC_OFF, 'W', 'E', 'B', 'P');
}

static bool HeaderIsGif(const uint8_t *data, size_t size) {
    return size >= K_GIF_SIG_BYTES &&
        data[0] == 'G' && data[K_BYTE_INDEX_1] == 'I' &&
        data[K_BYTE_INDEX_2] == 'F' && data[K_BYTE_INDEX_3] == '8' &&
        (data[K_GIF_VERSION_OFF] == '7' || data[K_GIF_VERSION_OFF] == '9') &&
        data[K_GIF_TRAILER_OFF] == 'a';
}

static bool HeaderIsHeif(const uint8_t *data, size_t size) {
    if (size < K_HEIF_FTYP_BYTES) {
        return false;
    }
    if (!FourCcEquals(data + K_U32_BYTES, 'f', 't', 'y', 'p')) {
        return false;
    }
    const char *brand = reinterpret_cast<const char *>(data + K_HEIF_BRAND_OFF);
    return std::strncmp(brand, "heic", K_FOURCC_LEN) == 0 ||
        std::strncmp(brand, "heix", K_FOURCC_LEN) == 0 ||
        std::strncmp(brand, "mif1", K_FOURCC_LEN) == 0 ||
        std::strncmp(brand, "avif", K_FOURCC_LEN) == 0;
}

static bool DetectWebpHasAlpha(const uint8_t *data, size_t size) {
    if (size < K_WEBP_VP8X_MIN_BYTES) {
        // Non-VP8X / truncated: treat as opaque (matches existing Kotlin policy).
        return false;
    }
    if (!FourCcEquals(data + K_WEBP_VP8X_FOURCC_OFF, 'V', 'P', '8', 'X')) {
        return false;
    }
    return (data[K_WEBP_VP8X_FLAGS_OFF] & K_WEBP_VP8X_ALPHA_BIT) != 0U;
}

static bool PngColorTypeHasAlpha(uint8_t colorType) {
    return colorType == K_PNG_COLOR_GRAY_ALPHA || colorType == K_PNG_COLOR_RGBA;
}

static bool DetectPngHasAlpha(const uint8_t *data, size_t size) {
    size_t offset = K_PNG_SIG_BYTES;
    bool sawIhdr = false;
    uint8_t colorType = 0U;
    while (offset + K_PNG_CHUNK_HDR_BYTES <= size) {
        const uint32_t chunkLen = ReadUInt32Be(data, size, offset);
        const uint8_t *type = data + offset + K_PNG_CHUNK_TYPE_OFF;
        if (FourCcEquals(type, 'I', 'H', 'D', 'R')) {
            const size_t colorOff = offset + K_PNG_IHDR_DATA_OFF + K_PNG_COLOR_TYPE_OFF;
            if (chunkLen < K_PNG_IHDR_MIN_LEN || colorOff >= size) {
                return true;
            }
            sawIhdr = true;
            colorType = data[colorOff];
            if (PngColorTypeHasAlpha(colorType)) {
                return true;
            }
        } else if (FourCcEquals(type, 't', 'R', 'N', 'S')) {
            return true;
        } else if (FourCcEquals(type, 'I', 'E', 'N', 'D')) {
            return false;
        }
        const size_t next = offset + K_PNG_CHUNK_HDR_BYTES + static_cast<size_t>(chunkLen);
        if (next <= offset || next > size) {
            break;
        }
        offset = next;
    }
    // Incomplete peek: palette may still have tRNS later; gray/RGB without tRNS → opaque.
    return !sawIhdr || colorType == K_PNG_COLOR_PALETTE;
}

/**
 * True when encoded header indicates the source may need an alpha channel.
 * Conservative: incomplete / unknown headers are treated as transparent so we skip NV21.
 * Used only to gate SetPixelFormat(NV21); does not change the developer YUV switch.
 */
static bool DetectSourceHasAlpha(const uint8_t *data, size_t size) {
    if (data == nullptr || size < K_MIN_CONTAINER_HEADER_BYTES) {
        return true;
    }
    if (HeaderIsJpeg(data, size) || HeaderIsHeif(data, size)) {
        return false;
    }
    if (HeaderIsGif(data, size)) {
        return true;
    }
    if (HeaderIsWebp(data, size)) {
        return DetectWebpHasAlpha(data, size);
    }
    if (HeaderIsPng(data, size)) {
        return DetectPngHasAlpha(data, size);
    }
    return true;
}

static const char *NormalizeRawFilePath(const char *path);

static bool PeekRawFileHeader(
    NativeResourceManager *mgr,
    const char *path,
    std::vector<uint8_t> *outHeader
) {
    if (mgr == nullptr || path == nullptr || outHeader == nullptr) {
        return false;
    }
    const char *normalizedPath = NormalizeRawFilePath(path);
    if (normalizedPath == nullptr || normalizedPath[0] == '\0') {
        return false;
    }
    RawFile *rawFile = OH_ResourceManager_OpenRawFile(mgr, normalizedPath);
    if (rawFile == nullptr) {
        return false;
    }
    outHeader->assign(K_SOURCE_ALPHA_PEEK_BYTES, 0U);
    const int readCount = OH_ResourceManager_ReadRawFile(
        rawFile, outHeader->data(), K_SOURCE_ALPHA_PEEK_BYTES);
    OH_ResourceManager_CloseRawFile(rawFile);
    if (readCount <= 0) {
        outHeader->clear();
        return false;
    }
    outHeader->resize(static_cast<size_t>(readCount));
    return true;
}

static bool IsYuv420SpNativeBufferFormat(int32_t nativeBufferFormat) {
    return nativeBufferFormat == NATIVEBUFFER_PIXEL_FMT_YCRCB_420_SP ||
        nativeBufferFormat == NATIVEBUFFER_PIXEL_FMT_YCBCR_420_SP;
}

static bool CopyBytesToVector(std::vector<uint8_t> &dest, const uint8_t *src, size_t size) {
    if (src == nullptr || size == 0U) {
        return false;
    }
    dest.assign(src, src + size);
    return true;
}

// Some OHOS image decode paths are not stable under high parallelism in native layer.
// Serialize decode-to-pixelmap to avoid native crashes (SIGSEGV) when DMA decode is enabled.
std::mutex gDecodeBufferHandleMutex;

static bool PeekImageSizeFromImageSource(OH_ImageSourceNative *imageSource, uint32_t *outWidth, uint32_t *outHeight);

static void ReleaseDecodedPixelmapResult(DecodedPixelmapResult *result, bool unreferenceNativeBuffer) {
    if (result == nullptr) {
        return;
    }
    if (unreferenceNativeBuffer && result->nativeBuffer != nullptr) {
        auto unrefResult = OH_NativeBuffer_Unreference(result->nativeBuffer);
        if (unrefResult != 0) {
            LOGE("ReleaseDecodedPixelmapResult: native buffer unreference failed ret=%{public}d", unrefResult);
        }
        result->nativeBuffer = nullptr;
    }
    if (result->pixelmap != nullptr) {
        OH_PixelmapNative_Release(result->pixelmap);
        result->pixelmap = nullptr;
    }
}

static const char *NormalizeRawFilePath(const char *path) {
    if (path == nullptr) {
        return nullptr;
    }
    static constexpr const char kPrefix[] = "rawfile/";
    if (strncmp(path, kPrefix, sizeof(kPrefix) - 1U) == 0) {
        return path + (sizeof(kPrefix) - 1U);
    }
    return path;
}

static bool CreateImageSourceFromRawFilePath(
    NativeResourceManager *mgr,
    const char *path,
    OH_ImageSourceNative **outSource
) {
    if (mgr == nullptr || path == nullptr || outSource == nullptr) {
        return false;
    }
    *outSource = nullptr;
    const char *normalizedPath = NormalizeRawFilePath(path);
    if (normalizedPath == nullptr || normalizedPath[0] == '\0') {
        return false;
    }

    RawFile *rawFile = OH_ResourceManager_OpenRawFile(mgr, normalizedPath);
    if (rawFile == nullptr) {
        LOGE("CreateImageSourceFromRawFilePath: OpenRawFile failed path=%{public}s", normalizedPath);
        return false;
    }

    RawFileDescriptor descriptor{};
    const bool gotDescriptor = OH_ResourceManager_GetRawFileDescriptorData(rawFile, &descriptor);
    OH_ResourceManager_CloseRawFile(rawFile);
    if (!gotDescriptor) {
        LOGE("CreateImageSourceFromRawFilePath: GetRawFileDescriptor failed path=%{public}s", normalizedPath);
        return false;
    }

    Image_ErrorCode err = OH_ImageSourceNative_CreateFromRawFile(&descriptor, outSource);
    OH_ResourceManager_ReleaseRawFileDescriptorData(&descriptor);
    if (err != IMAGE_SUCCESS || *outSource == nullptr) {
        LOGE("CreateImageSourceFromRawFilePath: CreateFromRawFile err=%{public}d path=%{public}s", err, normalizedPath);
        return false;
    }
    LOGI("CreateImageSourceFromRawFilePath: ok path=%{public}s", normalizedPath);
    return true;
}

static bool DecodePixelmapFromImageSource(
    OH_ImageSourceNative *imageSource,
    uint32_t desiredWidth,
    uint32_t desiredHeight,
    uint32_t cropX,
    uint32_t cropY,
    uint32_t cropWidth,
    uint32_t cropHeight,
    DecodedPixelmapResult *outResult
) {
    if (imageSource == nullptr || outResult == nullptr) {
        return false;
    }
    outResult->pixelmap = nullptr;
    outResult->nativeBuffer = nullptr;

    OH_DecodingOptions *opts = nullptr;
    Image_ErrorCode err = OH_DecodingOptions_Create(&opts);
    if (err != IMAGE_SUCCESS || opts == nullptr) {
        LOGE("DecodePixelmapFromImageSource: DecodingOptions_Create err=%{public}d opts=%{public}p", err, opts);
        return false;
    }

    err = OH_DecodingOptions_SetPixelFormat(opts, PIXEL_FORMAT_RGBA_8888);
    if (err != IMAGE_SUCCESS) {
        LOGE("DecodePixelmapFromImageSource: SetPixelFormat(RGBA_8888) err=%{public}d", err);
        OH_DecodingOptions_Release(opts);
        return false;
    }
    (void)OH_DecodingOptions_SetDesiredDynamicRange(opts, IMAGE_DYNAMIC_RANGE_SDR);

    if (desiredWidth > 0U && desiredHeight > 0U) {
        Image_Size desiredSize{};
        desiredSize.width = desiredWidth;
        desiredSize.height = desiredHeight;
        err = OH_DecodingOptions_SetDesiredSize(opts, &desiredSize);
        if (err != IMAGE_SUCCESS) {
            LOGE("DecodePixelmapFromImageSource: SetDesiredSize(%{public}u x %{public}u) err=%{public}d",
                 desiredWidth, desiredHeight, err);
        } else {
            LOGI(
                "DecodePixelmapFromImageSource: SetDesiredSize %{public}u x %{public}u ok",
                desiredWidth,
                desiredHeight);
        }
    }

    if (cropWidth > 0U && cropHeight > 0U) {
        Image_Region cropRegion{};
        cropRegion.x = static_cast<int32_t>(cropX);
        cropRegion.y = static_cast<int32_t>(cropY);
        cropRegion.width = static_cast<int32_t>(cropWidth);
        cropRegion.height = static_cast<int32_t>(cropHeight);
        err = OH_DecodingOptions_SetCropRegion(opts, &cropRegion);
        if (err != IMAGE_SUCCESS) {
            LOGE(
                "DecodePixelmapFromImageSource: SetCropRegion(%{public}u,%{public}u "
                "%{public}ux%{public}u) err=%{public}d",
                 cropX, cropY, cropWidth, cropHeight, err);
        } else {
            LOGI("DecodePixelmapFromImageSource: SetCropRegion %{public}u,%{public}u %{public}ux%{public}u ok",
                 cropX, cropY, cropWidth, cropHeight);
        }
        if (desiredWidth > 0U && desiredHeight > 0U) {
            (void)OH_DecodingOptions_SetCropAndScaleStrategy(
                opts, IMAGE_CROP_AND_SCALE_STRATEGY_CROP_FIRST);
        }
    }

    OH_PixelmapNative *pixelmap = nullptr;
    // AUTO: JPEG/PNG prefer DMA (+ possible HW decode); SVG etc. fall back to SHARE_MEMORY internally.
    err = OH_ImageSourceNative_CreatePixelmapUsingAllocator(
        imageSource, opts, IMAGE_ALLOCATOR_TYPE_AUTO, &pixelmap);
    if (err != IMAGE_SUCCESS || pixelmap == nullptr) {
        LOGE("DecodePixelmapFromImageSource: CreatePixelmapUsingAllocator(AUTO) err=%{public}d pixelmap=%{public}p",
             err, pixelmap);
        if (pixelmap != nullptr) {
            OH_PixelmapNative_Release(pixelmap);
            pixelmap = nullptr;
        }
        err = OH_ImageSourceNative_CreatePixelmap(imageSource, opts, &pixelmap);
        if (err != IMAGE_SUCCESS || pixelmap == nullptr) {
            LOGE("DecodePixelmapFromImageSource: CreatePixelmap fallback err=%{public}d pixelmap=%{public}p",
                 err, pixelmap);
            if (pixelmap != nullptr) {
                OH_PixelmapNative_Release(pixelmap);
            }
            OH_DecodingOptions_Release(opts);
            return false;
        }
        LOGI("DecodePixelmapFromImageSource: CreatePixelmap(RGBA_8888) fallback ok");
    } else {
        LOGI("DecodePixelmapFromImageSource: CreatePixelmapUsingAllocator(AUTO, RGBA_8888) ok");
    }
    OH_DecodingOptions_Release(opts);

    OH_NativeBuffer *nativeBuffer = nullptr;
    err = OH_PixelmapNative_GetNativeBuffer(pixelmap, &nativeBuffer);
    if (err != IMAGE_SUCCESS || nativeBuffer == nullptr) {
        LOGE(
            "DecodePixelmapFromImageSource: GetNativeBuffer err=%{public}d nativeBuffer=%{public}p",
            err,
            nativeBuffer);
        OH_PixelmapNative_Release(pixelmap);
        return false;
    }

    outResult->pixelmap = pixelmap;
    outResult->nativeBuffer = nativeBuffer;
    return true;
}

bool DecodeToPixelmapNative(
    const uint8_t *data,
    size_t size,
    uint32_t desiredWidth,
    uint32_t desiredHeight,
    uint32_t cropX,
    uint32_t cropY,
    uint32_t cropWidth,
    uint32_t cropHeight,
    DecodedPixelmapResult *outResult
) {
    if (outResult == nullptr) return false;
    outResult->pixelmap = nullptr;
    outResult->nativeBuffer = nullptr;
    if (data == nullptr || size == 0U) return false;

    LOGI("DecodeToPixelmapNative: enter size=%{public}zu desired=%{public}ux%{public}u",
         size, desiredWidth, desiredHeight);

    std::vector<uint8_t> dataCopy(size);
    memcpy(dataCopy.data(), data, size);

    OH_ImageSourceNative *imageSource = nullptr;
    Image_ErrorCode err = OH_ImageSourceNative_CreateFromData(dataCopy.data(), size, &imageSource);
    if (err != IMAGE_SUCCESS || imageSource == nullptr) {
        LOGE("DecodeToPixelmapNative: CreateFromData err=%{public}d source=%{public}p", err, imageSource);
        if (imageSource != nullptr) {
            OH_ImageSourceNative_Release(imageSource);
        }
        return false;
    }

    const bool decoded = DecodePixelmapFromImageSource(
        imageSource, desiredWidth, desiredHeight, cropX, cropY, cropWidth, cropHeight, outResult);
    OH_ImageSourceNative_Release(imageSource);
    return decoded;
}

static bool DecodeToPixelmapFromRawFilePath(
    NativeResourceManager *mgr,
    const char *path,
    uint32_t desiredWidth,
    uint32_t desiredHeight,
    uint32_t cropX,
    uint32_t cropY,
    uint32_t cropWidth,
    uint32_t cropHeight,
    DecodedPixelmapResult *outResult
) {
    OH_ImageSourceNative *imageSource = nullptr;
    if (!CreateImageSourceFromRawFilePath(mgr, path, &imageSource)) {
        return false;
    }
    const bool decoded = DecodePixelmapFromImageSource(
        imageSource, desiredWidth, desiredHeight, cropX, cropY, cropWidth, cropHeight, outResult);
    OH_ImageSourceNative_Release(imageSource);
    return decoded;
}

static bool CreateImageSourceFromDataCopy(const uint8_t *data, size_t size, OH_ImageSourceNative **outSource) {
    if (data == nullptr || size == 0U || outSource == nullptr) {
        return false;
    }
    *outSource = nullptr;
    std::vector<uint8_t> dataCopy;
    if (!CopyBytesToVector(dataCopy, data, size)) {
        return false;
    }
    Image_ErrorCode err = OH_ImageSourceNative_CreateFromData(dataCopy.data(), size, outSource);
    if (err != IMAGE_SUCCESS || *outSource == nullptr) {
        if (*outSource != nullptr) {
            OH_ImageSourceNative_Release(*outSource);
            *outSource = nullptr;
        }
        return false;
    }
    return true;
}

static bool CreateDmaDecodingOptions(
    const OhosImageDecodeParams *params,
    OH_DecodingOptions **outOpts,
    bool sourceHasAlpha
) {
    if (outOpts == nullptr) {
        return false;
    }
    *outOpts = nullptr;
    const bool useNv21 = params != nullptr && params->useNv21 != 0;
    const uint32_t desiredWidth = params != nullptr ? params->desiredWidth : 0U;
    const uint32_t desiredHeight = params != nullptr ? params->desiredHeight : 0U;
    // Developer YUV switch stays intact; alpha sources skip SetPixelFormat(NV21).
    const bool setNv21 = useNv21 && !sourceHasAlpha;

    OH_DecodingOptions *opts = nullptr;
    Image_ErrorCode err = OH_DecodingOptions_Create(&opts);
    if (err != IMAGE_SUCCESS || opts == nullptr) {
        return false;
    }

    if (setNv21) {
        err = OH_DecodingOptions_SetPixelFormat(opts, PIXEL_FORMAT_NV21);
        if (err != IMAGE_SUCCESS) {
            OH_DecodingOptions_Release(opts);
            return false;
        }
        LOGD(
            "DecodeContract: setPixelFormat=NV21 useNv21=1 hasAlpha=0");
    } else if (useNv21 && sourceHasAlpha) {
        LOGD(
            "DecodeContract: skip SetPixelFormat(NV21) useNv21=1 hasAlpha=1");
    }
    (void)OH_DecodingOptions_SetDesiredDynamicRange(opts, IMAGE_DYNAMIC_RANGE_SDR);

    if (desiredWidth > 0U && desiredHeight > 0U) {
        Image_Size desiredSize{};
        desiredSize.width = desiredWidth;
        desiredSize.height = desiredHeight;
        (void)OH_DecodingOptions_SetDesiredSize(opts, &desiredSize);
    }
    *outOpts = opts;
    return true;
}

static int32_t ReadNativeBufferFormat(OH_NativeBuffer *nativeBuffer) {
    if (nativeBuffer == nullptr) {
        return -1;
    }
    OH_NativeBuffer_Config config{};
    OH_NativeBuffer_GetConfig(nativeBuffer, &config);
    return config.format;
}

static bool FillPixelmapImageInfo(
    OH_PixelmapNative *pixelmap,
    DecodedPixelmapResult *outResult,
    int32_t *outDecodedFormat,
    bool *outHasDecodedFormat
) {
    *outDecodedFormat = -1;
    *outHasDecodedFormat = false;
    OH_Pixelmap_ImageInfo *pmInfo = nullptr;
    if (OH_PixelmapImageInfo_Create(&pmInfo) != IMAGE_SUCCESS || pmInfo == nullptr) {
        return true;
    }
    if (OH_PixelmapNative_GetImageInfo(pixelmap, pmInfo) == IMAGE_SUCCESS) {
        OH_PixelmapImageInfo_GetWidth(pmInfo, &outResult->width);
        OH_PixelmapImageInfo_GetHeight(pmInfo, &outResult->height);
        if (OH_PixelmapImageInfo_GetPixelFormat(pmInfo, outDecodedFormat) == IMAGE_SUCCESS) {
            *outHasDecodedFormat = true;
        }
    }
    OH_PixelmapImageInfo_Release(pmInfo);
    return true;
}

static const char *DecodeContractCaseName(bool requestedYuv, int isYuv) {
    if (requestedYuv && isYuv != 0) {
        return "request_nv21_got_yuv";
    }
    if (requestedYuv) {
        return "request_nv21_got_rgba";
    }
    if (isYuv != 0) {
        return "request_default_got_yuv";
    }
    return "request_default_got_rgba";
}

static void LogDecodedPixelmapContract(
    bool requestedYuv,
    int32_t requestedPixelFormat,
    int32_t decodedFormat,
    int32_t nativeFormat,
    const DecodedPixelmapResult *result
) {
    LOGD(
        "DecodeContract: requestedYuv=%{public}d requestedFmt=%{public}d "
        "pmFmt=%{public}d nbFmt=%{public}d isYuv=%{public}d size=%{public}ux%{public}u "
        "case=%{public}s",
        requestedYuv ? 1 : 0,
        requestedPixelFormat,
        decodedFormat,
        nativeFormat,
        result->isYuv,
        result->width,
        result->height,
        DecodeContractCaseName(requestedYuv, result->isYuv));
    // Do not fail when requested NV21 but system returned RGBA (or vice versa).
    // Caller/skiko must align to actual isYuv.
    if (requestedPixelFormat == PIXEL_FORMAT_NV21 && result->isYuv == 0) {
        LOGD(
            "DecodeContract: SetPixelFormat(NV21) but actual non-YUV "
            "pmFmt=%{public}d nbFmt=%{public}d (import must use RGBA path)",
            decodedFormat,
            nativeFormat);
    }
}

static bool PopulateDecodedPixelmapMetadata(
    OH_ImageSourceNative *imageSource,
    OH_PixelmapNative *pixelmap,
    OH_NativeBuffer *nativeBuffer,
    bool requestedYuv,
    int32_t requestedPixelFormat,
    DecodedPixelmapResult *outResult
) {
    if (imageSource == nullptr || pixelmap == nullptr || outResult == nullptr) {
        return false;
    }
    int32_t decodedFormat = -1;
    bool hasDecodedFormat = false;
    outResult->requestedNv21 = requestedYuv ? 1 : 0;
    outResult->requestedPixelFormat = requestedPixelFormat;
    (void)FillPixelmapImageInfo(pixelmap, outResult, &decodedFormat, &hasDecodedFormat);
    outResult->hasDecodedPixelFormat = hasDecodedFormat ? 1 : 0;
    outResult->decodedPixelFormat = decodedFormat;

    // Authority for skiko import: actual NativeBuffer format after decode.
    const int32_t nativeFormat = ReadNativeBufferFormat(nativeBuffer);
    const bool yuvFromPixelmap = hasDecodedFormat && IsDecodedYuv420Sp(decodedFormat);
    const bool yuvFromNative = IsYuv420SpNativeBufferFormat(nativeFormat);
    outResult->isYuv = (yuvFromPixelmap || yuvFromNative) ? 1 : 0;

    if (outResult->width == 0U || outResult->height == 0U) {
        if (!PeekImageSizeFromImageSource(imageSource, &outResult->width, &outResult->height)) {
            return false;
        }
    }
    LogDecodedPixelmapContract(
        requestedYuv, requestedPixelFormat, decodedFormat, nativeFormat, outResult);
    return true;
}

static bool DecodePixelmapWithRequestedFormat(
    OH_ImageSourceNative *imageSource,
    const OhosImageDecodeParams *params,
    bool decodeAsYuv,
    bool sourceHasAlpha,
    OH_PixelmapNative **outPixelmap,
    OH_NativeBuffer **outNativeBuffer,
    int32_t *outRequestedFormat
) {
    if (outPixelmap == nullptr || outNativeBuffer == nullptr || outRequestedFormat == nullptr) {
        return false;
    }

    OH_DecodingOptions *opts = nullptr;
    OhosImageDecodeParams paramsCopy{};
    if (params != nullptr) {
        paramsCopy = *params;
    }
    paramsCopy.useNv21 = decodeAsYuv ? 1 : 0;
    if (!CreateDmaDecodingOptions(&paramsCopy, &opts, sourceHasAlpha)) {
        return false;
    }

    OH_PixelmapNative *pixelmap = nullptr;
    Image_ErrorCode err = OH_ImageSourceNative_CreatePixelmapUsingAllocator(
        imageSource, opts, IMAGE_ALLOCATOR_TYPE_DMA, &pixelmap);
    OH_DecodingOptions_Release(opts);
    if (err != IMAGE_SUCCESS || pixelmap == nullptr) {
        if (pixelmap != nullptr) {
            OH_PixelmapNative_Release(pixelmap);
        }
        return false;
    }

    OH_NativeBuffer *nativeBuffer = nullptr;
    err = OH_PixelmapNative_GetNativeBuffer(pixelmap, &nativeBuffer);
    if (err != IMAGE_SUCCESS || nativeBuffer == nullptr) {
        OH_PixelmapNative_Release(pixelmap);
        return false;
    }

    *outPixelmap = pixelmap;
    *outNativeBuffer = nativeBuffer;
    const bool setNv21 = decodeAsYuv && !sourceHasAlpha;
    *outRequestedFormat = RequestedYuvPixelFormatOrDefault(setNv21);
    return true;
}

static bool HasProblematicAliasLayout(OH_NativeBuffer *nativeBuffer, int32_t format) {
    // NV21 (YCRCB) and NV12 (YCBCR) can both advertise 3-plane UV-alias descriptors.
    if (nativeBuffer == nullptr ||
        (format != NATIVEBUFFER_PIXEL_FMT_YCRCB_420_SP &&
         format != NATIVEBUFFER_PIXEL_FMT_YCBCR_420_SP)) {
        return false;
    }
    void *addr = nullptr;
    OH_NativeBuffer_Planes planes{};
    const int32_t mapRet = OH_NativeBuffer_MapPlanes(nativeBuffer, &addr, &planes);
    if (mapRet != 0 || addr == nullptr) {
        return false;
    }
    const bool aliasLayout =
        planes.planeCount == K_ALIAS_UV_PLANE_COUNT &&
        planes.planes[K_ALIAS_UV_PLANE_U].offset ==
            planes.planes[K_ALIAS_UV_PLANE_V].offset + K_ALIAS_UV_OFFSET_DELTA;
    LOGD(
        "YuvDiag: MapPlanes format=%{public}d planeCount=%{public}u alias=%{public}d "
        "p1.off=%{public}llu p2.off=%{public}llu",
        format,
        planes.planeCount,
        aliasLayout ? 1 : 0,
        static_cast<unsigned long long>(planes.planes[K_ALIAS_UV_PLANE_U].offset),
        static_cast<unsigned long long>(planes.planes[K_ALIAS_UV_PLANE_V].offset));
    (void)OH_NativeBuffer_Unmap(nativeBuffer);
    return aliasLayout;
}

struct AliasLayoutRecoverIo {
    OH_ImageSourceNative *imageSource = nullptr;
    const OhosImageDecodeParams *params = nullptr;
    bool useNv21 = false;
    bool sourceHasAlpha = false;
    OH_PixelmapNative **ioPixelmap = nullptr;
    OH_NativeBuffer **ioNativeBuffer = nullptr;
    int32_t *ioRequestedPixelFormat = nullptr;
};

static bool MaybeRecoverFromAliasLayout(const AliasLayoutRecoverIo &io) {
    if (!io.useNv21 || io.sourceHasAlpha) {
        return true;
    }
    OH_NativeBuffer_Config layoutConfig{};
    OH_NativeBuffer_GetConfig(*io.ioNativeBuffer, &layoutConfig);
    if (!HasProblematicAliasLayout(*io.ioNativeBuffer, layoutConfig.format)) {
        return true;
    }

    if (!K_DMA_YUV_HOTFIX_FALLBACK_RGBA_ON_ALIAS_LAYOUT) {
        LOGD("YuvHotfix: alias-layout kept (no RGBA fallback)");
        return true;
    }

    LOGW("YuvHotfix: alias-layout fallback to RGBA");
    if (*io.ioPixelmap != nullptr) {
        OH_PixelmapNative_Release(*io.ioPixelmap);
        *io.ioPixelmap = nullptr;
        *io.ioNativeBuffer = nullptr;
    }
    if (!DecodePixelmapWithRequestedFormat(
        io.imageSource,
        io.params,
        false,
        io.sourceHasAlpha,
        io.ioPixelmap,
        io.ioNativeBuffer,
        io.ioRequestedPixelFormat)) {
        LOGW("YuvHotfix: RGBA fallback decode failed");
        return false;
    }
    return true;
}

static void MaybeSetForcedYuvColorSpace(bool metadataUseNv21, OH_NativeBuffer *nativeBuffer) {
    if (!metadataUseNv21 || !K_DMA_YUV_DIAG_FORCE_COLOR_SPACE || nativeBuffer == nullptr) {
        return;
    }
    const int32_t setColorSpaceRet =
        OH_NativeBuffer_SetColorSpace(nativeBuffer, K_DMA_YUV_DIAG_FORCED_COLOR_SPACE);
    OH_NativeBuffer_ColorSpace verifyColorSpace = OH_COLORSPACE_NONE;
    const int32_t getColorSpaceRet =
        OH_NativeBuffer_GetColorSpace(nativeBuffer, &verifyColorSpace);
    if (setColorSpaceRet != 0 ||
        getColorSpaceRet != 0 ||
        verifyColorSpace != K_DMA_YUV_DIAG_FORCED_COLOR_SPACE) {
        LOGD(
            "YuvHotfix: colorspace set mismatch setRet=%{public}d getRet=%{public}d "
            "verify=%{public}d target=%{public}d",
            setColorSpaceRet,
            getColorSpaceRet,
            static_cast<int>(verifyColorSpace),
            static_cast<int>(K_DMA_YUV_DIAG_FORCED_COLOR_SPACE));
    }
}

static bool DecodePixelmapFromImageSourceWithParams(
    OH_ImageSourceNative *imageSource,
    const OhosImageDecodeParams *params,
    bool sourceHasAlpha,
    DecodedPixelmapResult *outResult
) {
    if (imageSource == nullptr || outResult == nullptr) {
        return false;
    }
    outResult->pixelmap = nullptr;
    outResult->nativeBuffer = nullptr;
    outResult->width = 0U;
    outResult->height = 0U;
    outResult->isYuv = 0;

    const bool useNv21 = params != nullptr && params->useNv21 != 0;
    const bool setNv21 = useNv21 && !sourceHasAlpha;
    const int32_t requestedPixelFormat = RequestedYuvPixelFormatOrDefault(setNv21);

    OH_PixelmapNative *pixelmap = nullptr;
    OH_NativeBuffer *nativeBuffer = nullptr;
    int32_t effectiveRequestedPixelFormat = requestedPixelFormat;
    if (!DecodePixelmapWithRequestedFormat(
        imageSource,
        params,
        useNv21,
        sourceHasAlpha,
        &pixelmap,
        &nativeBuffer,
        &effectiveRequestedPixelFormat)) {
        return false;
    }

    AliasLayoutRecoverIo recoverIo{};
    recoverIo.imageSource = imageSource;
    recoverIo.params = params;
    recoverIo.useNv21 = useNv21;
    recoverIo.sourceHasAlpha = sourceHasAlpha;
    recoverIo.ioPixelmap = &pixelmap;
    recoverIo.ioNativeBuffer = &nativeBuffer;
    recoverIo.ioRequestedPixelFormat = &effectiveRequestedPixelFormat;
    if (!MaybeRecoverFromAliasLayout(recoverIo)) {
        return false;
    }

    if (!PopulateDecodedPixelmapMetadata(
        imageSource,
        pixelmap,
        nativeBuffer,
        useNv21,
        effectiveRequestedPixelFormat,
        outResult)) {
        OH_PixelmapNative_Release(pixelmap);
        return false;
    }

    MaybeSetForcedYuvColorSpace(outResult->isYuv != 0, nativeBuffer);

    outResult->pixelmap = pixelmap;
    outResult->nativeBuffer = nativeBuffer;
    return true;
}

static bool DecodeToPixelmapNativeWithParams(
    const uint8_t *data,
    size_t size,
    const OhosImageDecodeParams *params,
    DecodedPixelmapResult *outResult
) {
    if (outResult == nullptr || data == nullptr || size == 0U) {
        return false;
    }
    const bool sourceHasAlpha = DetectSourceHasAlpha(data, size);
    OH_ImageSourceNative *imageSource = nullptr;
    if (!CreateImageSourceFromDataCopy(data, size, &imageSource)) {
        return false;
    }
    const bool ok =
        DecodePixelmapFromImageSourceWithParams(imageSource, params, sourceHasAlpha, outResult);
    OH_ImageSourceNative_Release(imageSource);
    return ok;
}

static bool DecodeRawFileToPixelmapNativeWithParams(
    NativeResourceManager *mgr,
    const char *path,
    const OhosImageDecodeParams *params,
    DecodedPixelmapResult *outResult
) {
    if (outResult == nullptr) {
        return false;
    }
    std::vector<uint8_t> header;
    const bool peeked = PeekRawFileHeader(mgr, path, &header);
    const bool sourceHasAlpha =
        peeked ? DetectSourceHasAlpha(header.data(), header.size()) : true;
    OH_ImageSourceNative *imageSource = nullptr;
    if (!CreateImageSourceFromRawFilePath(mgr, path, &imageSource)) {
        return false;
    }
    const bool ok =
        DecodePixelmapFromImageSourceWithParams(imageSource, params, sourceHasAlpha, outResult);
    OH_ImageSourceNative_Release(imageSource);
    return ok;
}

static bool ExportReferencedSurfaceBuffer(const DecodedPixelmapResult &result, void **outSurfaceBuffer) {
    if (outSurfaceBuffer == nullptr || result.nativeBuffer == nullptr) {
        return false;
    }
    *outSurfaceBuffer = nullptr;
    if (OH_NativeBuffer_Reference(result.nativeBuffer) != 0) {
        OH_PixelmapNative_Release(result.pixelmap);
        return false;
    }
    OH_PixelmapNative_Release(result.pixelmap);
    *outSurfaceBuffer = reinterpret_cast<void *>(result.nativeBuffer);
    return true;
}

static int DecodeToDmaSurfaceBufferInternal(
    const DmaDecodeSource &source,
    const OhosImageDecodeParams *params,
    OhosDmaDecodeOutput *output
) {
    if (output == nullptr || output->outWidth == nullptr || output->outHeight == nullptr ||
        output->outIsYuv == nullptr || output->outSurfaceBuffer == nullptr) {
        return 0;
    }
    *output->outWidth = 0U;
    *output->outHeight = 0U;
    *output->outIsYuv = 0;
    *output->outSurfaceBuffer = nullptr;

    std::lock_guard<std::mutex> lock(gDecodeBufferHandleMutex);
    DecodedPixelmapResult result{};
    const bool decoded = source.rawPath != nullptr
        ? DecodeRawFileToPixelmapNativeWithParams(source.mgr, source.rawPath, params, &result)
        : DecodeToPixelmapNativeWithParams(source.data, source.size, params, &result);
    if (!decoded) {
        return 0;
    }
    if (!ExportReferencedSurfaceBuffer(result, output->outSurfaceBuffer)) {
        return 0;
    }
    *output->outWidth = result.width;
    *output->outHeight = result.height;
    *output->outIsYuv = result.isYuv;
    return 1;
}

static bool PeekImageSizeFromImageSource(OH_ImageSourceNative *imageSource, uint32_t *outWidth, uint32_t *outHeight) {
    if (imageSource == nullptr || outWidth == nullptr || outHeight == nullptr) {
        return false;
    }
    OH_ImageSource_Info *info = nullptr;
    Image_ErrorCode err = OH_ImageSourceInfo_Create(&info);
    if (err != IMAGE_SUCCESS || info == nullptr) {
        return false;
    }
    err = OH_ImageSourceNative_GetImageInfo(imageSource, 0, info);
    if (err != IMAGE_SUCCESS) {
        OH_ImageSourceInfo_Release(info);
        return false;
    }
    uint32_t width = 0U;
    uint32_t height = 0U;
    err = OH_ImageSourceInfo_GetWidth(info, &width);
    if (err != IMAGE_SUCCESS) {
        OH_ImageSourceInfo_Release(info);
        return false;
    }
    err = OH_ImageSourceInfo_GetHeight(info, &height);
    OH_ImageSourceInfo_Release(info);
    if (err != IMAGE_SUCCESS || width == 0U || height == 0U) {
        return false;
    }
    *outWidth = width;
    *outHeight = height;
    return true;
}

static bool CreateImageSourceFromFd(int32_t fd, OH_ImageSourceNative **outSource) {
    if (fd < 0 || outSource == nullptr) {
        return false;
    }
    *outSource = nullptr;
    Image_ErrorCode err = OH_ImageSourceNative_CreateFromFd(fd, outSource);
    if (err != IMAGE_SUCCESS || *outSource == nullptr) {
        LOGE("CreateImageSourceFromFd: CreateFromFd err=%{public}d fd=%{public}d", err, fd);
        return false;
    }
    return true;
}

static bool CreateImageSourceFromFilePath(const char *path, OH_ImageSourceNative **outSource) {
    if (path == nullptr || path[0] == '\0' || outSource == nullptr) {
        return false;
    }
    *outSource = nullptr;
    const int fd = open(path, O_RDONLY);
    if (fd < 0) {
        LOGE("CreateImageSourceFromFilePath: open failed path=%{public}s errno=%{public}d", path, errno);
        return false;
    }
    const bool ok = CreateImageSourceFromFd(fd, outSource);
    close(fd);
    return ok;
}

static bool DecodeToPixelmapFromFd(
    int32_t fd,
    uint32_t desiredWidth,
    uint32_t desiredHeight,
    uint32_t cropX,
    uint32_t cropY,
    uint32_t cropWidth,
    uint32_t cropHeight,
    DecodedPixelmapResult *outResult
) {
    OH_ImageSourceNative *imageSource = nullptr;
    if (!CreateImageSourceFromFd(fd, &imageSource)) {
        return false;
    }
    const bool decoded = DecodePixelmapFromImageSource(
        imageSource, desiredWidth, desiredHeight, cropX, cropY, cropWidth, cropHeight, outResult);
    OH_ImageSourceNative_Release(imageSource);
    return decoded;
}

static bool DecodeToPixelmapFromFilePath(
    const char *path,
    uint32_t desiredWidth,
    uint32_t desiredHeight,
    uint32_t cropX,
    uint32_t cropY,
    uint32_t cropWidth,
    uint32_t cropHeight,
    DecodedPixelmapResult *outResult
) {
    OH_ImageSourceNative *imageSource = nullptr;
    if (!CreateImageSourceFromFilePath(path, &imageSource)) {
        return false;
    }
    const bool decoded = DecodePixelmapFromImageSource(
        imageSource, desiredWidth, desiredHeight, cropX, cropY, cropWidth, cropHeight, outResult);
    OH_ImageSourceNative_Release(imageSource);
    return decoded;
}

static bool PeekImageSizeFromFilePath(const char *path, uint32_t *outWidth, uint32_t *outHeight) {
    OH_ImageSourceNative *imageSource = nullptr;
    if (!CreateImageSourceFromFilePath(path, &imageSource)) {
        return false;
    }
    const bool ok = PeekImageSizeFromImageSource(imageSource, outWidth, outHeight);
    OH_ImageSourceNative_Release(imageSource);
    return ok;
}

static bool PeekImageSizeFromFd(int32_t fd, uint32_t *outWidth, uint32_t *outHeight) {
    OH_ImageSourceNative *imageSource = nullptr;
    if (!CreateImageSourceFromFd(fd, &imageSource)) {
        return false;
    }
    const bool ok = PeekImageSizeFromImageSource(imageSource, outWidth, outHeight);
    OH_ImageSourceNative_Release(imageSource);
    return ok;
}

static void *CreateDecodeBufferHandleFromResult(DecodedPixelmapResult *result) {
    auto *handle = new (std::nothrow) DecodeBufferHandle();
    if (handle == nullptr) {
        LOGE("CreateDecodeBufferHandleFromResult: OOM allocating handle");
        ReleaseDecodedPixelmapResult(result, true);
        return nullptr;
    }
    handle->pixelmap = result->pixelmap;
    handle->nativeBuffer = result->nativeBuffer;
    return handle;
}

} // namespace

static void LogNativeBufferConfig(OH_NativeBuffer *nativeBuffer, const char *label) {
    if (nativeBuffer == nullptr) {
        LOGE("DmaDiag[%{public}s]: nativeBuffer=null", label);
        return;
    }
    OH_NativeBuffer_Config config{};
    OH_NativeBuffer_GetConfig(nativeBuffer, &config);
    LOGE("DmaDiag[%{public}s]: nb w=%{public}d h=%{public}d stride=%{public}d format=%{public}d usage=%{public}llu",
         label, config.width, config.height, config.stride, config.format,
         static_cast<unsigned long long>(config.usage));
}

static void LogPixelmapImageInfo(OH_PixelmapNative *pixelmap, const char *label) {
    if (pixelmap == nullptr) {
        LOGE("DmaDiag[%{public}s]: pixelmap=null", label);
        return;
    }
    OH_Pixelmap_ImageInfo *imageInfo = nullptr;
    Image_ErrorCode err = OH_PixelmapImageInfo_Create(&imageInfo);
    if (err != IMAGE_SUCCESS || imageInfo == nullptr) {
        LOGE("DmaDiag[%{public}s]: PixelmapImageInfo_Create err=%{public}d", label, err);
        return;
    }
    err = OH_PixelmapNative_GetImageInfo(pixelmap, imageInfo);
    if (err != IMAGE_SUCCESS) {
        LOGE("DmaDiag[%{public}s]: GetImageInfo err=%{public}d", label, err);
        OH_PixelmapImageInfo_Release(imageInfo);
        return;
    }
    uint32_t width = 0U;
    uint32_t height = 0U;
    uint32_t rowStride = 0U;
    int32_t pixelFormat = 0;
    int32_t alphaType = 0;
    (void)OH_PixelmapImageInfo_GetWidth(imageInfo, &width);
    (void)OH_PixelmapImageInfo_GetHeight(imageInfo, &height);
    (void)OH_PixelmapImageInfo_GetRowStride(imageInfo, &rowStride);
    (void)OH_PixelmapImageInfo_GetPixelFormat(imageInfo, &pixelFormat);
    (void)OH_PixelmapImageInfo_GetAlphaType(imageInfo, &alphaType);
    OH_PixelmapImageInfo_Release(imageInfo);
    LOGE("DmaDiag[%{public}s]: pm w=%{public}u h=%{public}u stride=%{public}u format=%{public}d alpha=%{public}d",
         label, width, height, rowStride, pixelFormat, alphaType);
}

EXTERN_C_START
void *ARKUI_UTILS_EXPORT(decodeToSurfaceBuffer)(
    const uint8_t *data,
    size_t size,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
) {
    std::lock_guard<std::mutex> lock(gDecodeBufferHandleMutex);

    DecodedPixelmapResult result;
    if (!DecodeToPixelmapNative(
            data, size, desired_width, desired_height, crop_x, crop_y, crop_width, crop_height, &result)) {
        LOGE("decodeToSurfaceBuffer: decode failed");
        return nullptr;
    }
    // Reference → Release(pixelmap) → Unreference; caller holds GetNativeBuffer ref.
    auto refResult = OH_NativeBuffer_Reference(result.nativeBuffer);
    if (refResult != 0) {
        LOGE("decodeToSurfaceBuffer: native buffer reference failed ret=%{public}d", refResult);
        ReleaseDecodedPixelmapResult(&result, true);
        return nullptr;
    }
    OH_PixelmapNative_Release(result.pixelmap);
    result.pixelmap = nullptr;
    OH_NativeBuffer_Unreference(result.nativeBuffer);
    return reinterpret_cast<void *>(result.nativeBuffer);
}

void androidx_compose_ui_arkui_utils_releaseSurfaceBuffer(void *surfaceBuffer) {
    if (surfaceBuffer == nullptr) {
        return;
    }
    auto *nativeBuffer = reinterpret_cast<OH_NativeBuffer *>(surfaceBuffer);
    auto unrefResult = OH_NativeBuffer_Unreference(nativeBuffer);
    if (unrefResult != 0) {
        LOGE("releaseSurfaceBuffer: native buffer unreference failed ret=%{public}d", unrefResult);
    }
}

void androidx_compose_ui_arkui_utils_init(napi_env env, napi_value exports) {
    LOGD("androidx_compose_ui_arkui_utils_init: start");
    auto holder = androidx::compose::ui::arkui::utils::XComponentHolder::GetInstance();
    holder->InitXComponent(env, exports);
}
napi_value androidx_compose_ui_arkui_utils_wrapped(napi_env env, void *nativeController) {
    LOGD("androidx_compose_ui_arkui_utils_wrapped: start");
    return androidx::compose::ui::arkui::utils::ArkUIViewControllerWrapper::Wrapped(reinterpret_cast<napi_env>(env),
                                                                                    nativeController);
}

Boolean androidx_compose_ui_arkui_utils_xcomponent_prepareDraw(void *render) {
    auto xComponentRender = reinterpret_cast<androidx::compose::ui::arkui::utils::XComponentRender *>(render);
    return xComponentRender->EglPrepareDraw();
}

Boolean androidx_compose_ui_arkui_utils_xcomponent_finishDraw(void *render) {
    auto xComponentRender = reinterpret_cast<androidx::compose::ui::arkui::utils::XComponentRender *>(render);
    return xComponentRender->EglFinishDraw();
}

Boolean androidx_compose_ui_arkui_utils_xcomponent_isEglReady(void *render) {
    auto xComponentRender = reinterpret_cast<androidx::compose::ui::arkui::utils::XComponentRender *>(render);
    return xComponentRender->IsEglReady();
}

Boolean androidx_compose_ui_arkui_utils_xcomponent_releaseEGL(void *render) {
    LOGD("androidx_compose_ui_arkui_utils_xcomponent_releaseEGL render(%{public}p)", render);
    auto xComponentRender = reinterpret_cast<androidx::compose::ui::arkui::utils::XComponentRender *>(render);
    return xComponentRender->ReleaseEGL();
}

void androidx_compose_ui_arkui_utils_xcomponent_registerFrameCallback(void *render) {
    LOGD("androidx_compose_ui_arkui_utils_xcomponent_registerFrameCallback render(%{public}p)", render);
    auto xComponentRender = reinterpret_cast<androidx::compose::ui::arkui::utils::XComponentRender *>(render);
    xComponentRender->RegisterFrameCallback();
}

void androidx_compose_ui_arkui_utils_xcomponent_unregisterFrameCallback(void *render) {
    LOGD("androidx_compose_ui_arkui_xcomponent_c_unregisterFrameCallback render(%{public}p)", render);
    auto xComponentRender = reinterpret_cast<androidx::compose::ui::arkui::utils::XComponentRender *>(render);
    xComponentRender->UnregisterFrameCallback();
}

void androidx_compose_ui_arkui_utils_native_dvsync_set_enabled(void *render, int enable) {
    LOGI("androidx_compose_ui_arkui_utils_native_dvsync_set_enabled: render=%{public}p enable=%{public}d",
         render, enable);
    if (render == nullptr) {
        LOGI("androidx_compose_ui_arkui_utils_native_dvsync_set_enabled: render=null (no-op)");
        return;
    }
    auto *xComponentRender = reinterpret_cast<androidx::compose::ui::arkui::utils::XComponentRender *>(render);
    xComponentRender->SetNativeDvsyncEnabled(enable != 0);
}

int androidx_compose_ui_arkui_utils_getHarmonyApiVersion(void) {
    return OH_GetSdkApiVersion();
}

int androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(ArkUI_ContextHandle context, Boolean enable) {
    using SetUiDvsyncSwitchFn = int32_t (*)(ArkUI_ContextHandle, bool);
    static auto function = ResolveAceLibSymbol<SetUiDvsyncSwitchFn>("OH_ArkUI_NodeUtils_SetUiDvsyncSwitch");
    if (function == nullptr) {
        LOGE("OH_ArkUI_NodeUtils_SetUiDvsyncSwitch is unavailable");
        return -1;
    }
    return function(context, enable);
}

void androidx_compose_ui_arkui_utils_initHiAppEventXperfBridge(void) {
    compose::hiappevent::InitXperfBridge();
}

void *ARKUI_UTILS_EXPORT(decodeToDecodeBufferHandle)(
    const uint8_t *data,
    size_t size,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
) {
    std::lock_guard<std::mutex> lock(gDecodeBufferHandleMutex);

    if (data == nullptr || size == 0U) {
        LOGE("decodeToDecodeBufferHandle: invalid input data=%{public}p size=%{public}zu", data, size);
        return nullptr;
    }
    DecodedPixelmapResult result;
    if (!DecodeToPixelmapNative(
            data, size, desired_width, desired_height, crop_x, crop_y, crop_width, crop_height, &result)) {
        LOGE("decodeToDecodeBufferHandle: decode failed");
        return nullptr;
    }

    return CreateDecodeBufferHandleFromResult(&result);
}

void *androidx_compose_ui_arkui_utils_decodeBufferHandleGetNativeBuffer(void *handle) {
    if (handle == nullptr) {
        return nullptr;
    }
    return reinterpret_cast<DecodeBufferHandle *>(handle)->nativeBuffer;
}

void androidx_compose_ui_arkui_utils_logDecodeBufferHandleDiagnostics(void *handle) {
    if (handle == nullptr) {
        LOGE("DmaDiag[decodeHandle]: handle=null");
        return;
    }
    auto *h = reinterpret_cast<DecodeBufferHandle *>(handle);
    LogPixelmapImageInfo(h->pixelmap, "decodeHandle");
    LogNativeBufferConfig(h->nativeBuffer, "decodeHandle");
}

void androidx_compose_ui_arkui_utils_logNativeBufferDiagnostics(void *nativeBuffer) {
    LogNativeBufferConfig(reinterpret_cast<OH_NativeBuffer *>(nativeBuffer), "surfaceBuffer");
}

void androidx_compose_ui_arkui_utils_releaseDecodeBufferHandle(void *handle) {
    if (handle == nullptr) {
        return;
    }
    auto *h = reinterpret_cast<DecodeBufferHandle *>(handle);
    // §4.1 / Skiko pairing: drop compose-side native buffer ref; Skiko import holds its own Reference.
    if (h->nativeBuffer != nullptr) {
        auto unrefResult = OH_NativeBuffer_Unreference(h->nativeBuffer);
        if (unrefResult != 0) {
            LOGE("releaseDecodeBufferHandle: native buffer unreference failed ret=%{public}d", unrefResult);
        }
        h->nativeBuffer = nullptr;
    }
    if (h->pixelmap != nullptr) {
        OH_PixelmapNative_Release(h->pixelmap);
        h->pixelmap = nullptr;
    }
    delete h;
}

void *androidx_compose_ui_arkui_utils_decodeToDecodeBufferHandleFromRawFile(
    void *nativeResourceManager,
    const char *path,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
) {
    if (nativeResourceManager == nullptr || path == nullptr || path[0] == '\0') {
        LOGE("decodeToDecodeBufferHandleFromRawFile: invalid mgr/path");
        return nullptr;
    }
    DecodedPixelmapResult result;
    if (!DecodeToPixelmapFromRawFilePath(
            reinterpret_cast<NativeResourceManager *>(nativeResourceManager),
            path,
            desired_width,
            desired_height,
            crop_x,
            crop_y,
            crop_width,
            crop_height,
            &result)) {
        LOGE("decodeToDecodeBufferHandleFromRawFile: decode failed path=%{public}s", path);
        return nullptr;
    }
    return CreateDecodeBufferHandleFromResult(&result);
}

void *androidx_compose_ui_arkui_utils_decodeToSurfaceBufferFromRawFile(
    void *nativeResourceManager,
    const char *path,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
) {
    if (nativeResourceManager == nullptr || path == nullptr || path[0] == '\0') {
        LOGE("decodeToSurfaceBufferFromRawFile: invalid mgr/path");
        return nullptr;
    }
    DecodedPixelmapResult result;
    if (!DecodeToPixelmapFromRawFilePath(
            reinterpret_cast<NativeResourceManager *>(nativeResourceManager),
            path,
            desired_width,
            desired_height,
            crop_x,
            crop_y,
            crop_width,
            crop_height,
            &result)) {
        LOGE("decodeToSurfaceBufferFromRawFile: decode failed path=%{public}s", path);
        return nullptr;
    }
    auto refResult = OH_NativeBuffer_Reference(result.nativeBuffer);
    if (refResult != 0) {
        LOGE("decodeToSurfaceBufferFromRawFile: native buffer reference failed ret=%{public}d", refResult);
        ReleaseDecodedPixelmapResult(&result, true);
        return nullptr;
    }
    OH_PixelmapNative_Release(result.pixelmap);
    result.pixelmap = nullptr;
    OH_NativeBuffer_Unreference(result.nativeBuffer);
    return reinterpret_cast<void *>(result.nativeBuffer);
}

int androidx_compose_ui_arkui_utils_peekImageSizeFromRawFile(
    void *nativeResourceManager,
    const char *path,
    uint32_t *out_width,
    uint32_t *out_height
) {
    if (nativeResourceManager == nullptr || path == nullptr || path[0] == '\0' ||
        out_width == nullptr || out_height == nullptr) {
        return 0;
    }
    OH_ImageSourceNative *imageSource = nullptr;
    if (!CreateImageSourceFromRawFilePath(reinterpret_cast<NativeResourceManager *>(nativeResourceManager), path,
                                          &imageSource)) {
        return 0;
    }
    const bool ok = PeekImageSizeFromImageSource(imageSource, out_width, out_height);
    OH_ImageSourceNative_Release(imageSource);
    return ok ? 1 : 0;
}

int androidx_compose_ui_arkui_utils_peekImageSizeFromData(
    const uint8_t *data,
    size_t size,
    uint32_t *out_width,
    uint32_t *out_height
) {
    if (data == nullptr || size == 0U || out_width == nullptr || out_height == nullptr) {
        return 0;
    }
    std::vector<uint8_t> dataCopy(size);
    memcpy(dataCopy.data(), data, size);

    OH_ImageSourceNative *imageSource = nullptr;
    Image_ErrorCode err = OH_ImageSourceNative_CreateFromData(dataCopy.data(), size, &imageSource);
    if (err != IMAGE_SUCCESS || imageSource == nullptr) {
        if (imageSource != nullptr) {
            OH_ImageSourceNative_Release(imageSource);
        }
        return 0;
    }
    const bool ok = PeekImageSizeFromImageSource(imageSource, out_width, out_height);
    OH_ImageSourceNative_Release(imageSource);
    return ok ? 1 : 0;
}

void *androidx_compose_ui_arkui_utils_decodeToDecodeBufferHandleFromFd(
    int32_t fd,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
) {
    if (fd < 0) {
        LOGE("decodeToDecodeBufferHandleFromFd: invalid fd=%{public}d", fd);
        return nullptr;
    }
    DecodedPixelmapResult result;
    if (!DecodeToPixelmapFromFd(fd, desired_width, desired_height, crop_x, crop_y, crop_width, crop_height, &result)) {
        LOGE("decodeToDecodeBufferHandleFromFd: decode failed fd=%{public}d", fd);
        return nullptr;
    }
    return CreateDecodeBufferHandleFromResult(&result);
}

void *androidx_compose_ui_arkui_utils_decodeToDecodeBufferHandleFromFile(
    const char *path,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
) {
    if (path == nullptr || path[0] == '\0') {
        LOGE("decodeToDecodeBufferHandleFromFile: invalid path");
        return nullptr;
    }
    DecodedPixelmapResult result;
    if (!DecodeToPixelmapFromFilePath(
            path, desired_width, desired_height, crop_x, crop_y, crop_width, crop_height, &result)) {
        LOGE("decodeToDecodeBufferHandleFromFile: decode failed path=%{public}s", path);
        return nullptr;
    }
    return CreateDecodeBufferHandleFromResult(&result);
}

void *androidx_compose_ui_arkui_utils_decodeToSurfaceBufferFromFd(
    int32_t fd,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
) {
    if (fd < 0) {
        LOGE("decodeToSurfaceBufferFromFd: invalid fd=%{public}d", fd);
        return nullptr;
    }
    DecodedPixelmapResult result;
    if (!DecodeToPixelmapFromFd(fd, desired_width, desired_height, crop_x, crop_y, crop_width, crop_height, &result)) {
        LOGE("decodeToSurfaceBufferFromFd: decode failed fd=%{public}d", fd);
        return nullptr;
    }
    auto refResult = OH_NativeBuffer_Reference(result.nativeBuffer);
    if (refResult != 0) {
        LOGE("decodeToSurfaceBufferFromFd: native buffer reference failed ret=%{public}d", refResult);
        ReleaseDecodedPixelmapResult(&result, true);
        return nullptr;
    }
    OH_PixelmapNative_Release(result.pixelmap);
    result.pixelmap = nullptr;
    OH_NativeBuffer_Unreference(result.nativeBuffer);
    return reinterpret_cast<void *>(result.nativeBuffer);
}

void *androidx_compose_ui_arkui_utils_decodeToSurfaceBufferFromFile(
    const char *path,
    uint32_t desired_width,
    uint32_t desired_height,
    uint32_t crop_x,
    uint32_t crop_y,
    uint32_t crop_width,
    uint32_t crop_height
) {
    if (path == nullptr || path[0] == '\0') {
        LOGE("decodeToSurfaceBufferFromFile: invalid path");
        return nullptr;
    }
    DecodedPixelmapResult result;
    if (!DecodeToPixelmapFromFilePath(
            path, desired_width, desired_height, crop_x, crop_y, crop_width, crop_height, &result)) {
        LOGE("decodeToSurfaceBufferFromFile: decode failed path=%{public}s", path);
        return nullptr;
    }
    auto refResult = OH_NativeBuffer_Reference(result.nativeBuffer);
    if (refResult != 0) {
        LOGE("decodeToSurfaceBufferFromFile: native buffer reference failed ret=%{public}d", refResult);
        ReleaseDecodedPixelmapResult(&result, true);
        return nullptr;
    }
    OH_PixelmapNative_Release(result.pixelmap);
    result.pixelmap = nullptr;
    OH_NativeBuffer_Unreference(result.nativeBuffer);
    return reinterpret_cast<void *>(result.nativeBuffer);
}

int androidx_compose_ui_arkui_utils_peekImageSizeFromFd(
    int32_t fd,
    uint32_t *out_width,
    uint32_t *out_height
) {
    if (fd < 0 || out_width == nullptr || out_height == nullptr) {
        return 0;
    }
    return PeekImageSizeFromFd(fd, out_width, out_height) ? 1 : 0;
}

int androidx_compose_ui_arkui_utils_peekImageSizeFromFile(
    const char *path,
    uint32_t *out_width,
    uint32_t *out_height
) {
    if (path == nullptr || path[0] == '\0' || out_width == nullptr || out_height == nullptr) {
        return 0;
    }
    return PeekImageSizeFromFilePath(path, out_width, out_height) ? 1 : 0;
}

int androidx_compose_ui_arkui_utils_decodeToDmaSurfaceBuffer(
    const uint8_t *data,
    size_t size,
    const OhosImageDecodeParams *params,
    OhosDmaDecodeOutput *output
) {
    const DmaDecodeSource source{data, size, nullptr, nullptr};
    return DecodeToDmaSurfaceBufferInternal(source, params, output);
}

int androidx_compose_ui_arkui_utils_decodeRawFileToDmaSurfaceBuffer(
    void *nativeResourceManager,
    const char *path,
    const OhosImageDecodeParams *params,
    OhosDmaDecodeOutput *output
) {
    const DmaDecodeSource source{
        nullptr,
        0U,
        reinterpret_cast<NativeResourceManager *>(nativeResourceManager),
        path,
    };
    return DecodeToDmaSurfaceBufferInternal(source, params, output);
}

int androidx_compose_ui_arkui_utils_hiAppEventReportFrameDrop(
    const char *domain,
    const char *eventName,
    const char *traceName,
    const char *controllerId,
    int droppedFrameCount,
    int longFrameCount,
    long long durationMs,
    double avgFps
) {
    const char *safeDomain = (domain != nullptr && domain[0] != '\0') ? domain : "CMP_COMPOSE";
    const char *safeEvent = (eventName != nullptr && eventName[0] != '\0') ? eventName : "FRAME_DROP";
    const char *safeTraceName = (traceName != nullptr) ? traceName : "";
    const char *safeControllerId = (controllerId != nullptr) ? controllerId : "";

    ParamList list = OH_HiAppEvent_CreateParamList();
    if (list == nullptr) {
        LOGE("HiAppEvent report failed: CreateParamList returned null.");
        return static_cast<int>(HIAPPEVENT_INVALID_PARAM_VALUE);
    }

    OH_HiAppEvent_AddStringParam(list, "trace_name", safeTraceName);
    OH_HiAppEvent_AddStringParam(list, "controller_id", safeControllerId);
    OH_HiAppEvent_AddInt32Param(list, "dropped_frame_count", droppedFrameCount);
    OH_HiAppEvent_AddInt32Param(list, "long_frame_count", longFrameCount);
    OH_HiAppEvent_AddInt64Param(list, "duration_ms", static_cast<int64_t>(durationMs));
    OH_HiAppEvent_AddDoubleParam(list, "avg_callback_fps", avgFps);

    const int writeResult = compose::hiappevent::WritePerformanceEvent(
        safeDomain,
        safeEvent,
        [&safeDomain, &safeEvent, &list]() {
            return OH_HiAppEvent_Write(safeDomain, safeEvent, BEHAVIOR, list);
        });
    OH_HiAppEvent_DestroyParamList(list);

    if (writeResult != HIAPPEVENT_SUCCESS) {
        LOGE("HiAppEvent write failed. domain=%{public}s event=%{public}s code=%{public}d",
             safeDomain, safeEvent, writeResult);
    } else {
        LOGD("HiAppEvent write success. domain=%{public}s event=%{public}s dropped=%{public}d long=%{public}d",
             safeDomain, safeEvent, droppedFrameCount, longFrameCount);
    }
    return writeResult;
}

int androidx_compose_ui_arkui_utils_hiAppEventReportScrollJank(
    const char *frameworkName,
    int versionCode,
    long long startTimeMillis,
    long long endTimeMillis,
    long long maxFrameTimeMillis,
    int maxMissedFrameRate,
    int totalMissedFrames,
    int totalFrames,
    const char *pageName,
    int recentScrollCount,
    const char *scrollPhase,
    int pid
) {
    const char *safeFramework = (frameworkName != nullptr && frameworkName[0] != '\0') ? frameworkName : "CMP";
    const char *safePage = (pageName != nullptr) ? pageName : "";
    const char *safePhase = (scrollPhase != nullptr && scrollPhase[0] != '\0') ? scrollPhase : "scroll_other";

    ParamList list = OH_HiAppEvent_CreateParamList();
    if (list == nullptr) {
        LOGE("HiAppEvent scroll jank: CreateParamList returned null.");
        return static_cast<int>(HIAPPEVENT_INVALID_PARAM_VALUE);
    }

    OH_HiAppEvent_AddStringParam(list, "frameworkName", safeFramework);
    OH_HiAppEvent_AddInt32Param(list, "versionCode", versionCode);
    OH_HiAppEvent_AddInt64Param(list, "startTime", startTimeMillis);
    OH_HiAppEvent_AddInt64Param(list, "endTime", endTimeMillis);
    OH_HiAppEvent_AddInt64Param(list, "maxFrameTime", maxFrameTimeMillis);
    OH_HiAppEvent_AddInt32Param(list, "maxMissedFrameRate", maxMissedFrameRate);
    OH_HiAppEvent_AddInt32Param(list, "totalMissedFrames", totalMissedFrames);
    OH_HiAppEvent_AddInt32Param(list, "totalFrames", totalFrames);
    OH_HiAppEvent_AddStringParam(list, "pageName", safePage);
    OH_HiAppEvent_AddInt32Param(list, "recentScrollCount", recentScrollCount);
    OH_HiAppEvent_AddStringParam(list, "scrollPhase", safePhase);
    const int effectivePid = pid != 0 ? pid : static_cast<int>(getpid());
    OH_HiAppEvent_AddInt32Param(list, "pid", effectivePid);

    constexpr const char *kDomain = "PERFORMANCE";
    constexpr const char *kEvent = "OTHER_SCROLL_JANK";
    const int writeResult = compose::hiappevent::WritePerformanceEvent(
        kDomain,
        kEvent,
        [&kDomain, &kEvent, &list]() {
            return OH_HiAppEvent_Write(kDomain, kEvent, BEHAVIOR, list);
        });
    OH_HiAppEvent_DestroyParamList(list);

    if (writeResult != HIAPPEVENT_SUCCESS) {
        LOGE("HiAppEvent scroll jank write failed. code=%{public}d", writeResult);
    } else {
        LOGD("HiAppEvent scroll jank ok. phase=%{public}s missed=%{public}d frames=%{public}d",
             safePhase, totalMissedFrames, totalFrames);
    }
    return writeResult;
}

void ARKUI_UTILS_EXPORT(registerAccessibilityNodeHandle)(const char *composeId, void *nodeHandle) {
    RegisterNodeHandleWithSource(
        composeId,
        nodeHandle,
        NODE_HANDLE_SOURCE_ARKTS_FRAME_NODE);
}

void ARKUI_UTILS_EXPORT(registerFusionAccessibilityNodeHandle)(const char *composeId, void *nodeHandle) {
    RegisterNodeHandleWithSource(
        composeId,
        nodeHandle,
        NODE_HANDLE_SOURCE_FUSION_RENDER_NODE);
}

void ARKUI_UTILS_EXPORT(registerFusionAccessibilityRenderNode)(
    const char *composeId,
    long long renderNodePtr) {
    if (composeId == nullptr) {
        LOGE("registerFusionRenderNode invalid composeId=%{public}p", composeId);
        return;
    }
    if (renderNodePtr == 0) {
        LOGE("registerFusionRenderNode invalid composeId=%{public}p renderNodePtr=0", composeId);
        return;
    }
    auto fn = ResolveGetRenderNodeHandle();
    if (fn == nullptr) {
        LOGW("registerFusionRenderNode node handle function unavailable composeId=%{public}s", composeId);
        return;
    }
    auto renderNode = reinterpret_cast<void *>(static_cast<intptr_t>(renderNodePtr));
    auto nodeHandle = fn(renderNode);
    RegisterNodeHandleWithSource(
        composeId,
        nodeHandle,
        NODE_HANDLE_SOURCE_FUSION_RENDER_NODE);
}

void ARKUI_UTILS_EXPORT(unregisterAccessibilityNodeHandle)(const char *composeId) {
    if (composeId == nullptr) {
        return;
    }
    ArkUiAccessibilityProvider *ownedProvider = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_nodeHandlesMutex);
        g_nodeHandles.erase(composeId);
        TakeFallbackProviderLocked(composeId, &ownedProvider);
    }
    DisposeFallbackProvider(composeId, ownedProvider);
    LOGD("unregisterAccessibilityNodeHandle composeId=%{public}s", composeId);
}

void *ARKUI_UTILS_EXPORT(accessibilityProviderFromComposeId)(const char *composeId) {
    if (composeId == nullptr) {
        LOGE("accessibilityProviderFromComposeId composeId is null");
        return nullptr;
    }
    void *nodeHandle = FindNodeHandle(composeId);
    if (nodeHandle == nullptr) {
        LOGW("accessibilityProviderFromComposeId missing composeId=%{public}s", composeId);
        return nullptr;
    }

    auto fallbackProvider = FindFallbackProvider(composeId);
    if (fallbackProvider != nullptr) {
        return fallbackProvider;
    }

    auto handle = reinterpret_cast<ArkUI_NodeHandle>(nodeHandle);
    ArkUiAccessibilityProvider *provider = GetNativeProvider(composeId, handle);
    if (provider == nullptr) {
        provider = CreateFallbackProvider(composeId, handle);
    }
    return provider;
}

int ARKUI_UTILS_EXPORT(setThreadQoS)(int level) {
    return OH_QoS_SetThreadQoS(static_cast<QoS_Level>(level));
}

int ARKUI_UTILS_EXPORT(notifyPageDpi)(const char *pageName, int32_t pageNameLength) {
    return androidx::compose::ui::arkui::utils::PageDpiNotifier_notifyPageChanged(pageName, pageNameLength);
}

void ARKUI_UTILS_EXPORT(setPageDpiWindowId)(int32_t windowId) {
    androidx::compose::ui::arkui::utils::PageDpiNotifier_setWindowId(windowId);
}

void ARKUI_UTILS_EXPORT(clearPageDpiWindowId)() {
    androidx::compose::ui::arkui::utils::PageDpiNotifier_clearWindowId();
}

int ARKUI_UTILS_EXPORT(isPageDpiAvailable)() {
    return androidx::compose::ui::arkui::utils::PageDpiNotifier_isAvailable() ? 1 : 0;
}

EXTERN_C_END

#undef ARKUI_UTILS_EXPORT
