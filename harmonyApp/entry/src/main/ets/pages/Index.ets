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
  topBarAction
} from 'libentry.so';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { fileIo } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { http } from '@kit.NetworkKit';
import { util } from '@kit.ArkTS';
import { display, promptAction, uiMaterial, LengthMetrics } from '@kit.ArkUI';

const USE_CONTENT_GRADIENT_BLUR: boolean = true;

interface BlurBand {
  height: string;
  radius: number;
}

const TOP_BAR_BLUR_BANDS: BlurBand[] = [
  { height: '100%', radius: 3 },
  { height: '76%', radius: 5 },
  { height: '56%', radius: 8 },
  { height: '38%', radius: 12 },
  { height: '22%', radius: 16 }
];

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

interface NativeTopBarDay {
  label: string;
  date: string;
  today: boolean;
}

interface NativeTopBarMenuItem {
  id: string;
  label: string;
}

interface NativeTopBarState {
  title: string;
  materialVisible: boolean;
  accent: number;
  onSurface: number;
  onSurfaceVariant: number;
  showsSwitch: boolean;
  showsMore: boolean;
  leadingPadDp: number;
  trailingPadDp: number;
  heightDp: number;
  titleHeightDp: number;
  statusBarDp: number;
  days: NativeTopBarDay[];
  menu: NativeTopBarMenuItem[];
}

function argbHex(value: number): string {
  return '#' + (value >>> 0).toString(16).padStart(8, '0');
}

@Entry
@Component
struct Index {
  private controller: ArkUIViewController | undefined = undefined;
  @State private topBar: NativeTopBarState | null = null;
  @State private topBarMaterial: number = 0;

  private buttonMaterial: uiMaterial.ImmersiveMaterial =
    new uiMaterial.ImmersiveMaterial({
      style: uiMaterial.ImmersiveStyle.ULTRA_THIN,
      interactive: true,
      lightEffect: { color: Color.White }
    });

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
    } catch (e) {
      hilog.error(0x0000, 'nexio', 'setAppEnv failed: %{public}s', JSON.stringify(e as BusinessError));
    }

    registerWallpaperPicker((): void => {
      this.pickWallpaper();
    });

    registerHttpSender(sendHttp);

    registerToast((message: string, longDuration: boolean): void => {
      try {
        promptAction.showToast({ message: message, duration: longDuration ? 3500 : 2000 });
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'showToast failed: %{public}s', JSON.stringify(e as BusinessError));
      }
    });

    registerTopBar((json: string): void => {
      if (json === 'null') {
        this.topBar = null;
        return;
      }
      let next: NativeTopBarState;
      try {
        next = JSON.parse(json) as NativeTopBarState;
      } catch (e) {
        hilog.warn(0x0000, 'nexio', 'top bar json parse failed');
        return;
      }
      this.topBar = next;
      const target: number = next.materialVisible ? 1 : 0;
      if (target === this.topBarMaterial) {
        return;
      }
      try {
        this.getUIContext().animateTo({ duration: 240, curve: Curve.EaseOut }, (): void => {
          this.topBarMaterial = target;
        });
      } catch (e) {
        this.topBarMaterial = target;
      }
    });

    this.controller = MainArkUIViewController();
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

  private navSlotWidth(): number {
    const count = (this.topBar?.showsSwitch === true ? 1 : 0) +
      (this.topBar?.showsMore === true ? 1 : 0);
    return count === 0 ? 0 : count * 40 + (count - 1) * 8 + 16;
  }

  @Builder
  navTitle() {
    Stack({ alignContent: Alignment.TopStart }) {
      if (!USE_CONTENT_GRADIENT_BLUR) {
        this.navMaterial()
      }

      Column() {
        Row() {
          Blank().width(this.navSlotWidth())
          Text(this.topBar?.title ?? '')
            .layoutWeight(1)
            .textAlign(TextAlign.Center)
            .fontSize(19)
            .fontWeight(FontWeight.Medium)
            .fontColor(argbHex(this.topBar?.onSurface ?? 0xFF000000))
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          this.navButtons()
        }
        .width('100%')
        .height(56)
        .alignItems(VerticalAlign.Center)

        Row() {
          ForEach(this.topBar?.days ?? [], (day: NativeTopBarDay) => {
            Column() {
              Text(day.label)
                .fontSize(12)
                .fontWeight(day.today ? FontWeight.Bold : FontWeight.Normal)
                .fontColor(argbHex(day.today
                  ? (this.topBar?.accent ?? 0)
                  : (this.topBar?.onSurface ?? 0)))
              if (day.date !== '') {
                Text(day.date)
                  .fontSize(10)
                  .fontColor(argbHex(day.today
                    ? (this.topBar?.accent ?? 0)
                    : (this.topBar?.onSurfaceVariant ?? 0)))
              }
            }
            .layoutWeight(1)
            .justifyContent(FlexAlign.Center)
          }, (day: NativeTopBarDay) => day.label + day.date)
        }
        .width('100%')
        .height(40)
        .alignItems(VerticalAlign.Center)
        .padding({
          left: this.topBar?.leadingPadDp ?? 0,
          right: this.topBar?.trailingPadDp ?? 0
        })
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Start)
      .padding({ top: this.topBar?.statusBarDp ?? 0 })
    }
    .width('100%')
    .height('100%')
    .hitTestBehavior(HitTestMode.Transparent)
  }

  @Builder
  navMaterial() {
    Stack({ alignContent: Alignment.TopStart }) {
      ForEach(TOP_BAR_BLUR_BANDS, (band: BlurBand) => {
        Column()
          .width('100%')
          .height(band.height)
          .backdropBlur(band.radius)
          .opacity(this.topBarMaterial)
      }, (band: BlurBand) => band.height)
    }
    .width('100%')
    .height('100%')
    .hitTestBehavior(HitTestMode.Transparent)
  }

  @Builder
  navButtons() {
    Row({ space: 8 }) {
      if (this.topBar?.showsSwitch === true) {
        this.navSwitchButton()
      }
      if (this.topBar?.showsMore === true) {
        this.navMoreButton()
      }
    }
    .width(this.navSlotWidth())
    .height(56)
    .padding({ right: 16 })
    .justifyContent(FlexAlign.End)
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  navSwitchButton() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph($r('sys.symbol.arrow_left_arrow_right'))
        .fontSize(20)
        .fontColor([argbHex(this.topBar?.onSurface ?? 0xFF000000)])
    }
    .width(40)
    .height(40)
    .backgroundColor(Color.Transparent)
    .systemMaterial(this.buttonMaterial)
    .onClick((): void => {
      topBarAction('switchSchedule');
    })
  }

  @Builder
  navMoreButton() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph($r('sys.symbol.more'))
        .fontSize(20)
        .fontColor([argbHex(this.topBar?.onSurface ?? 0xFF000000)])
    }
    .width(40)
    .height(40)
    .backgroundColor(Color.Transparent)
    .systemMaterial(this.buttonMaterial)
    .bindMenu(this.moreMenu)
  }

  @Builder
  moreMenu() {
    Menu() {
      ForEach(this.topBar?.menu ?? [], (item: NativeTopBarMenuItem) => {
        MenuItem({ content: item.label })
          .onClick((): void => {
            topBarAction(item.id);
          })
      }, (item: NativeTopBarMenuItem) => item.id)
    }
    // 菜单宽度
    .width(220)
    // 菜单分割线
    .menuItemDivider({
      strokeWidth: LengthMetrics.vp(0.5),
      color: $r('sys.color.comp_divider'),
    })
  }

  private titleHeight(): number {
    return (this.topBar?.statusBarDp ?? 0) + (this.topBar?.titleHeightDp ?? 118);
  }

  build() {
    Navigation() {
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
      .linearGradientBlur(USE_CONTENT_GRADIENT_BLUR ? 18 : 0, {
        fractionStops: [
          [1.0, 0.0],
          [1.0, 0.14],
          [0.0, 0.18]
        ],
        direction: GradientDirection.Bottom
      })
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
