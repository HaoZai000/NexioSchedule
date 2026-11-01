package com.haooz.nexio.ohos

import androidx.compose.ui.graphics.ImageBitmap
import com.haooz.chedule.data.storage.AppStorage
import com.haooz.chedule.data.storage.CourseStorage
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import org.jetbrains.skia.Image

/**
 * 壁纸选图的双向桥。
 *
 * ```
 * UI（:ui-shared）-> PlatformWallpaperPicker -> requestPick()
 *   -> C++ HostRequestWallpaperPick -> ArkTS 系统选图器
 *   -> setWallpaperBytes -> NexioSetWallpaperBytes -> 这里解码 -> 回调 UI
 * ```
 *
 * ### 为什么是「C++ 把函数指针交给 Kotlin」而不是 Kotlin 调 C 符号
 *
 * `libentry.so` 依赖 `libnexioohos.so`，反方向没有 DT_NEEDED，所以 Kotlin 侧
 * `external fun NexioRequestWallpaperPick()` 运行时解析不到那个符号（@SymbolName
 * 又是内部弃用注解，不给用）。函数指针不经过动态符号表，纯地址调用。
 */
@OptIn(ExperimentalForeignApi::class)
private object WallpaperPickBridge {

    /** 宿主（C++）的「起选图器」入口；ArkTS 调 `registerWallpaperPicker` 之前为 null。 */
    private var hostPick: (() -> Unit)? = null

    /** 当前待回传的选图请求。 */
    private var pending: ((ImageBitmap?) -> Unit)? = null

    fun onHostReady(fn: COpaquePointer?) {
        if (fn == null) return
        val cFunction: CPointer<CFunction<() -> Unit>> = fn.reinterpret<CFunction<() -> Unit>>()
        hostPick = { cFunction.invoke() }
    }

    fun request(onResult: (ImageBitmap?) -> Unit) {
        val pick = hostPick
        if (pick == null) {
            // 原生侧还没就绪：立刻以「取消」收尾，别让 UI 卡在等待态
            onResult(null)
            return
        }
        pending = onResult
        pick()
    }

    /** 选图结果；空字节数组 = 用户取消或读取失败。 */
    fun onResult(bytes: ByteArray) {
        val callback = pending ?: return
        pending = null
        val bitmap = if (bytes.isEmpty()) null else runCatching {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
        callback(bitmap)
    }
}

actual val PlatformWallpaperPicker: (((ImageBitmap?) -> Unit) -> Unit)? = { onResult ->
    WallpaperPickBridge.request(onResult)
}

/** C++ 在模块注册时把自己的选图入口交过来（见 `napi_init.cpp`）。 */
@OptIn(ExperimentalForeignApi::class)
@CName("NexioOnWallpaperPickHostReady")
fun NexioOnWallpaperPickHostReady(fn: COpaquePointer?) {
    WallpaperPickBridge.onHostReady(fn)
}

/**
 * ArkTS 回传选图字节；签名对应 C 侧 `void(const uint8_t*, int32_t)`。
 * @param data 可为 null；[length] 为 0 表示取消/失败。
 */
@OptIn(ExperimentalForeignApi::class)
@CName("NexioSetWallpaperBytes")
fun NexioSetWallpaperBytes(data: CPointer<ByteVar>?, length: Int) {
    val bytes = if (data == null || length <= 0) ByteArray(0) else data.readBytes(length)
    WallpaperPickBridge.onResult(bytes)
}

/**
 * 存储装配：与 Android / iOS 同构（同一个 `nexio.db`、同一份 schema）。
 *
 * ⚠ 必须在 `NexioRoot` 之前跑：`AppStorage.store()` 一旦被调用就会缓存实例，
 * 之后再 init 等于换了一套存储，旧缓存还在用。
 */
actual fun installPlatformStorage() {
    if (!AppStorage.isReady) {
        AppStorage.init { namespace -> OhosSqlStorage.store(namespace) }
    }
    CourseStorage.install { OhosSqlStorage.courseRows() }
}
