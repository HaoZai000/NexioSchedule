package com.haooz.chedule.data.platform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSUUID
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.size_tVar

// sysctl 系列已从 Apple 平台库移除且直接绑头文件会被弃用标注滤掉，
// 由自定义 cinterop 的 static inline 包装（apple-sysctl.def）暴露
import applesysctl.nexio_sysctlbyname

/**
 * iOS 实现：用 `sysctl` 读取真实机型与系统版本。
 *
 * 刻意不走 `UIDevice`——统计上报跑在 IO 线程，UIKit 的 currentDevice 属主线程 API，
 * 而且被裁剪过的 native target 也要能编。纯 posix 读取，线程安全：
 * - `hw.machine` → 机型标识（如 "iPhone16,2"，与 Android 侧 `Build.MODEL` 同为标识符口径）
 * - `kern.osproductversion` → 系统版本（如 "26.0"，对应 `Build.VERSION.RELEASE`）
 *
 * `brand` / `manufacturer` 恒为 "Apple"：iOS 生态没有厂商之分，写死即可。
 * `sdkLevel` 恒为 0 —— 它是 Android `Build.VERSION.SDK_INT` 的语义。
 */
@OptIn(ExperimentalForeignApi::class)
private fun sysctlString(name: String): String? = memScoped {
    val size = alloc<size_tVar>()
    if (nexio_sysctlbyname(name, null, size.ptr) != 0) return null
    if (size.value == 0uL) return null
    val buffer = allocArray<ByteVar>(size.value.toInt())
    if (nexio_sysctlbyname(name, buffer, size.ptr) != 0) return null
    buffer.toKString().takeIf { it.isNotBlank() }
}

actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    model = sysctlString("hw.machine") ?: "unknown",
    brand = "Apple",
    manufacturer = "Apple",
    osVersion = sysctlString("kern.osproductversion") ?: "unknown",
    sdkLevel = 0,
)

/**
 * Keychain 中保存的随机 UUID。
 *
 * iOS 的 `identifierForVendor` 在删光同厂商应用后会重置，无法跨卸载重装；
 * Keychain 条目默认在卸载后保留，因此首次生成后写入 Keychain，之后直接复用。
 * `AfterFirstUnlockThisDeviceOnly` 不随 iCloud 备份迁移到新设备。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun stableDeviceId(): String {
    val service = "icu.nexioschedule.device"
    val account = "device-id"

    val cfService = CFStringCreateWithCString(kCFAllocatorDefault, service, kCFStringEncodingUTF8)
    val cfAccount = CFStringCreateWithCString(kCFAllocatorDefault, account, kCFStringEncodingUTF8)
    val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
    if (cfService == null || cfAccount == null || query == null) return ""
    try {
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, cfService)
        CFDictionaryAddValue(query, kSecAttrAccount, cfAccount)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val existing = memScoped {
            val result = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(query, result.ptr) == errSecSuccess) {
                val data = result.value as CFDataRef
                val length = CFDataGetLength(data).toInt()
                val bytes = CFDataGetBytePtr(data)
                val text = if (length > 0 && bytes != null) {
                    bytes.reinterpret<ByteVar>().readBytes(length).decodeToString()
                } else {
                    null
                }
                data?.let { CFRelease(it) }
                text
            } else {
                null
            }
        }
        if (!existing.isNullOrBlank()) return existing
    } finally {
        cfService?.let { CFRelease(it) }
        cfAccount?.let { CFRelease(it) }
        query?.let { CFRelease(it) }
    }

    val id = NSUUID().UUIDString
    val bytes = id.encodeToByteArray()
    val cfId = bytes.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
    }
    val addService = CFStringCreateWithCString(kCFAllocatorDefault, service, kCFStringEncodingUTF8)
    val addAccount = CFStringCreateWithCString(kCFAllocatorDefault, account, kCFStringEncodingUTF8)
    val addQuery = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
    if (addService == null || addAccount == null || addQuery == null) return id
    try {
        CFDictionaryAddValue(addQuery, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(addQuery, kSecAttrService, addService)
        CFDictionaryAddValue(addQuery, kSecAttrAccount, addAccount)
        CFDictionaryAddValue(addQuery, kSecValueData, cfId)
        CFDictionaryAddValue(
            addQuery,
            kSecAttrAccessible,
            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        SecItemAdd(addQuery, null)
    } finally {
        addService?.let { CFRelease(it) }
        addAccount?.let { CFRelease(it) }
        cfId?.let { CFRelease(it) }
        addQuery?.let { CFRelease(it) }
    }
    return id
}
