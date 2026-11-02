import { ArkUIViewController } from 'libcompose_arkui_utils.so';

/**
 * Kotlin 侧 @CName("MainArkUIViewController") 的实现：
 * 返回一个 controller 句柄，交给 @cpf-kmp-cmp/compose 的 Compose 组件去渲染。
 */
export const MainArkUIViewController: () => ArkUIViewController;

/**
 * 第二个 Compose 实例：二级页（设置等）住在这一层。
 *
 * 一个 XComponent 只有一层，画在里面的东西盖不住 ArkUI 的兄弟节点；把子页拆到独立实例后，
 * ArkUI 只要把这一层排在顶栏/底栏之后，设置页就是真的压在它们上面。
 */
export const OverlayArkUIViewController: () => ArkUIViewController;

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

/**
 * 注册 HTTP 发送器。NDK 里没有 curl，网络走 ArkTS 的 `@ohos.net.http`。
 *
 * Kotlin 侧发起请求时回调这里；实现必须**异步**，完成后用 [setHttpResult] 回传。
 *
 * @param sender (requestId, payloadJson, maxBytes) => void
 *   payloadJson: `{ method, url, headers, body?, contentType?, connectTimeout, readTimeout }`
 */
export const registerHttpSender: (
  sender: (requestId: number, payloadJson: string, maxBytes: number) => void
) => void;

/**
 * HTTP 结果回传。`code <= 0` 表示失败，此时 `bytes` 是错误信息。
 */
export const setHttpResult: (
  requestId: number,
  code: number,
  bytes: Uint8Array,
  truncated: boolean
) => void;

/**
 * 注册提示条（Kotlin 的 `showToast` → ArkTS `promptAction.showToast`）。
 * 不注册的话所有错误提示都会被静默丢弃。
 */
export const registerToast: (
  toast: (message: string, longDuration: boolean) => void
) => void;

/**
 * 顶栏语义：Compose 发布，ArkTS 用系统组件渲染。
 *
 * 宿主顶栏走 `Navigation` 的标题栏（`title`）与官方沉浸光感材质
 * （`uiMaterial.ImmersiveMaterial`），不再自绘模糊 —— JSON 字段与 Kotlin 的
 * `NativeTopBarState` 一一对应，`titleHeightDp` 是**不含状态栏**的标题栏高度，
 * `statusBarDp` 由宿主自己补到标题栏顶端（实测标题栏从窗口 y=0 开始排，不吃安全区）。
 */
export const registerTopBar: (
  publish: (json: string) => void
) => void;

/** 顶栏按钮 / 菜单动作回传给 Compose（"switchSchedule" / "more" / "jumpWeek" / ...）。 */
export const topBarAction: (id: string) => void;

/**
 * 底部导航语义：Compose 发布，ArkTS 用 `HdsTabs` 的悬浮页签胶囊渲染。
 *
 * JSON 字段与 Kotlin 的 `NativeBottomBarState` 一一对应：`selected` 是当前格，
 * `tabsCount` 是页签格数（1 = 排班模式），设置格不算在内。
 */
export const registerBottomBar: (
  publish: (json: string) => void
) => void;

/** 点了某格；`index >= tabsCount` 表示末尾的设置页签。 */
export const bottomBarSelected: (index: number) => void;

/**
 * 有没有二级页 → 决定覆盖层显不显示。
 *
 * 无子页时必须整层藏掉：全屏 XComponent 即使内容为空也会吃掉主界面的手势。
 */
export const registerOverlay: (publish: (active: boolean) => void) => void;
