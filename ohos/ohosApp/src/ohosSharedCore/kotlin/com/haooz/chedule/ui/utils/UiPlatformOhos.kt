package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import com.haooz.nexio.ohos.OhosEnv
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * `ui-shared` 平台抽象在鸿蒙端（ohosArm64）的实现。
 *
 * 口径：**能拿到真值的就给真值，拿不到的降级成「零/空」并写清后果** —— 不假装支持。
 * 参照的是 iOS / 桌面侧的同类实现（native 端没有 Activity/LocalViewModelStoreOwner 那一套）。
 */

/**
 * 当前平台。
 *
 * `AppPlatform` 只有 Android / Ios / Desktop 三值，鸿蒙是「三者之外的 native 宿主」。
 * 取 [AppPlatform.Desktop] 是因为它代表「没有 Android 专属语义、也没有 iOS 原生 chrome 接管」，
 * 与鸿蒙当前形态最接近。
 *
 * ⚠ 目前 UI 层没有任何调用点（`currentAppPlatform()` 全仓库只在定义处出现），
 * 所以这个取值暂时无行为影响；将来真要按平台分叉时，应该给枚举加 `Ohos` 值而不是复用别的。
 */
actual fun currentAppPlatform(): AppPlatform = AppPlatform.Desktop

/** 窗口宽度（dp）。与 iOS/桌面同口径：窗口像素 ÷ 密度。 */
@Composable
actual fun screenWidthDp(): Int {
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val density = LocalDensity.current.density
    val widthDp = if (density > 0f) widthPx / density else widthPx
    return widthDp.toInt()
}

/** 窗口高度（dp）。同 [screenWidthDp]。 */
@Composable
actual fun screenHeightDp(): Int {
    val heightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val density = LocalDensity.current.density
    val heightDp = if (density > 0f) heightPx / density else heightPx
    return heightDp.toInt()
}

/**
 * 屏幕物理圆角（px）。
 *
 * 数据来自 ArkTS 的 `Display.getRoundedCorner()`（API 17+，单位 px，无需权限），
 * 经 `setAppEnv` 传进来存在 [OhosEnv.screenCornerRadiusPx]。
 *
 * 与 Android/iOS 的差别只有一点：Android 能拿到「每个角各自的值」，
 * 鸿蒙这个 API 只给一个半径（API 23+ 的 `RoundedCorner[]` 才有分角数据，
 * 我们这一档 SDK 拿不到），所以四角按同一个值填。
 * 折叠屏/外接屏这类四角确实不同的场景会有细微差别，但常规直板机完全一致。
 *
 * 无圆角设备（外接显示器、部分模拟器）返回 0 = 不裁切；消费方的 `coerceAtLeast`
 * 会兜底出最小圆角，不会退化成硬直角。
 */
@Composable
actual fun rememberScreenCornerRadiiPx(): ScreenCornerRadiiPx {
    val radiusPx = OhosEnv.screenCornerRadiusPx.toFloat()
    return remember(radiusPx) {
        if (radiusPx > 0f) ScreenCornerRadiiPx.uniform(radiusPx) else ScreenCornerRadiiPx.Zero
    }
}

/**
 * 收起原生键盘并清焦点。
 *
 * 空实现：鸿蒙的输入法由 ArkUI 管，Compose 里的 TextField 还没接原生 IME 桥
 * （等 `NexioApp` 真跑表单页时再接 ArkTS 的 `inputMethod`）。
 * 返回稳定的空 lambda，避免每次重组换实例导致下游 `LaunchedEffect` 重跑。
 */
@Composable
actual fun rememberNativeImeDismiss(): () -> Unit = remember { {} }

/**
 * 带原生 chrome 的屏幕快照。
 *
 * 返回 null = 「没有原生 chrome 需要额外录」，调用方沿用 Compose 自己录的 `GraphicsLayer` 快照 ——
 * 这正是 Android/桌面的口径。鸿蒙没有 ArkTS 层的外壳视图，语义与它们一致。
 */
actual fun captureScreenWithNativeChrome(): ImageBitmap? = null

/**
 * ImageBitmap ↔ PNG。
 *
 * 降级为 null：iOS/桌面走 `org.jetbrains.skia` 的编码器，而鸿蒙这套 ui-ohos klib 没有公开
 * 对应的 Compose 桥（同 `asComposeShader` 的情况）。用到的场景是「壁纸/截图导出」，
 * 拿 null 时调用方会退回到不导出图片，不会崩。
 */
internal actual fun encodeImageBitmapPng(image: ImageBitmap): ByteArray? = null

/** 同 [encodeImageBitmapPng]，降级为 null。 */
internal actual fun decodeImageBitmapPng(bytes: ByteArray): ImageBitmap? = null
