/**
 * 鸿蒙端宿主页。
 *
 * 这一页只做一件事：把 Compose 的 controller 挂到 ArkUI 节点上。
 * 界面本身全在 Kotlin 侧（`ohos/ohosApp` → `libnexioohos.so`），这里不写任何业务 UI ——
 * 在 ArkTS 里再画一份就是两套真相。
 *
 * 链路：setAppEnv(沙箱目录, 屏幕像素) → MainArkUIViewController()（NAPI，来自
 * libentry.so → libnexioohos.so）→ Compose 组件（HAR）→ XComponent → Compose 渲染。
 *
 * 为什么要在 ArkTS 传目录：Kotlin/Native 侧拿不到 `UIAbilityContext`，而壁纸、缓存、
 * 设置的落盘目录只有 `context.filesDir` 给得出。
 */

// 副作用 import：只为触发 libskikobridge 的 NAPI 模块注册（RegisterSkikobridgeModule）。
// 它的 napi_init 里会调 `skikobridge_compose_coroutines_dispatcher_bind_jsenv(env)`，
// 把协程调度器的 JS env 绑上 —— 不绑的话宿主每次投递任务都报
// 「MainHandler: runOnMainThread failed: not initialized」。
import 'libskikobridge.so';
import { Compose, ArkUIViewController } from '@cpf-kmp-cmp/compose';
import {
  MainArkUIViewController,
  setAppEnv,
  registerWallpaperPicker,
  setWallpaperBytes
} from 'libentry.so';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { fileIo } from '@kit.CoreFileKit';
import { display } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

@Entry
@Component
struct Index {
  private controller: ArkUIViewController | undefined = undefined;

  aboutToAppear(): void {
    // 先把运行环境交给 Kotlin（沙箱目录 + 屏幕像素），再建 controller：
    // 壁纸是**首帧同步**读取的，晚一步就会先画一帧纯色再跳成用户壁纸。
    try {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const d = display.getDefaultDisplaySync();
      // 屏幕圆角半径（px）：API 17+ 的 `Display.getRoundedCorner()`。
      // API 23+ 同一方法会返回 RoundedCorner[]（每个角各自 position/radius），这里都兼容。
      // 无圆角的设备（外接显示器/部分模拟器）返回 0 —— 消费方会当作「不裁切」。
      let cornerRadiusPx = 0;
      try {
        const rc: Object = (d as object)['getRoundedCorner']?.();
        if (typeof rc === 'number') {
          cornerRadiusPx = rc;
        } else if (Array.isArray(rc) && rc.length > 0) {
          cornerRadiusPx = Math.max(...rc.map((c: Record<string, number>) => c.radius ?? 0));
        }
      } catch (e3) {
        hilog.warn(0x0000, 'nexio', 'getRoundedCorner failed: %{public}s', JSON.stringify(e3 as BusinessError));
      }
      setAppEnv(ctx.filesDir, d.width, d.height, cornerRadiusPx);
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'setAppEnv failed: %{public}s', JSON.stringify(e as BusinessError));
    }

    // 壁纸选图：UI 侧（Kotlin）点「选壁纸」时，反过来调这里起系统选图器，
    // 选完把文件字节回传（Kotlin 负责解码/降采样/落盘）。
    registerWallpaperPicker((): void => {
      this.pickWallpaper();
    });

    // 拿到 Kotlin 侧的 controller 句柄；UI 树此时才真正建立
    this.controller = MainArkUIViewController();
  }

  /** 起系统选图器，把选中的图以字节回传给 Kotlin。 */
  private pickWallpaper(): void {
    try {
      const picker = new photoAccessHelper.PhotoViewPicker();
      picker.select({
        MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
        maxSelectNumber: 1
      }).then((result: photoAccessHelper.PhotoSelectResult) => {
        const uri = result?.photoUris?.[0];
        if (!uri) {
          setWallpaperBytes(new Uint8Array(0));
          return;
        }
        const file = fileIo.openSync(uri, fileIo.OpenMode.READ_ONLY);
        try {
          const size = fileIo.statSync(file.fd).size;
          const buffer = new ArrayBuffer(size);
          fileIo.readSync(file.fd, buffer);
          setWallpaperBytes(new Uint8Array(buffer));
        } finally {
          fileIo.closeSync(file);
        }
      }).catch((err: BusinessError) => {
        hilog.warn(0x0000, 'nexio', 'pick wallpaper cancelled: %{public}s', JSON.stringify(err));
        setWallpaperBytes(new Uint8Array(0));
      });
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'pick wallpaper failed: %{public}s', JSON.stringify(e as BusinessError));
      setWallpaperBytes(new Uint8Array(0));
    }
  }

  build() {
    Stack() {
      if (this.controller !== undefined) {
        Compose({
          controller: this.controller,
          libraryName: 'entry',
          onBackPressed: () => false
        })
          .width('100%')
          .height('100%')
      }
    }
    .width('100%')
    .height('100%')
  }
}
