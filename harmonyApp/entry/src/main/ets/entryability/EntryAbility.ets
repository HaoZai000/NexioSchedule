/**
 * 入口 Ability。
 *
 * 只做三件「页面做不了」的事：
 * 1. 沉浸式布局（内容铺到状态栏/导航栏下面），把两端的**真实高度**算成 vp 写进 AppStorage；
 * 2. 系统栏图标颜色跟随深浅色 —— 页面自绘顶栏，系统栏只剩图标，颜色必须和顶栏一致；
 * 3. 深浅色变化时同步 AppStorage，页面靠 @StorageProp 自动重组。
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

      const density = display.getDefaultDisplaySync().densityPixels;
      const avoid = win.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM);
      // 状态栏高度：页面的顶栏自己要吃掉这块，否则「第 9 周」会顶到刘海里
      AppStorage.setOrCreate('topInsetVp', pxToVp(avoid.topRect.height, density));
      AppStorage.setOrCreate('bottomInsetVp', pxToVp(avoid.bottomRect.height, density));

      this.syncSystemBar(win);
    } catch (e) {
      hilog.warn(DOMAIN, TAG, 'applyImmersive failed: %{public}s', JSON.stringify(e));
    }
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
