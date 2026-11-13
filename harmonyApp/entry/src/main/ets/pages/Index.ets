import 'libskikobridge.so';
import { Compose, ArkUIViewController } from '@cpf-kmp-cmp/compose';
import {
  MainArkUIViewController,
  setAppEnv,
  registerWallpaperPicker,
  setWallpaperBytes,
  registerHttpSender,
  setHttpResult,
  registerToast,
  registerOverlay,
  overlayPopBack,
  registerFilePicker,
  setFileResult,
  setAppInfo,
  registerShareHost,
  registerCalendarSync,
  setCalendarResult
} from 'libentry.so';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { fileIo, picker, fileUri } from '@kit.CoreFileKit';
import { abilityAccessCtrl, bundleManager, common, Permissions } from '@kit.AbilityKit';
import { calendarManager } from '@kit.CalendarKit';
import { BusinessError, pasteboard } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { http } from '@kit.NetworkKit';
import { util } from '@kit.ArkTS';
import { display } from '@kit.ArkUI';
import { systemShare } from '@kit.ShareKit';
import { uniformTypeDescriptor } from '@kit.ArkData';
import { WebImportOverlay } from './WebImportOverlay';

/** 文本导入上限：与共享侧 `MAX_BACKUP_PARSE_BYTES` 同口径，防止把超大文件整个读进内存。 */
const MAX_TEXT_FILE_BYTES: number = 8 * 1024 * 1024;

function sendHttp(requestId: number, payloadJson: string, maxBytes: number): void {
  const fail = (message: string): void => {
    setHttpResult(requestId, -1, new util.TextEncoder().encodeInto(message), false);
  };

  let payload: Record<string, Object>;
  try {
    payload = JSON.parse(payloadJson) as Record<string, Object>;
  } catch (e) {
    fail('payload 解析失败');
    return;
  }

  const method = (payload.method as string ?? 'GET').toUpperCase();
  const url = payload.url as string;
  const headers = (payload.headers as Record<string, string>) ?? {};
  const body = payload.body as string | undefined;

  const options: http.HttpRequestOptions = {
    method: method as http.RequestMethod,
    header: headers,
    expectDataType: http.HttpDataType.ARRAY_BUFFER,
    connectTimeout: (payload.connectTimeout as number) ?? 10000,
    readTimeout: (payload.readTimeout as number) ?? 10000,
  };
  if (body !== undefined && body !== null) {
    options.extraData = body;
  }
  if (maxBytes > 0) {
    options.maxLimit = maxBytes;
  }

  const request = http.createHttp();
  request.request(url, options)
    .then((response: http.HttpResponse) => {
      let bytes = new Uint8Array(0);
      const raw = response.result;
      if (raw instanceof ArrayBuffer) {
        bytes = new Uint8Array(raw);
      } else if (typeof raw === 'string') {
        bytes = new util.TextEncoder().encodeInto(raw as string);
      }
      let truncated = false;
      if (maxBytes > 0 && bytes.byteLength > maxBytes) {
        bytes = bytes.subarray(0, maxBytes);
        truncated = true;
      }
      setHttpResult(requestId, response.responseCode, bytes, truncated);
      request.destroy();
    })
    .catch((err: BusinessError) => {
      fail(err?.message ?? '网络请求失败');
      request.destroy();
    });
}

/** 「同步到日历」里的一节课：时间拆成字段，用 `new Date(y, mo-1, d, h, mi)` 转本地时间戳。 */
interface CalendarTimeParts {
  y: number;
  mo: number;
  d: number;
  h: number;
  mi: number;
}

interface CalendarEventPayload {
  uid: string;
  title: string;
  description: string;
  location: string;
  start: CalendarTimeParts;
  end: CalendarTimeParts;
}

function toEpochMillis(parts: CalendarTimeParts): number {
  return new Date(parts.y, parts.mo - 1, parts.d, parts.h, parts.mi, 0, 0).getTime();
}

/**
 * ArkUI 宿主页。
 *
 * 顶栏、底栏、子页顶栏、转场全部由 :ui-shared 的 Compose 绘制（与安卓同一套）。
 * 这里只保留 ArkUI 才能做的事：挂 Compose 的 XComponent、系统选图 / 文件选择 /
 * 分享 / 剪贴板 / 日历写入，以及盖在 Compose 之上的网页导入覆盖层。
 */
@Entry
@Component
struct Index {
  private controller: ArkUIViewController | undefined = undefined;

  /** 有没有子页。系统返回键据此决定是否消费；页面本身全在 Compose 里。 */
  @State private overlayActive: boolean = false;

  aboutToAppear(): void {
    try {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const d = display.getDefaultDisplaySync();
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
      try {
        const bundleInfo = bundleManager.getBundleInfoForSelfSync(
          bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT);
        const appName = ctx.resourceManager.getStringByNameSync('app_name');
        setAppInfo(bundleInfo.versionName ?? '', appName ?? '');
      } catch (e3) {
        hilog.warn(0x0000, 'nexio', 'setAppInfo failed: %{public}s', JSON.stringify(e3 as BusinessError));
      }
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'setAppEnv failed: %{public}s', JSON.stringify(e as BusinessError));
    }

    registerWallpaperPicker((): void => {
      this.pickWallpaper();
    });

    registerHttpSender(sendHttp);

    // 文件选择 / 另存为：Kotlin 侧导入导出页发起，这里拉起系统选择器后回传文本
    registerFilePicker((requestId: number, op: string, payload: string): void => {
      this.onFileRequest(requestId, op, payload);
    });

    // 系统分享 / 剪贴板：ShareKit 与 pasteboard 只能由 ArkTS 调起
    registerShareHost((op: string, payload: string): void => {
      this.onShareRequest(op, payload);
    });

    // 同步到日历：系统日历只能由 ArkTS 的 calendarManager 写
    registerCalendarSync((requestId: number, payload: string): void => {
      this.onCalendarSync(requestId, payload);
    });

    registerToast((message: string, longDuration: boolean): void => {
      try {
        this.getUIContext().getPromptAction().showToast({
          message: message,
          duration: longDuration ? 3500 : 2000
        });
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'showToast failed: %{public}s', JSON.stringify(e as BusinessError));
      }
    });

    registerOverlay((active: boolean): void => {
      this.overlayActive = active;
    });

    this.controller = MainArkUIViewController();
  }

  /**
   * 系统返回键：有子页时出栈并消费（Compose 的预测返回在鸿蒙是空实现，
   * 不接这条链系统默认行为就是退出应用）；主界面保持系统默认。
   */
  private handleBack(): boolean {
    if (this.overlayActive) {
      overlayPopBack();
      return true;
    }
    return false;
  }

  /**
   * 返回键的真正入口是 @Entry 的生命周期钩子 —— `Compose` 组件的 onBackPressed
   * 属性在鸿蒙上不会被系统调用。返回 true = 本次按键由页面自己处理（不退出应用）。
   */
  onBackPress(): boolean {
    return this.handleBack();
  }

  /**
   * 页面可见性必须转给控制器：HAR 的 README 明确要求由 @Entry 转发。
   * 不转的话 Compose 收不到 onPageShow / onPageHide，焦点、输入法与动画时钟
   * 在切后台/回前台时会停在旧状态。
   */
  onPageShow(): void {
    this.controller?.onPageShow();
  }

  onPageHide(): void {
    this.controller?.onPageHide();
  }

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

  // ── 文件选择 / 另存为 ────────────────────────────────────────────────
  // Kotlin 侧导入导出页发起：op = "openText" 读文本，op = "saveText" 写文本。
  // 取消不当作错误（ok=false + 空 payload），失败带一句可展示的原因。

  private onFileRequest(requestId: number, op: string, payload: string): void {
    if (op === 'openText') {
      this.openTextFile(requestId);
      return;
    }
    if (op === 'saveText') {
      this.saveTextFile(requestId, payload);
      return;
    }
    setFileResult(requestId, false, '不支持的文件操作');
  }

  private openTextFile(requestId: number): void {
    try {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const docPicker = new picker.DocumentViewPicker(ctx);
      docPicker.select({ maxSelectNumber: 1 }).then((uris: string[]) => {
        if (uris === undefined || uris.length === 0) {
          setFileResult(requestId, false, '');
          return;
        }
        const file = fileIo.openSync(uris[0], fileIo.OpenMode.READ_ONLY);
        try {
          const size = fileIo.statSync(file.fd).size;
          if (size > MAX_TEXT_FILE_BYTES) {
            setFileResult(requestId, false, '文件过大，上限 8 MB');
            return;
          }
          const buffer = new ArrayBuffer(size);
          fileIo.readSync(file.fd, buffer);
          const text = new util.TextDecoder('utf-8').decode(new Uint8Array(buffer));
          setFileResult(requestId, true, text);
        } finally {
          fileIo.closeSync(file);
        }
      }).catch((err: BusinessError) => {
        hilog.warn(0x0000, 'nexio', 'open text cancelled: %{public}s', JSON.stringify(err));
        setFileResult(requestId, false, '');
      });
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'open text failed: %{public}s', JSON.stringify(e as BusinessError));
      setFileResult(requestId, false, '无法打开文件选择器');
    }
  }

  private saveTextFile(requestId: number, payload: string): void {
    let fileName = 'nexio.txt';
    let content = '';
    try {
      const obj = JSON.parse(payload) as Record<string, string>;
      fileName = obj.fileName ?? fileName;
      content = obj.content ?? content;
    } catch (e) {
      setFileResult(requestId, false, '导出参数解析失败');
      return;
    }
    try {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const docPicker = new picker.DocumentViewPicker(ctx);
      docPicker.save({ newFileNames: [fileName] }).then((uris: string[]) => {
        if (uris === undefined || uris.length === 0) {
          setFileResult(requestId, false, '');
          return;
        }
        const file = fileIo.openSync(uris[0],
          fileIo.OpenMode.WRITE_ONLY | fileIo.OpenMode.CREATE | fileIo.OpenMode.TRUNC);
        try {
          const bytes = new util.TextEncoder().encodeInto(content);
          fileIo.writeSync(file.fd, bytes.buffer as ArrayBuffer);
          setFileResult(requestId, true, '');
        } finally {
          fileIo.closeSync(file);
        }
      }).catch((err: BusinessError) => {
        hilog.warn(0x0000, 'nexio', 'save text cancelled: %{public}s', JSON.stringify(err));
        setFileResult(requestId, false, '');
      });
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'save text failed: %{public}s', JSON.stringify(e as BusinessError));
      setFileResult(requestId, false, '无法打开另存为选择器');
    }
  }

  // ── 系统分享 / 剪贴板 ────────────────────────────────────────────────
  // op = "clipboard"：payload 是文本；op = "share"：payload = { path, fileName, text }。

  private onShareRequest(op: string, payload: string): void {
    if (op === 'clipboard') {
      try {
        const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, payload);
        pasteboard.getSystemPasteboard().setDataSync(data);
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'set clipboard failed: %{public}s', JSON.stringify(e as BusinessError));
      }
      return;
    }
    if (op === 'exitApp') {
      try {
        const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
        ctx.terminateSelf();
      } catch (e) {
        hilog.error(0x0000, 'nexio', 'exit app failed: %{public}s', JSON.stringify(e as BusinessError));
      }
      return;
    }
    if (op === 'share') {
      this.shareImageAndText(payload);
    }
  }

  private shareImageAndText(payload: string): void {
    try {
      const obj = JSON.parse(payload) as Record<string, string>;
      const path = obj.path ?? '';
      const text = obj.text ?? '';
      if (path === '') {
        return;
      }
      const imageRecord: systemShare.SharedRecord = {
        utd: uniformTypeDescriptor.UniformDataType.PNG,
        uri: fileUri.getUriFromPath(path),
        title: obj.fileName ?? ''
      };
      const textRecord: systemShare.SharedRecord = {
        utd: uniformTypeDescriptor.UniformDataType.PLAIN_TEXT,
        content: text
      };
      const data = new systemShare.SharedData(imageRecord);
      data.addRecord(textRecord);
      const controller = new systemShare.ShareController(data);
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
      controller.show(ctx, {
        selectionMode: systemShare.SelectionMode.BATCH,
        previewMode: systemShare.SharePreviewMode.DETAIL
      }).catch((err: BusinessError) => {
        hilog.warn(0x0000, 'nexio', 'share panel failed: %{public}s', JSON.stringify(err));
      });
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'share request failed: %{public}s', JSON.stringify(e as BusinessError));
    }
  }

  // ── 同步到日历 ────────────────────────────────────────────────────────
  // Kotlin 把逐节课事件发过来（payload = { reminderMinutes, events[] }）。
  // 语义与安卓一致：每次同步是覆盖 —— 先清空本应用自建日历里的旧事件，再全量重写。

  private async onCalendarSync(requestId: number, payload: string): Promise<void> {
    let items: CalendarEventPayload[] = [];
    let reminderMinutes = -1;
    try {
      const obj = JSON.parse(payload) as Record<string, Object>;
      items = (obj.events as CalendarEventPayload[]) ?? [];
      reminderMinutes = (obj.reminderMinutes as number) ?? -1;
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'calendar payload parse failed: %{public}s',
        JSON.stringify(e as BusinessError));
      setCalendarResult(requestId, false, '{"status":"failed","count":0}');
      return;
    }

    try {
      const ctx = this.getUIContext().getHostContext() as common.UIAbilityContext;
      const atManager = abilityAccessCtrl.createAtManager();
      const perms: Permissions[] = ['ohos.permission.READ_CALENDAR', 'ohos.permission.WRITE_CALENDAR'];
      const grant = await atManager.requestPermissionsFromUser(ctx, perms);
      for (let i = 0; i < grant.authResults.length; i++) {
        if (grant.authResults[i] !== 0) {
          setCalendarResult(requestId, false, '{"status":"denied","count":0}');
          return;
        }
      }

      const manager = calendarManager.getCalendarManager(ctx);
      let calendar: calendarManager.Calendar;
      try {
        calendar = await manager.getCalendar({
          name: 'Nexio课程表',
          type: calendarManager.CalendarType.LOCAL
        } as calendarManager.CalendarAccount);
      } catch (e) {
        // 自建账户不可用时退回应用默认日历
        calendar = await manager.getCalendar();
      }

      const existing = await calendar.getEvents();
      for (let i = 0; i < existing.length; i++) {
        const oldId = existing[i].id;
        if (oldId !== undefined) {
          await calendar.deleteEvent(oldId);
        }
      }

      let written = 0;
      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        const event: calendarManager.Event = {
          type: calendarManager.EventType.NORMAL,
          title: item.title,
          description: item.description,
          startTime: toEpochMillis(item.start),
          endTime: toEpochMillis(item.end),
          isAllDay: false
        };
        if (item.location !== '') {
          event.location = { location: item.location } as calendarManager.Location;
        }
        if (reminderMinutes >= 0) {
          event.reminderTime = [reminderMinutes];
        }
        await calendar.addEvent(event);
        written++;
      }
      setCalendarResult(requestId, true, `{"status":"ok","count":${written}}`);
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'calendar sync failed: %{public}s',
        JSON.stringify(e as BusinessError));
      setCalendarResult(requestId, false, '{"status":"failed","count":0}');
    }
  }

  build() {
    Stack() {
      if (this.controller !== undefined) {
        Compose({
          controller: this.controller,
          libraryName: 'entry',
          onBackPressed: (): boolean => this.handleBack()
        })
          .width('100%')
          .height('100%')
      }

      // 网页导入覆盖层：纯 ArkUI 页面，压在整个 Compose 之上（网页本体只能由 ArkTS 建）
      WebImportOverlay()
    }
    .width('100%')
    .height('100%')
  }
}
