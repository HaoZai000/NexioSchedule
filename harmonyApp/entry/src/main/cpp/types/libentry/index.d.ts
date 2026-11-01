import { ArkUIViewController } from 'libcompose_arkui_utils.so';

/**
 * Kotlin 侧 @CName("MainArkUIViewController") 的实现：
 * 返回一个 controller 句柄，交给 @cpf-kmp-cmp/compose 的 Compose 组件去渲染。
 */
export const MainArkUIViewController: () => ArkUIViewController;

/**
 * 把运行环境交给 Kotlin/Native 侧（Kotlin 拿不到 UIAbilityContext）。
 *
 * @param filesDir  应用私有目录 `context.filesDir`（壁纸/缓存/设置都落在这里）
 * @param screenW   屏幕宽度（px，来自 `display.getDefaultDisplaySync()`）
 * @param screenH   屏幕高度（px）
 * @param cornerRadiusPx 屏幕圆角半径（px，`Display.getRoundedCorner()`；无圆角为 0）
 *
 * 必须在 `MainArkUIViewController()` 之前调用：壁纸是首帧同步读取的。
 */
/**
 * 注册壁纸选图回调（ArkTS 侧实现系统选图器）。
 *
 * UI 里的「选壁纸」在 Kotlin 侧，而 `photoAccessHelper.PhotoViewPicker` 只能由 ArkTS 起，
 * 所以启动时把 picker 注册进来，Kotlin 需要时反着触发；选完用 [setWallpaperBytes] 回传像素。
 */
export const registerWallpaperPicker: (picker: () => void) => void;

/**
 * 选图结果回传（Kotlin 侧解码 + 落盘）。传空数组表示取消/失败。
 */
export const setWallpaperBytes: (bytes: Uint8Array) => void;

export const setAppEnv: (
  filesDir: string,
  screenW: number,
  screenH: number,
  cornerRadiusPx: number
) => void;
