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
  registerTopBar,
  topBarAction,
  registerBottomBar,
  bottomBarSelected,
  OverlayArkUIViewController,
  registerOverlay,
  overlayPopBack,
  registerDocChrome,
  registerDocChromeMotion,
  docChromeAction,
  registerMainMotion
} from 'libentry.so';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { fileIo } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { http } from '@kit.NetworkKit';
import { util } from '@kit.ArkTS';
import { display, uiMaterial, LengthMetrics } from '@kit.ArkUI';
import { HdsTabs, HdsTabsController, hdsMaterial } from '@kit.UIDesignKit';

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

interface NativeTopBarMenuItem {
  id: string;
  label: string;
}

interface NativeTopBarGroup {
  showsSwitch: boolean;
  showsMore: boolean;
  offsetXDp: number;
  offsetYDp: number;
  menu: NativeTopBarMenuItem[];
}

interface NativeTopBarState {
  onSurface: number;
  topDp: number;
  sizeDp: number;
  spacingDp: number;
  rightPadDp: number;
  today: NativeTopBarGroup;
  schedule: NativeTopBarGroup;
}

interface NativeBottomBarState {
  selected: number;
  tabsCount: number;
  labels: string[];
  onSurface: number;
  /** 主题色，选中态的图标与文字都用它。 */
  accent: number;
  heightDp: number;
}

function argbHex(value: number): string {
  return '#' + (value >>> 0).toString(16).padStart(8, '0');
}

/** 二三四级页顶栏上的一颗文字动作。 */
interface DocChip {
  /** SF Symbol 名（与 iOS 同源）；空串 = 画文字。 */
  symbol: string;
  label: string;
  enabled: boolean;
}

/** 一层子页的原生顶栏语义（Kotlin 的 NativeDocumentChrome，与 iOS 同协议）。 */
interface NativeDocChrome {
  key: string;
  title: string;
  /** 主题色 ARGB（accent），个别部件着色用；返回图标/动作文字不用它 */
  tint: number;
  /** 黑白前景 ARGB（onSurface，iOS 的 .label 对应物）：返回图标与动作文字 */
  label: number;
  /** 无 leading（编辑器取消）时显示返回钮。 */
  back: boolean;
  leading: DocChip | null;
  action: DocChip | null;
  secondary: DocChip | null;
  /** 顶栏位移，1/10000 屏宽（栈顶 1-p、次顶 -0.24p，与 PageLayer 同式）。 */
  fracX: number;
}

@Entry
@Component
struct Index {
  private controller: ArkUIViewController | undefined = undefined;
  /** 二级页那层（第二个 Compose 实例），z 序排在顶栏、底栏之后。 */
  private overlayController: ArkUIViewController | undefined = undefined;
  private tabsController: HdsTabsController = new HdsTabsController();

  @State private topBar: NativeTopBarState | null = null;
  @State private titleH: number = 62;
  @State private bottomBar: NativeBottomBarState | null = null;
  /** 有没有二级页。没有时覆盖层整层藏掉，否则全屏 XComponent 会吃掉主界面手势。 */
  @State @Watch('onOverlayChanged') private overlayActive: boolean = false;

  /** 每层子页的原生顶栏（内容 + 位移合并后的可见集合，最多两层同屏）。 */
  @State private docChromes: NativeDocChrome[] = [];
  /** 内容缓存（低频），位移到达时合并成 docChromes。 */
  private docContents: NativeDocChrome[] = [];
  private docMotion: string[][] = [];

  /** 覆盖层刚藏起来时，把页签选中态拉回 Compose 发布的值：设置格点击会把 HdsTabs 内部 index 带到越界位，参数 diff 不变就不会自己回来。 */
  private onOverlayChanged(): void {
    if (!this.overlayActive) {
      this.tabsController.changeIndex(this.bottomBar?.selected ?? 0);
    }
  }

  // 翻页位移每帧都变，单独放，避免整份结构一起替换导致按钮晚一帧
  @State private sOffX: number = 0;
  @State private sOffY: number = 0;
  @State private tOffX: number = 0;
  @State private tOffY: number = 0;

  /** 主界面视差（vp）：顶栏圆钮与底栏胶囊跟主界面一起滑。 */
  @State private mainShiftX: number = 0;

  /** 底部避让区高度（vp），由 EntryAbility 写入；沉浸式下底栏靠它避让导航条。 */
  @StorageProp('bottomInsetVp') private bottomInsetVp: number = 0;
  /** 顶部避让区（状态栏）高度（vp）；子页顶栏壳从窗口 y=0 开始排，按钮要自己让出来。 */
  @StorageProp('topInsetVp') private topInsetVp: number = 0;

  /**
   * 顶栏圆钮材质。沉浸光感（systemMaterial）是系统硬限制：只在 Navigation.title
   * 这类系统 chrome 结构里生效，所以顶栏必须留在 title，不能下放进 Stack。
   */
  private buttonMaterial: uiMaterial.ImmersiveMaterial =
    new uiMaterial.ImmersiveMaterial({
      style: uiMaterial.ImmersiveStyle.ULTRA_THIN,
      interactive: true,
      lightEffect: { color: Color.White }
    });

  /** 屏宽（vp），启动时算一次，供底栏算胶囊宽度。 */
  private screenWidthVp: number = 360;

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
      this.screenWidthVp = this.getUIContext().px2vp(d.width);
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'setAppEnv failed: %{public}s', JSON.stringify(e as BusinessError));
    }

    registerWallpaperPicker((): void => {
      this.pickWallpaper();
    });

    registerHttpSender(sendHttp);

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

    registerTopBar((json: string): void => {
      if (json === 'null') {
        this.topBar = null;
        return;
      }
      // 高频路径：`@课程表x,课程表y,今日x,今日y`，单位是 1/100 dp
      if (json.charCodeAt(0) === 0x40) {
        const p: string[] = json.substring(1).split(',');
        this.setOffsets(Number.parseFloat(p[0]) / 100, Number.parseFloat(p[1]) / 100,
          Number.parseFloat(p[2]) / 100, Number.parseFloat(p[3]) / 100);
        return;
      }
      try {
        const state = JSON.parse(json) as NativeTopBarState;
        this.topBar = state;
        this.titleH = state.topDp + state.sizeDp + 20;
        this.setOffsets(state.schedule.offsetXDp, state.schedule.offsetYDp,
          state.today.offsetXDp, state.today.offsetYDp);
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'top bar json parse failed');
      }
    });

    registerBottomBar((json: string): void => {
      if (json === 'null') {
        this.bottomBar = null;
        return;
      }
      try {
        this.bottomBar = JSON.parse(json) as NativeBottomBarState;
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'bottom bar json parse failed');
      }
    });

    registerOverlay((active: boolean): void => {
      this.overlayActive = active;
    });

    registerDocChrome((json: string): void => {
      try {
        const arr = JSON.parse(json) as NativeDocChrome[];
        this.docContents = arr ?? [];
        this.applyDocMotion();
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'doc chrome json parse failed');
      }
    });

    registerDocChromeMotion((json: string): void => {
      try {
        const arr = JSON.parse(json) as string[][];
        this.docMotion = arr ?? [];
        this.applyDocMotion();
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'doc chrome motion parse failed');
      }
    });

    // 主界面位移 `@<centi>`（1/10000 屏宽）。try 住旧 .so，避免拖垮整个 aboutToAppear。
    try {
      registerMainMotion((json: string): void => {
        const x: number = Number.parseInt(json.substring(1), 10) / 10000 * this.screenWidthVp;
        if (x !== this.mainShiftX) {
          this.mainShiftX = x;
        }
      });
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'registerMainMotion unavailable');
    }

    this.controller = MainArkUIViewController();
    // ⚠ 必须常驻：转场进度、页面状态都在这个组合里，按需创建等于把正在滑的页面扔掉
    this.overlayController = OverlayArkUIViewController();
  }

  /**
   * 系统返回键：有二级页时出栈并消费（Compose 的预测返回在鸿蒙是空实现，
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

  private setOffsets(sx: number, sy: number, tx: number, ty: number): void {
    if (sx !== this.sOffX) {
      this.sOffX = sx;
    }
    if (sy !== this.sOffY) {
      this.sOffY = sy;
    }
    if (tx !== this.tOffX) {
      this.tOffX = tx;
    }
    if (ty !== this.tOffY) {
      this.tOffY = ty;
    }
  }

  /** 内容 × 位移 → 可见集合。位移里没有的层不渲染（深层停泊时本来也在屏外）。
   *  按 fracX 降序排：栈顶（位移最大）排在最后渲染，压在次顶壳之上。 */
  private applyDocMotion(): void {
    const motion = this.docMotion;
    this.docChromes = this.docContents
      .map((c: NativeDocChrome): NativeDocChrome => {
        let fracX = -1;
        for (const m of motion) {
          if (m.length >= 2 && m[0] === c.key) {
            fracX = Number(m[1]) / 10000;
            break;
          }
        }
        const out: NativeDocChrome = {
          key: c.key,
          title: c.title,
          tint: c.tint,
          label: typeof c.label === 'number' ? c.label : (c.tint ?? 0xFF000000),
          back: c.back,
          leading: c.leading ? {
            symbol: typeof c.leading.symbol === 'string' ? c.leading.symbol : '',
            label: c.leading.label,
            enabled: c.leading.enabled
          } : null,
          action: c.action ? {
            symbol: typeof c.action.symbol === 'string' ? c.action.symbol : '',
            label: c.action.label,
            enabled: c.action.enabled
          } : null,
          secondary: c.secondary ? {
            symbol: typeof c.secondary.symbol === 'string' ? c.secondary.symbol : '',
            label: c.secondary.label,
            enabled: c.secondary.enabled
          } : null,
          fracX: fracX
        };
        return out;
      })
      .filter((c: NativeDocChrome): boolean => c.fracX > -0.5)
      .sort((a: NativeDocChrome, b: NativeDocChrome): number => b.fracX - a.fracX);
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

  // ── 顶栏 ──────────────────────────────────────────────────────────────
  // 只有右上角两三个圆钮交给 ArkUI，标题与其余内容仍由 Compose 画。

  private titleHeight(): number {
    return this.titleH;
  }

  private topDp(): number {
    return this.topBar?.topDp ?? 0;
  }

  private buttonSize(): number {
    return this.topBar?.sizeDp ?? 42;
  }

  private buttonGap(): number {
    return this.topBar?.spacingDp ?? 8;
  }

  private rightPad(): number {
    return this.topBar?.rightPadDp ?? 16;
  }

  private iconColor(): string {
    return argbHex(this.topBar?.onSurface ?? 0xFF000000);
  }

  /**
   * Navigation.title 的内容：只有右上角圆钮，标题与其余内容仍由 Compose 画。
   * 空白区域必须 Transparent，否则会把主界面的滑动全部吃掉。
   */
  @Builder
  navTitle() {
    Stack({ alignContent: Alignment.TopEnd }) {
      this.scheduleGroup()
      this.todayGroup()
    }
    .width('100%')
    .height('100%')
    .hitTestBehavior(HitTestMode.Transparent)
  }

  @Builder
  scheduleGroup() {
    Row({ space: this.buttonGap() }) {
      if (this.topBar?.schedule?.showsSwitch === true) {
        this.switchButton()
      }
      if (this.topBar?.schedule?.showsMore === true) {
        this.scheduleMoreButton()
      }
    }
    .margin({ top: this.topDp(), right: this.rightPad() })
    .translate({ x: this.sOffX + this.mainShiftX, y: this.sOffY })
  }

  @Builder
  todayGroup() {
    Row({ space: this.buttonGap() }) {
      if (this.topBar?.today?.showsMore === true) {
        this.todayMoreButton()
      }
    }
    .margin({ top: this.topDp(), right: this.rightPad() })
    .translate({ x: this.tOffX + this.mainShiftX, y: this.tOffY })
  }

  @Builder
  switchButton() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph($r('sys.symbol.arrow_left_arrow_right'))
        .fontSize(20)
        .fontColor([this.iconColor()])
    }
    .width(this.buttonSize())
    .height(this.buttonSize())
    .backgroundColor(Color.Transparent)
    .systemMaterial(this.buttonMaterial)
    .onClick((): void => {
      topBarAction('switchSchedule');
    })
  }

  @Builder
  scheduleMoreButton() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph($r('sys.symbol.more'))
        .fontSize(20)
        .fontColor([this.iconColor()])
    }
    .width(this.buttonSize())
    .height(this.buttonSize())
    .backgroundColor(Color.Transparent)
    .systemMaterial(this.buttonMaterial)
    .bindMenu(this.scheduleMenu)
  }

  @Builder
  todayMoreButton() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph($r('sys.symbol.more'))
        .fontSize(20)
        .fontColor([this.iconColor()])
    }
    .width(this.buttonSize())
    .height(this.buttonSize())
    .backgroundColor(Color.Transparent)
    .systemMaterial(this.buttonMaterial)
    .bindMenu(this.todayMenu)
  }

  @Builder
  scheduleMenu() {
    this.topBarMenu(this.topBar?.schedule?.menu ?? [])
  }

  @Builder
  todayMenu() {
    this.topBarMenu(this.topBar?.today?.menu ?? [])
  }

  @Builder
  topBarMenu(items: NativeTopBarMenuItem[]) {
    Menu() {
      ForEach(items, (item: NativeTopBarMenuItem) => {
        MenuItem({ content: item.label })
          .onClick((): void => {
            topBarAction(item.id);
          })
      }, (item: NativeTopBarMenuItem) => item.id)
    }
    .width(220)
    .menuItemDivider({
      strokeWidth: LengthMetrics.vp(0.5),
      color: $r('sys.color.comp_divider'),
    })
  }

  // ── 底栏 ──────────────────────────────────────────────────────────────
  // 页签胶囊由系统绘制（HdsTabs + BottomTabBarStyle），Compose 只发语义和颜色。

  private barHeight(): number {
    return this.bottomBar?.heightDp ?? 56;
  }

  /** 设置那一格的索引：永远排在所有页签之后。 */
  private settingsIndex(): number {
    return this.bottomBar?.tabsCount ?? 2;
  }

  /** 胶囊宽度 = 屏宽 × 每格占屏比 × 格数，格数含设置那一格，所以每格宽度恒定。 */
  private pillWidthVp(): number {
    return Math.max(120, this.screenWidthVp * 0.2 * (this.settingsIndex() + 1));
  }

  /** 图标与 Compose 底栏同款：Miuix 的 Album / Months / Settings，已导出为 SVG 资源。 */
  private barIconRes(index: number): Resource {
    if (index === this.settingsIndex()) {
      return $r('app.media.settings');
    }
    if ((this.bottomBar?.tabsCount ?? 2) <= 1) {
      // 排班模式只有课程表一格
      return $r('app.media.months');
    }
    return index === 0 ? $r('app.media.album') : $r('app.media.months');
  }

  /** 未选中色取纯黑或纯白：深色主题给白、浅色主题给黑，压 alpha 会被玻璃材质吃掉。 */
  private barNeutral(): string {
    const color: number = this.bottomBar?.onSurface ?? 0xFF000000;
    const r: number = (color >>> 16) & 0xFF;
    const g: number = (color >>> 8) & 0xFF;
    const b: number = color & 0xFF;
    return 0.299 * r + 0.587 * g + 0.114 * b > 140 ? '#FFFFFFFF' : '#FF000000';
  }

  /** 设置格用固定文案，其余取 Compose 发来的 labels（宿主不硬编码多语言）。 */
  private tabLabel(index: number): ResourceStr {
    if (index === this.settingsIndex()) {
      return $r('app.string.tab_settings');
    }
    const labels: string[] = this.bottomBar?.labels ?? [];
    return index < labels.length ? labels[index] : '';
  }

  /**
   * 一格页签。`iconStyle` 对 SVG 是直接替换 fill，所以选中变主题色不需要自绘。
   * 设置格不给选中色：它点开设置页，不留在底栏上。
   */
  private tabStyle(index: number): BottomTabBarStyle {
    const accent: string = argbHex(this.bottomBar?.accent ?? 0xFF007DFF);
    const neutral: string = this.barNeutral();
    const tint: string = index === this.settingsIndex() ? neutral : accent;
    return new BottomTabBarStyle(this.barIconRes(index), this.tabLabel(index))
      .iconStyle({
        selectedColor: tint,
        unselectedColor: neutral
      })
      .labelStyle({
        selectedColor: tint,
        unselectedColor: neutral,
        font: { size: 11, weight: FontWeight.Medium }
      });
  }

  /**
   * 悬浮胶囊。设置是最后一格页签，三格同在一个胶囊里，整条自然居中。
   *
   * `BAR_SHADOW_PAD` 只加在高度上，给悬浮投影留空间。
   * barOverlap(true) / vertical(false) / barPosition(End) 缺一，悬浮样式与材质都不生效。
   */
  @Builder
  bottomBarTabs() {
    HdsTabs({ index: this.bottomBar?.selected ?? 0, controller: this.tabsController }) {
      if ((this.bottomBar?.tabsCount ?? 2) > 1) {
        TabContent() {
        }
        .tabBar(this.tabStyle(0))
        TabContent() {
        }
        .tabBar(this.tabStyle(1))
      } else {
        TabContent() {
        }
        .tabBar(this.tabStyle(0))
      }
      // 设置：最后一格页签，没有选中态，点击只开设置页
      TabContent() {
      }
      .tabBar(this.tabStyle(this.settingsIndex()))
    }
    .barOverlap(true)
    .vertical(false)
    .barPosition(BarPosition.End)
    .barMode(BarMode.Fixed)
    .scrollable(false)
    .barHeight(this.barHeight())
    .barFloatingStyle({
      barWidth: {
        smallWidth: this.pillWidthVp(),
        mediumWidth: this.pillWidthVp(),
        largeWidth: this.pillWidthVp()
      },
      barSideMargin: 16,
      // 窗口是沉浸式，系统不会自己让出导航条，这里补上避让区高度
      barBottomMargin: this.bottomInsetVp,
      systemMaterialEffect: {
        materialType: hdsMaterial.MaterialType.IMMERSIVE,
        materialLevel: hdsMaterial.MaterialLevel.ADAPTIVE
      }
    })
    .width('100%')
    // 必须限高：HdsTabs 不限高会撑满父 Stack
    .height(this.barHeight() + this.bottomInsetVp)
    // 系统自己切选中态，点击瞬间就通知 Compose，不等动画结束
    .onTabBarClick((index: number): void => {
      if (index === this.settingsIndex()) {
        bottomBarSelected(index);
        // 设置不进选中态，立刻切回原来的页签
        this.tabsController.changeIndex(this.bottomBar?.selected ?? 0);
        return;
      }
      if (index !== (this.bottomBar?.selected ?? 0)) {
        bottomBarSelected(index);
      }
    })
  }

  // ── 子页原生顶栏 ────────────────────────────────────────────────────────
  // 每层子页一个 Navigation 壳：按钮住在 Navigation.title 里才有沉浸光感（系统硬限制）。
  // 壳只占标题栏那么高、背景全透明，不挡下面覆盖层的手势；位移逐帧跟随 Compose 转场。
  //
  // ⚠ @Builder 按值传对象参数不会刷新 UI：docChromes 每帧整份替换、元素是全新对象，
  //   Builder 里握着的永远是创建首帧那份快照（位移、按钮全部冻结）。所以所有 Builder
  //   只收常量下标/角色字符串，数据一律经 this.docChromes 读取，让 ArkUI 的最小更新
  //   逐帧重算属性。Kotlin 侧静止时不发布次顶层（p≥0.999），这里见到的都是活层。

  // ── 顶栏壳几何（微调只改这几个数）─────────────────────────────────────
  /** 按钮所在行的高度（不含状态栏避让）。 */
  private docBarContentH: number = 48;
  /** 返回钮距左边缘。 */
  private docBarLeftPad: number = 16;
  /** 返回钮图标大小（SymbolGlyph 字号）。 */
  private docBackIconSize: number = 32;
  /** 返回钮图标字重（可变字体数值 100-900）。 */
  private docBackIconWeight: number = 300;
  /** 返回钮图标水平微调（vp，负值左移；抵消箭头字形自身偏右）。 */
  private docBackIconOffsetX: number = -1.5;
  /** 动作图标大小（SymbolGlyph 字号）。 */
  private docIconSize: number = 24;
  /** 动作文字距右边缘。 */
  private docBarRightPad: number = 16;
  /** 圆形返回钮直径。 */
  private docButtonSize: number = 40;
  /** 壳底额外余量：给按钮材质/阴影留的绘制空间，不加会被 Navigation 裁掉。 */
  private docShadowPad: number = 12;

  private docTitleHeight(): number {
    return this.topInsetVp + this.docBarContentH + this.docShadowPad;
  }

  /** 槽位 index 的层；越界（没有这一层）返回 null。 */
  private docEntry(index: number): NativeDocChrome | null {
    return index >= 0 && index < this.docChromes.length ? this.docChromes[index] : null;
  }

  /** 槽位 index 某个角色的文字动作；没有该层或该角色返回 null。 */
  private docChip(index: number, role: string): DocChip | null {
    const entry: NativeDocChrome | null = this.docEntry(index);
    if (entry === null) {
      return null;
    }
    if (role === 'leading') {
      return entry.leading;
    }
    if (role === 'secondary') {
      return entry.secondary;
    }
    return entry.action;
  }

  /** 槽位 index 的顶栏位移（0=落位，1=整层在屏外右）。 */
  private docFracX(index: number): number {
    const entry: NativeDocChrome | null = this.docEntry(index);
    return entry !== null ? entry.fracX : 1;
  }

  @Builder
  docChromeSlot(index: number) {
    if (index < this.docChromes.length) {
      Navigation() {
      }
      .mode(NavigationMode.Stack)
      .titleMode(NavigationTitleMode.Mini)
      .hideBackButton(true)
      .title(
        { builder: (): void => { this.docTitleBar(index) }, height: this.docTitleHeight() } as NavigationCustomTitle,
        { barStyle: BarStyle.STACK }
      )
      .backgroundColor(Color.Transparent)
      .width('100%')
      .height(this.docTitleHeight())
      .align(Alignment.Top)
      .translate({ x: this.docFracX(index) * this.screenWidthVp })
      .hitTestBehavior(HitTestMode.Transparent)
    }
  }

  @Builder
  docTitleBar(index: number) {
    Row() {
      Row({ space: 12 }) {
        // 编辑器类页面（course 编辑）的取消占据返回位
        if (this.docChip(index, 'leading') !== null) {
          this.docTextButton(index, 'leading')
        } else if (this.docEntry(index)?.back === true) {
          this.docBackButton(index)
        }
      }
      .alignItems(VerticalAlign.Center)

      Blank()

      Row({ space: 12 }) {
        if (this.docChip(index, 'secondary') !== null) {
          this.docTextButton(index, 'secondary')
        }
        if (this.docChip(index, 'action') !== null) {
          this.docTextButton(index, 'action')
        }
      }
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    // 固定高度并顶对齐：壳多出的 docShadowPad 只给阴影用，按钮位置不随它变
    .height(this.topInsetVp + this.docBarContentH)
    .padding({ left: this.docBarLeftPad, right: this.docBarRightPad, top: this.topInsetVp })
    .hitTestBehavior(HitTestMode.Transparent)
  }

  @Builder
  docBackButton(index: number) {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph($r('sys.symbol.chevron_left'))
        .fontSize(this.docBackIconSize)
        .fontWeight(this.docBackIconWeight)
        .fontColor([argbHex(this.docEntry(index)?.label ?? 0xFF000000)])
        .offset({ x: this.docBackIconOffsetX })
    }
    .width(this.docButtonSize)
    .height(this.docButtonSize)
    .backgroundColor(Color.Transparent)
    .systemMaterial(this.buttonMaterial)
    .onClick((): void => {
      const key: string = this.docEntry(index)?.key ?? '';
      if (key !== '') {
        docChromeAction(key, 'back');
      }
    })
  }

  /** 动作按钮一律图标（与 iOS 的 systemName 一致）；无已知 symbol 的 chip 不渲染。 */
  @Builder
  docTextButton(index: number, role: string) {
    if (this.docChipHasIcon(this.docChip(index, role)?.symbol ?? '')) {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        this.docChipGlyph(this.docChip(index, role)?.symbol ?? '', index)
      }
      .width(this.docButtonSize)
      .height(this.docButtonSize)
      .backgroundColor(Color.Transparent)
      .systemMaterial(this.buttonMaterial)
      .opacity((this.docChip(index, role)?.enabled ?? false) ? 1 : 0.35)
      .onClick((): void => {
        const chip: DocChip | null = this.docChip(index, role);
        if (chip !== null && chip.enabled) {
          docChromeAction(this.docEntry(index)?.key ?? '', role);
        }
      })
    }
  }

  /** SF Symbol → 系统符号（sys.symbol）映射；返回空串表示没有对应物。 */
  private docSymbolName(symbol: string): string {
    switch (symbol) {
      case 'plus': return 'plus';
      case 'xmark': return 'xmark';
      case 'checkmark': return 'checkmark';
      case 'arrow.clockwise': return 'arrow_clockwise';
      case 'play.fill': return 'play_fill';
      case 'folder.badge.plus': return 'folder_badge_plus';
      case 'chevron.backward': return 'chevron_left';
      default: return '';
    }
  }

  private docChipHasIcon(symbol: string): boolean {
    return this.docSymbolName(symbol) !== '';
  }

  /** SymbolGlyph：系统符号字体渲染（对标 iOS UIImage(systemName:)），黑白染色。 */
  @Builder
  docChipGlyph(symbol: string, index: number) {
    if (symbol === 'plus') {
      SymbolGlyph($r('sys.symbol.plus'))
        .fontSize(this.docIconSize)
        .fontColor([argbHex(this.docEntry(index)?.label ?? 0xFF000000)])
    } else if (symbol === 'xmark') {
      SymbolGlyph($r('sys.symbol.xmark'))
        .fontSize(this.docIconSize)
        .fontColor([argbHex(this.docEntry(index)?.label ?? 0xFF000000)])
    } else if (symbol === 'checkmark') {
      SymbolGlyph($r('sys.symbol.checkmark'))
        .fontSize(this.docIconSize)
        .fontColor([argbHex(this.docEntry(index)?.label ?? 0xFF000000)])
    } else if (symbol === 'arrow.clockwise') {
      SymbolGlyph($r('sys.symbol.arrow_clockwise'))
        .fontSize(this.docIconSize)
        .fontColor([argbHex(this.docEntry(index)?.label ?? 0xFF000000)])
    } else if (symbol === 'play.fill') {
      SymbolGlyph($r('sys.symbol.play_fill'))
        .fontSize(this.docIconSize)
        .fontColor([argbHex(this.docEntry(index)?.label ?? 0xFF000000)])
    } else if (symbol === 'folder.badge.plus') {
      SymbolGlyph($r('sys.symbol.folder_badge_plus'))
        .fontSize(this.docIconSize)
        .fontColor([argbHex(this.docEntry(index)?.label ?? 0xFF000000)])
    }
  }

  build() {
    // ⚠ alignContent 必须显式 Top：默认居中会把不满屏的子页顶栏壳（docTitleHeight）
    //   垂直居中到屏幕中部；满屏的兄弟节点（主 Navigation / 覆盖层）不受影响。
    Stack({ alignContent: Alignment.Top }) {
      Navigation() {
        Stack({ alignContent: Alignment.Bottom }) {
        if (this.controller !== undefined) {
          Compose({
            controller: this.controller,
            libraryName: 'entry',
            onBackPressed: (): boolean => this.handleBack()
          })
            .width('100%')
            .height('100%')
        }
          if (this.bottomBar !== null) {
            Column() {
              Blank()
              this.bottomBarTabs()
            }
            .width('100%')
            .height('100%')
            .translate({ x: this.mainShiftX })
            .hitTestBehavior(HitTestMode.Transparent)
          }
        }
        .width('100%')
        .height('100%')
      }
      .mode(NavigationMode.Stack)
      .titleMode(NavigationTitleMode.Mini)
      .hideBackButton(true)
      .hideTitleBar(this.topBar === null)
      .title(
        { builder: (): void => { this.navTitle() }, height: this.titleHeight() } as NavigationCustomTitle,
        { barStyle: BarStyle.STACK }
      )
      .width('100%')
      .height('100%')

      // 二级页：独立 Compose 实例，排在 Navigation 之外、之上 —— title 栏与底栏
      // 胶囊都是 Navigation 的 chrome，只有 Navigation 的兄弟节点才真正盖得住。
      // 无二级页时必须 Hidden：全屏 XComponent 空着也会吃掉主界面手势。
      if (this.overlayController !== undefined) {
        Compose({
          controller: this.overlayController,
          libraryName: 'entry',
          onBackPressed: (): boolean => this.handleBack()
        })
          .width('100%')
          .height('100%')
          .visibility(this.overlayActive ? Visibility.Visible : Visibility.Hidden)
      }

      // 每层子页的原生顶栏：排在覆盖层之后才盖得住它。两个固定槽位（不动态增删节点），
      // 槽位里按 docChromes 长度决定是否存在；先画次顶、后画栈顶，栈顶壳在最上层。
      this.docChromeSlot(1)
      this.docChromeSlot(0)
    }
    .width('100%')
    .height('100%')
  }
}
