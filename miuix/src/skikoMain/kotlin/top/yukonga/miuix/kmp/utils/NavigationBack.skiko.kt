package top.yukonga.miuix.kmp.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Skia（iOS / Desktop / wasm）实现 —— CMP 1.12 **没有** predictive back 的公共 API。
 *
 * 核查结论（`ui-backhandler-desktop-1.12.0` 只有 .pom/.module，无实现类；
 * material3 里的 `PredictiveBackState` / `BackHandler_skikoKt` 全是 `internal`）：
 * 上游 Miuix 0.9.3 自己也只在 androidMain 用 `androidx.navigationevent`，
 * commonMain 里没有任何对应物 —— 所以这里**不存在**可对接的跨平台实现。
 *
 * 语义对齐 navigationevent：终态「确认返回」→ onBackCompleted，「取消」→ onBackCancelled。
 *
 * handler 为空实现（不注册任何手势，**不会误触发**）：iOS 的系统返回手势由宿主 UIKit
 * 接管，Kotlin 侧拿不到事件。将来在 iOS 入口层接上系统手势后，只需替换这里的
 * `onHandler`，与 Android 侧同形 —— 调用点（DialogContentLayout /
 * BottomSheetContentLayout / ListPopupLayout）无需再改。
 */
@Composable
actual fun rememberNavigationBack(): NavigationBackState = remember {
    NavigationBackState(
        // 非 Android 侧没有跨帧进度源：保守报告「不在转场」「无进度」。
        // 调用方的状态机仍可只靠 onBackCompleted 正常关闭，不会误判。
        isTransitioningBack = { false },
        backProgress = { null },
        onHandler = {},
    )
}