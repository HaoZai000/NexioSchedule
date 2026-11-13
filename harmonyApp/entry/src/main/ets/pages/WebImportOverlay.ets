import { webview } from '@kit.ArkWeb';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { display } from '@kit.ArkUI';
import { url } from '@kit.ArkTS';
import { registerWebHost, webHostEvent } from 'libentry.so';

/** ArkTS → Kotlin 的网页事件（`webHostEvent` 的 JSON 体）。 */
interface WebEvent {
  event: string;
  title?: string;
  progress?: number;
  canGoBack?: boolean;
  host?: string;
  tableId?: string;
  method?: string;
  payload?: string;
  promiseId?: string;
}

/** Kotlin → ArkTS 的网页导入指令（见 `OhosWebHost.kt` 的 `op`）。 */
interface WebCommand {
  op: string;
  url?: string;
  desktop?: boolean;
  schoolName?: string;
  currentSchedule?: string;
  schedules?: string[];
  promiseBridgeJs?: string;
  js?: string;
  promiseId?: string;
  result?: string;
  error?: string;
}

/**
 * 暴露给教务脚本的 `window.AndroidBridge`。
 *
 * 名字与参数顺序是云端脚本仓库的契约（与 Android / iOS 逐字一致）。异步方法把
 * promiseId 交给 Kotlin 侧兑现 —— 落库在内核里，这里只负责转发与弹窗。
 */
class NexioWebProxy {
  onCall: (method: string, payload: string, promiseId: string) => void = () => {};

  showToast(message: string): void {
    this.onCall('showToast', message, '');
  }

  notifyTaskCompletion(): void {
    this.onCall('notifyTaskCompletion', '', '');
  }

  saveImportedCourses(json: string, promiseId: string): void {
    this.onCall('saveImportedCourses', json, promiseId);
  }

  saveCourseConfig(json: string, promiseId: string): void {
    this.onCall('saveCourseConfig', json, promiseId);
  }

  savePresetTimeSlots(json: string, promiseId: string): void {
    this.onCall('savePresetTimeSlots', json, promiseId);
  }

  saveComboSchedule(json: string, promiseId: string): void {
    this.onCall('saveComboSchedule', json, promiseId);
  }

  showAlert(title: string, content: string, confirmText: string, promiseId: string): void {
    this.onCall('showAlert', JSON.stringify([title, content, confirmText]), promiseId);
  }

  showPrompt(title: string, tip: string, defaultText: string, validatorJs: string, promiseId: string): void {
    this.onCall('showPrompt', JSON.stringify([title, tip, defaultText, validatorJs]), promiseId);
  }

  showSingleSelection(title: string, itemsJson: string, defaultIndex: number, promiseId: string): void {
    this.onCall('showSingleSelection', JSON.stringify([title, itemsJson, defaultIndex]), promiseId);
  }

  /** 鸿蒙没有 POST 转发通道（与 iOS 同口径） */
  isPostForwardEnabled(): boolean {
    return false;
  }
}

/**
 * 网页导入覆盖层：纯 ArkUI 的独立页面。
 *
 * 页面完全脱离 Compose 的文档顶栏体系 —— 顶栏、网页、底栏、弹窗都在这一层里，
 * 并且挂在子页顶栏之上（z 序最后），所以不会与下层页面的按钮重叠，也不会被抢点击。
 * Compose 侧只负责路由栈、返回与课程落库（见 `OhosWebImportRoute`）。
 */
@Component
export struct WebImportOverlay {
  @StorageProp('topInsetVp') private topInsetVp: number = 0;

  private webController: webview.WebviewController = new webview.WebviewController();
  private webProxy: NexioWebProxy = new NexioWebProxy();
  private webPromiseBridgeJs: string = '';
  private screenWidthVp: number = 360;

  @State private visible: boolean = false;
  @State private src: string = '';
  @State private progress: number = 0;
  @State private desktop: boolean = false;
  @State private schoolName: string = '';
  @State private schedules: string[] = [];
  @State private currentSchedule: string = '';
  @State private title: string = '';

  /** 进出场位移（vp）：进入从右侧滑入，退出向右滑走。 */
  @State private shiftX: number = 0;
  @State private overlayOpacity: number = 1;

  // 弹窗自己画（系统弹窗会被 Web 表面盖住）：alert / prompt / selection / import
  @State private dialogKind: string = '';
  @State private dialogTitle: string = '';
  @State private dialogMessage: string = '';
  @State private dialogConfirm: string = '确定';
  @State private dialogItems: string[] = [];
  @State private dialogSelected: number = 0;
  @State private dialogInput: string = '';
  private pendingPromiseId: string = '';
  private promptValidator: string = '';

  aboutToAppear(): void {
    try {
      const d = display.getDefaultDisplaySync();
      this.screenWidthVp = this.getUIContext().px2vp(d.width);
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'web overlay size failed: %{public}s',
        JSON.stringify(e as BusinessError));
    }
    this.webProxy.onCall = (method: string, payload: string, promiseId: string): void => {
      this.onBridgeCall(method, payload, promiseId);
    };
    registerWebHost((json: string): void => {
      this.onCommand(json);
    });
  }

  aboutToDisappear(): void {
    this.finishHide();
  }

  // ── Kotlin → ArkTS ────────────────────────────────────────────────

  private onCommand(json: string): void {
    let cmd: WebCommand;
    try {
      cmd = JSON.parse(json) as WebCommand;
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'web command parse failed: %{public}s',
        JSON.stringify(e as BusinessError));
      return;
    }
    if (cmd.op === 'open') {
      this.webPromiseBridgeJs = cmd.promiseBridgeJs ?? '';
      this.schoolName = cmd.schoolName ?? '';
      this.schedules = Array.isArray(cmd.schedules) ? cmd.schedules : [];
      this.currentSchedule = cmd.currentSchedule ?? '';
      this.desktop = cmd.desktop ?? false;
      this.title = cmd.schoolName ?? '';
      this.progress = 0;
      const url = (cmd.url ?? '').trim();
      this.src = url === '' ? 'about:blank' : (url.startsWith('http') ? url : `https://${url}`);
      this.shiftX = this.screenWidthVp;
      this.overlayOpacity = 1;
      this.visible = true;
      setTimeout((): void => {
        animateTo({ duration: 320, curve: Curve.FastOutSlowIn }, (): void => {
          this.shiftX = 0;
        });
      }, 16);
      return;
    }
    if (cmd.op === 'exit') {
      // 与页面一起滑走：按钮就在这一层里，不会再出现「按钮先走、页面后走」
      if (!this.visible) {
        return;
      }
      animateTo({ duration: 260, curve: Curve.FastOutSlowIn }, (): void => {
        this.shiftX = this.screenWidthVp;
        this.overlayOpacity = 0;
      });
      setTimeout((): void => {
        if (this.shiftX === this.screenWidthVp) {
          this.finishHide();
        }
      }, 280);
      return;
    }
    if (cmd.op === 'close') {
      this.finishHide();
      return;
    }
    if (cmd.op === 'evalJs') {
      // 兜底：脚本执行前确保 Promise 基础设施在页面上
      this.runJs(this.webPromiseBridgeJs);
      this.runJs(cmd.js ?? '');
      return;
    }
    if (cmd.op === 'reload') {
      this.refreshWeb();
      return;
    }
    if (cmd.op === 'goBack') {
      try {
        this.webController.backward();
        this.reportState();
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'web goBack failed: %{public}s', JSON.stringify(e as BusinessError));
      }
      return;
    }
    if (cmd.op === 'desktop') {
      this.desktop = cmd.desktop ?? false;
      this.applyUserAgent();
      this.refreshWeb();
      return;
    }
    if (cmd.op === 'resolve') {
      this.resolvePromise(cmd.promiseId ?? '', cmd.result ?? 'null');
      return;
    }
    if (cmd.op === 'reject') {
      this.rejectPromise(cmd.promiseId ?? '', cmd.error ?? '');
    }
  }

  private finishHide(): void {
    if (!this.visible) {
      return;
    }
    this.visible = false;
    this.shiftX = 0;
    this.overlayOpacity = 1;
    this.progress = 0;
    this.title = '';
    this.dialogKind = '';
    this.runJs('window._androidPromiseResolvers = {}; window._androidPromiseRejectors = {};');
    try {
      this.webController.stop();
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'web stop failed: %{public}s', JSON.stringify(e as BusinessError));
    }
  }

  // ── 网页控制 ──────────────────────────────────────────────────────

  /** 手机版 / 桌面版 UA：不少教务系统只认 Safari/Chrome 版本号。 */
  private userAgent(desktop: boolean): string {
    if (desktop) {
      return 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 ' +
        '(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
    }
    return 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 ' +
      '(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
  }

  private applyUserAgent(): void {
    try {
      this.webController.setCustomUserAgent(this.userAgent(this.desktop));
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'set UA failed: %{public}s', JSON.stringify(e as BusinessError));
    }
  }

  private refreshWeb(): void {
    try {
      this.webController.refresh();
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'web refresh failed: %{public}s', JSON.stringify(e as BusinessError));
    }
  }

  private runJs(js: string): void {
    if (js === '') {
      return;
    }
    try {
      this.webController.runJavaScript(js);
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'runJavaScript failed: %{public}s', JSON.stringify(e as BusinessError));
    }
  }

  private escapeJs(value: string): string {
    return value
      .split('\\').join('\\\\')
      .split("'").join("\\'")
      .split('\n').join('\\n')
      .split('\r').join('');
  }

  private resolvePromise(promiseId: string, result: string): void {
    if (promiseId === '') {
      return;
    }
    this.runJs(`window._resolveAndroidPromise && window._resolveAndroidPromise('${promiseId}', ${result});`);
  }

  private rejectPromise(promiseId: string, error: string): void {
    if (promiseId === '') {
      return;
    }
    this.runJs(
      `window._rejectAndroidPromise && window._rejectAndroidPromise('${promiseId}', '${this.escapeJs(error)}');`);
  }

  private reportState(): void {
    let canGoBack = false;
    try {
      canGoBack = this.webController.accessBackward();
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'web state failed: %{public}s', JSON.stringify(e as BusinessError));
    }
    let host = '';
    try {
      host = new url.URL(this.webController.getUrl()).hostname;
    } catch (e) {
      host = '';
    }
    webHostEvent(JSON.stringify({
      event: 'state',
      canGoBack: canGoBack,
      host: host
    } as WebEvent));
  }

  /** 顶栏返回：网页有历史就后退，否则交给路由出栈。 */
  private onBack(): void {
    try {
      if (this.webController.accessBackward()) {
        this.webController.backward();
        this.reportState();
        return;
      }
    } catch (e) {
      hilog.warn(0x0000, 'nexio', 'web backward failed: %{public}s', JSON.stringify(e as BusinessError));
    }
    webHostEvent(JSON.stringify({ event: 'back' } as WebEvent));
  }

  private toggleDesktop(): void {
    this.desktop = !this.desktop;
    this.applyUserAgent();
    this.refreshWeb();
  }

  // ── 脚本 → 原生 ──────────────────────────────────────────────────

  private onBridgeCall(method: string, payload: string, promiseId: string): void {
    if (method === 'showToast') {
      try {
        this.getUIContext().getPromptAction().showToast({ message: payload, duration: 2000 });
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'web toast failed');
      }
      return;
    }
    if (method === 'showAlert') {
      this.showAlert(payload, promiseId);
      return;
    }
    if (method === 'showPrompt') {
      this.showPrompt(payload, promiseId);
      return;
    }
    if (method === 'showSingleSelection') {
      this.showSelection(payload, promiseId);
      return;
    }
    webHostEvent(JSON.stringify({
      event: 'bridge',
      method: method,
      payload: payload,
      promiseId: promiseId
    } as WebEvent));
  }

  private showAlert(payload: string, promiseId: string): void {
    try {
      const args = JSON.parse(payload) as string[];
      this.pendingPromiseId = promiseId;
      this.dialogKind = 'alert';
      this.dialogTitle = args[0] ?? '';
      this.dialogMessage = args[1] ?? '';
      this.dialogConfirm = (args[2] ?? '') !== '' ? args[2] : '确定';
    } catch (e) {
      this.resolvePromise(promiseId, 'true');
    }
  }

  private showSelection(payload: string, promiseId: string): void {
    try {
      const args = JSON.parse(payload) as [string, string, number];
      this.pendingPromiseId = promiseId;
      this.dialogKind = 'selection';
      this.dialogTitle = args[0] ?? '';
      const parsed = JSON.parse(args[1]) as Object;
      this.dialogItems = Array.isArray(parsed) ? parsed as string[] : [];
      const index = args[2] ?? 0;
      this.dialogSelected = (index >= 0 && index < this.dialogItems.length) ? index : 0;
    } catch (e) {
      this.resolvePromise(promiseId, 'null');
    }
  }

  private showPrompt(payload: string, promiseId: string): void {
    let args: string[] = [];
    try {
      args = JSON.parse(payload) as string[];
    } catch (e) {
      this.resolvePromise(promiseId, 'null');
      return;
    }
    this.pendingPromiseId = promiseId;
    this.promptValidator = args[3] ?? '';
    this.dialogKind = 'prompt';
    this.dialogTitle = args[0] ?? '';
    this.dialogMessage = args[1] ?? '';
    this.dialogInput = args[2] ?? '';
  }

  private onDialogPick(index: number): void {
    const kind = this.dialogKind;
    const value = index >= 0 && index < this.dialogItems.length ? this.dialogItems[index] : '';
    this.dialogKind = '';
    if (kind === 'selection') {
      this.resolvePromise(this.pendingPromiseId, `${index}`);
      return;
    }
    if (kind === 'import') {
      webHostEvent(JSON.stringify({ event: 'import', tableId: value } as WebEvent));
    }
  }

  private onAlertResult(confirmed: boolean): void {
    this.dialogKind = '';
    this.resolvePromise(this.pendingPromiseId, confirmed ? 'true' : 'false');
  }

  private onPromptResult(confirmed: boolean): void {
    const promiseId = this.pendingPromiseId;
    const validator = this.promptValidator;
    this.dialogKind = '';
    if (!confirmed) {
      this.resolvePromise(promiseId, 'null');
      return;
    }
    const escaped = this.escapeJs(this.dialogInput);
    if (validator === '') {
      this.resolvePromise(promiseId, `'${escaped}'`);
      return;
    }
    try {
      this.webController.runJavaScript(`${validator}('${escaped}')`, (err, result) => {
        const message = (result ?? '').split('"').join('').trim();
        if (message === '' || message.toLowerCase() === 'false') {
          this.resolvePromise(promiseId, `'${escaped}'`);
        } else {
          try {
            this.getUIContext().getPromptAction().showToast({ message: message, duration: 2000 });
          } catch (e) {
            hilog.warn(0x0000, 'nexio', 'web validator toast failed');
          }
          this.rejectPromise(promiseId, message);
        }
      });
    } catch (e) {
      this.resolvePromise(promiseId, `'${escaped}'`);
    }
  }

  /** 底部条「执行导入」：先选目标课表（自绘弹窗），再让 Kotlin 拉脚本注入。 */
  private onImportClick(): void {
    if (this.schedules.length === 0) {
      webHostEvent(JSON.stringify({ event: 'import', tableId: '' } as WebEvent));
      return;
    }
    let selected = this.schedules.indexOf(this.currentSchedule);
    if (selected < 0) {
      selected = 0;
    }
    this.dialogKind = 'import';
    this.dialogTitle = '选择课表';
    this.dialogItems = this.schedules;
    this.dialogSelected = selected;
  }

  // ── UI ───────────────────────────────────────────────────────────

  build() {
    if (this.visible) {
      Stack({ alignContent: Alignment.Bottom }) {
        Column() {
          this.topBar()
          if (this.progress > 0 && this.progress < 100) {
            Row()
              .width(`${this.progress}%`)
              .height(3)
              .backgroundColor($r('sys.color.brand'))
          }
          Web({ src: this.src, controller: this.webController })
            .javaScriptAccess(true)
            .domStorageAccess(true)
            .fileAccess(true)
            .mixedMode(MixedMode.All)
            .zoomAccess(true)
            .javaScriptProxy({
              object: this.webProxy,
              name: 'AndroidBridge',
              methodList: [
                'showToast', 'notifyTaskCompletion', 'saveImportedCourses', 'saveCourseConfig',
                'savePresetTimeSlots', 'saveComboSchedule', 'showAlert', 'showPrompt',
                'showSingleSelection', 'isPostForwardEnabled'
              ],
              controller: this.webController
            })
            // scriptRules 必须显式给通配：空数组按 SDK 语义是不匹配任何页面
            .runJavaScriptOnDocumentStart([{ script: this.webPromiseBridgeJs, scriptRules: ['*'] }])
            .onControllerAttached((): void => {
              this.applyUserAgent();
            })
            .onProgressChange((event: OnProgressChangeEvent): void => {
              this.progress = event.newProgress;
              webHostEvent(JSON.stringify({
                event: 'progress',
                progress: event.newProgress / 100
              } as WebEvent));
            })
            .onTitleReceive((event: OnTitleReceiveEvent): void => {
              if (event.title !== '') {
                this.title = event.title;
              }
            })
            .onPageEnd((): void => {
              this.progress = 100;
              this.runJs(this.webPromiseBridgeJs);
              this.reportState();
            })
            .width('100%')
            .layoutWeight(1)
        }
        .width('100%')
        .height('100%')

        Column() {
          Blank()
          this.dock()
        }
        .width('100%')
        .height('100%')
        .padding({ left: 12, right: 12, bottom: 12 })
        .hitTestBehavior(HitTestMode.Transparent)

        if (this.dialogKind !== '') {
          this.dialog()
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor($r('sys.color.background_primary'))
      .translate({ x: this.shiftX })
      .opacity(this.overlayOpacity)
    }
  }

  /** 自绘顶栏：返回 / 标题 / 刷新。 */
  @Builder
  topBar() {
    Stack({ alignContent: Alignment.Center }) {
      Text(this.title)
        .fontSize(17)
        .fontWeight(FontWeight.Medium)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .padding({ left: 56, right: 56 })

      Row() {
        Button({ type: ButtonType.Circle, stateEffect: true }) {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(22)
            .fontColor([$r('sys.color.font_primary')])
        }
        .width(40)
        .height(40)
        .backgroundColor($r('sys.color.comp_background_tertiary'))
        .onClick((): void => {
          this.onBack();
        })

        Blank()

        Button({ type: ButtonType.Circle, stateEffect: true }) {
          SymbolGlyph($r('sys.symbol.arrow_clockwise'))
            .fontSize(20)
            .fontColor([$r('sys.color.font_primary')])
        }
        .width(40)
        .height(40)
        .backgroundColor($r('sys.color.comp_background_tertiary'))
        .onClick((): void => {
          this.refreshWeb();
        })
      }
      .width('100%')
    }
    .width('100%')
    .height(this.topInsetVp + 48)
    .padding({ left: 16, right: 16, top: this.topInsetVp })
    .backgroundColor($r('sys.color.background_primary'))
  }

  /** 悬浮底栏：左侧学校与模式，右侧「切换模式 / 执行导入」，贴底 12vp。 */
  @Builder
  dock() {
    Row({ space: 8 }) {
      Column({ space: 2 }) {
        Text(this.schoolName)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.desktop ? '桌面版' : '手机版')
          .fontSize(11)
          .fontColor($r('sys.color.font_secondary'))
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      Button(this.desktop ? '手机版' : '桌面版')
        .height(36)
        .fontSize(13)
        .backgroundColor($r('sys.color.comp_background_tertiary'))
        .fontColor($r('sys.color.font_primary'))
        .onClick((): void => {
          this.toggleDesktop();
        })
      Button('执行导入')
        .height(36)
        .fontSize(13)
        .onClick((): void => {
          this.onImportClick();
        })
    }
    .width('100%')
    .padding({ left: 16, right: 8, top: 8, bottom: 8 })
    .backgroundBlurStyle(BlurStyle.COMPONENT_THICK)
    .borderRadius(28)
  }

  /** 自绘弹窗：alert / prompt / 单选 / 目标课表。 */
  @Builder
  dialog() {
    Column() {
      Column({ space: 14 }) {
        Text(this.dialogTitle)
          .fontSize(17)
          .fontWeight(FontWeight.Medium)
          .width('100%')
          .textAlign(TextAlign.Center)
        if (this.dialogMessage !== '' &&
          (this.dialogKind === 'alert' || this.dialogKind === 'prompt')) {
          Text(this.dialogMessage)
            .fontSize(14)
            .fontColor($r('sys.color.font_secondary'))
            .width('100%')
            .textAlign(TextAlign.Center)
        }
        if (this.dialogKind === 'prompt') {
          TextInput({ text: this.dialogInput })
            .onChange((value: string): void => {
              this.dialogInput = value;
            })
        }
        if (this.dialogKind === 'selection' || this.dialogKind === 'import') {
          List() {
            ForEach(this.dialogItems, (item: string, index: number) => {
              ListItem() {
                Row({ space: 8 }) {
                  Text(item)
                    .fontSize(16)
                    .layoutWeight(1)
                    .maxLines(1)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                  if (index === this.dialogSelected) {
                    SymbolGlyph($r('sys.symbol.checkmark'))
                      .fontSize(18)
                      .fontColor([$r('sys.color.brand')])
                  }
                }
                .width('100%')
                .padding({ top: 12, bottom: 12 })
              }
              .onClick((): void => {
                this.onDialogPick(index);
              })
            }, (item: string, index: number) => `${index}-${item}`)
          }
          .width('100%')
          .constraintSize({ maxHeight: 320 })
        }
        Row({ space: 12 }) {
          if (this.dialogKind === 'alert' || this.dialogKind === 'prompt') {
            Button('取消')
              .layoutWeight(1)
              .height(40)
              .fontSize(15)
              .backgroundColor($r('sys.color.comp_background_tertiary'))
              .fontColor($r('sys.color.font_primary'))
              .onClick((): void => {
                if (this.dialogKind === 'prompt') {
                  this.onPromptResult(false);
                } else {
                  this.onAlertResult(false);
                }
              })
            Button(this.dialogKind === 'prompt' ? '确定' : this.dialogConfirm)
              .layoutWeight(1)
              .height(40)
              .fontSize(15)
              .onClick((): void => {
                if (this.dialogKind === 'prompt') {
                  this.onPromptResult(true);
                } else {
                  this.onAlertResult(true);
                }
              })
          } else {
            Button('取消')
              .layoutWeight(1)
              .height(40)
              .fontSize(15)
              .backgroundColor($r('sys.color.comp_background_tertiary'))
              .fontColor($r('sys.color.font_primary'))
              .onClick((): void => {
                const kind = this.dialogKind;
                this.dialogKind = '';
                if (kind === 'selection') {
                  this.resolvePromise(this.pendingPromiseId, 'null');
                }
              })
          }
        }
        .width('100%')
      }
      .alignItems(HorizontalAlign.Center)
      .width('86%')
      .padding(20)
      .backgroundColor($r('sys.color.comp_background_primary'))
      .borderRadius(20)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor('#66000000')
  }
}
