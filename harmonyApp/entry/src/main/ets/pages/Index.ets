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
  bottomBarSelected
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

@Entry
@Component
struct Index {
  private controller: ArkUIViewController | undefined = undefined;
  private tabsController: HdsTabsController = new HdsTabsController();

  @State private topBar: NativeTopBarState | null = null;
  @State private titleH: number = 62;
  @State private bottomBar: NativeBottomBarState | null = null;

  // 翻页位移每帧都变，单独放，避免整份结构一起替换导致按钮晚一帧
  @State private sOffX: number = 0;
  @State private sOffY: number = 0;
  @State private tOffX: number = 0;
  @State private tOffY: number = 0;

  /** 底部避让区高度（vp），由 EntryAbility 写入；沉浸式下底栏靠它避让导航条。 */
  @StorageProp('bottomInsetVp') private bottomInsetVp: number = 0;

  /** 顶栏圆钮材质。 */
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

    this.controller = MainArkUIViewController();
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
    .translate({ x: this.sOffX, y: this.sOffY })
  }

  @Builder
  todayGroup() {
    Row({ space: this.buttonGap() }) {
      if (this.topBar?.today?.showsMore === true) {
        this.todayMoreButton()
      }
    }
    .margin({ top: this.topDp(), right: this.rightPad() })
    .translate({ x: this.tOffX, y: this.tOffY })
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

  build() {
    Navigation() {
      Stack({ alignContent: Alignment.Bottom }) {
        if (this.controller !== undefined) {
          Compose({
            controller: this.controller,
            libraryName: 'entry',
            onBackPressed: () => false
          })
            .width('100%')
            .height('100%')
        }
        if (this.bottomBar !== null) {
          this.bottomBarTabs()
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
  }
}
