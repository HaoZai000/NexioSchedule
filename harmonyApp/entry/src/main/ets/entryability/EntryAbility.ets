/**
 * 入口 Ability。负责页面做不了的三件事：
 * 1. 沉浸式布局，把状态栏 / 导航条的真实高度写进 AppStorage（`topInsetVp` / `bottomInsetVp`）；
 * 2. 系统栏图标颜色跟随深浅色；
 * 3. 深浅色变化同步 AppStorage。
 */

import { AbilityConstant, Configuration, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { display, window } from '@kit.ArkUI';

const DOMAIN = 0x0000;
const TAG = 'nexio';

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.syncColorMode();
  }

  onConfigurationUpdate(newConfig: Configuration): void {
    this.syncColorMode();
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, TAG, 'Failed to load content: %{public}s', JSON.stringify(err));
        return;
      }
      this.applyImmersive(windowStage);
    });
  }

  private applyImmersive(windowStage: window.WindowStage): void {
    try {
      const win = windowStage.getMainWindowSync();
      win.setWindowLayoutFullScreen(true);

      this.syncInsets(win);
      // 全屏设置后避让区可能还没刷新，旋转、手势条显隐也会变，必须持续收
      win.on('avoidAreaChange', () => {
        this.syncInsets(win);
      });

      this.syncSystemBar(win);
    } catch (e) {
      hilog.warn(DOMAIN, TAG, 'applyImmersive failed: %{public}s', JSON.stringify(e));
    }
  }

  /**
   * 同步避让区高度到 AppStorage（vp）。
   *
   * 底部取 `TYPE_SYSTEM` 与 `TYPE_NAVIGATION_INDICATOR` 的最大值：前者只覆盖三键导航栏，
   * 手势导航时 bottomRect 为 0，只查它会让悬浮底栏贴到屏幕最底。
   */
  private syncInsets(win: window.Window): void {
    const density = display.getDefaultDisplaySync().densityPixels;
    let topPx = 0;
    let bottomPx = 0;
    const types: window.AvoidAreaType[] = [
      window.AvoidAreaType.TYPE_SYSTEM,
      window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR
    ];
    for (const type of types) {
      try {
        const area = win.getWindowAvoidArea(type);
        topPx = Math.max(topPx, area.topRect.height);
        bottomPx = Math.max(bottomPx, area.bottomRect.height);
      } catch (e) {
        hilog.warn(DOMAIN, TAG, 'getWindowAvoidArea(%{public}d) failed: %{public}s', type, JSON.stringify(e));
      }
    }
    // 状态栏高度：页面的顶栏自己要吃掉这块，否则「第 9 周」会顶到刘海里
    AppStorage.setOrCreate('topInsetVp', pxToVp(topPx, density));
    AppStorage.setOrCreate('bottomInsetVp', pxToVp(bottomPx, density));
    hilog.info(DOMAIN, TAG, 'insets: top=%{public}s vp, bottom=%{public}s vp',
      String(pxToVp(topPx, density)), String(pxToVp(bottomPx, density)));
  }

  private syncSystemBar(win: window.Window): void {
    const dark = AppStorage.get<boolean>('darkMode') === true;
    win.setWindowSystemBarProperties({
      statusBarContentColor: dark ? '#FFFFFF' : '#000000',
      navigationBarContentColor: dark ? '#FFFFFF' : '#000000'
    }).catch((e: Error) => {
      hilog.warn(DOMAIN, TAG, 'setWindowSystemBarProperties failed: %{public}s', e.message);
    });
  }

  private syncColorMode(): void {
    const mode = this.context.config.colorMode;
    const dark = mode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK;
    AppStorage.setOrCreate('darkMode', dark);
  }
}

function pxToVp(px: number, density: number): number {
  if (density <= 0) {
    return 0;
  }
  return Math.round(px / density * 10) / 10;
}
