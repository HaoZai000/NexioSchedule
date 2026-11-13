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

/** 应用版本 / 名称（关于页展示）；Kotlin 侧拿不到 bundleManager。 */
export const setAppInfo: (version: string, name: string) => void;

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
 * 有没有子页 → ArkTS 用它决定系统返回键是否消费。
 *
 * 页面本身全在 Compose 里，这条通道只传一个布尔值。
 */
export const registerOverlay: (publish: (active: boolean) => void) => void;

/**
 * 系统返回键出栈。有二级页时在 `onBackPressed` 里调用并消费事件（返回 true），
 * 否则系统默认行为是直接退出应用。
 */
export const overlayPopBack: () => void;

/**
 * 注册系统文件选择器（JSON / ICS 导入、导出）。
 *
 * @param picker (requestId, op, payload) => void
 *   op = `openText`：拉起选择器读文本；
 *   op = `saveText`：payload 是 `{ fileName, mimeType, content }` JSON，拉起另存为写入。
 *   完成后必须调 [setFileResult] 回传。
 */
export const registerFilePicker: (
  picker: (requestId: number, op: string, payload: string) => void
) => void;

/**
 * 文件选择结果回传。`ok=false` 且 `payload` 为空表示用户取消；
 * 非空 `payload` 是可直接展示的失败原因。
 */
export const setFileResult: (requestId: number, ok: boolean, payload: string) => void;

/**
 * 注册系统分享 / 剪贴板宿主。
 *
 * @param host (op, payload) => void
 *   op = `share`：payload 是 `{ path, fileName, text }`，用 ShareKit 分享图片与文案；
 *   op = `clipboard`：payload 是要写入系统剪贴板的文本。
 */
export const registerShareHost: (
  host: (op: string, payload: string) => void
) => void;

/**
 * 注册「同步到日历」宿主（系统日历只有 ArkTS 的 calendarManager 能写）。
 *
 * @param host (requestId, payload) => void
 *   payload = `{ reminderMinutes, events: [{ uid, title, description, location, start, end }] }`，
 *   start / end 是 `{ y, mo, d, h, mi }`。完成后必须调 [setCalendarResult] 回传。
 */
export const registerCalendarSync: (
  host: (requestId: number, payload: string) => void
) => void;

/**
 * 同步结果回传：`ok` 与 `payload = { status: "ok"|"denied"|"noCalendar"|"failed", count }`。
 */
export const setCalendarResult: (requestId: number, ok: boolean, payload: string) => void;

/**
 * 注册网页导入宿主（ArkUI `Web` 组件）。Kotlin 侧发指令，ArkTS 执行并回传事件。
 *
 * @param host (json) => void
 *   json 的 `op`：`open` / `close` / `evalJs` / `reload` / `goBack` / `desktop` / `resolve` / `reject`。
 */
export const registerWebHost: (host: (json: string) => void) => void;

/**
 * 网页事件回传：`{event: "title"|"progress"|"state"|"import"|"bridge", …}`。
 */
export const webHostEvent: (json: string) => void;

